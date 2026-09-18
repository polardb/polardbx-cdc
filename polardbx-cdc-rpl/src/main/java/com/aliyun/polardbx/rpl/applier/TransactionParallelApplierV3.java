/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.ConflictStrategy;
import com.aliyun.polardbx.rpl.taskmeta.ConflictType;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.rpl.applier.DmlApplyHelper.repairDMLName;

@Slf4j
public class TransactionParallelApplierV3 extends TransactionApplier {

    // ======================== Compaction Parameters ========================

    /**
     * 单个 window 最大包含的事务节点数
     */
    private static final int MAX_COMPACT_TRANSACTIONS = 64;

    /**
     * 单个 window 最大 event 总数
     */
    private static final int MAX_COMPACT_EVENTS = 200;

    /**
     * 功能开关在 Applier 初始化时固化，配置变更后需重启进程生效。
     */
    private final boolean compactionEnabled;
    private final boolean batchEnabled;
    private final Cache<String, Boolean> serialFallbackWarningCache = CacheBuilder.newBuilder()
        .maximumSize(4096)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build();

    // ======================== Inner Classes ========================

    /**
     * DAG 节点结构
     */
    private static class TxNode {
        Transaction tx;
        int index; // 原始索引（批次中的顺序）
        int originalIndegree; // DAG 构建完成后记录的原始 indegree（用于 window 构建）
        AtomicInteger indegree = new AtomicInteger(0);
        List<TxNode> downstream = new ArrayList<>();

        // Cached during DAG construction to avoid redundant extraction in compaction
        Set<TableKey> cachedPkKeys;
        Map<TableKey, List<DefaultRowChange>> eventsByPk;
        // 按原始顺序缓存的全部事件对象，供 createMergedTransaction 复用。
        // 主要作用：保证 persist=ON 时 supersededEvents(IdentityHashMap) 引用判等正确
        // （持久化事务的 rangeIterator() 每次调用都会反序列化出新对象）。
        List<DefaultRowChange> cachedEvents;

        TxNode(Transaction tx, int index) {
            this.tx = tx;
            this.index = index;
        }
    }

    /**
     * 资源 Key（schema + table + PK/UK values）
     */
    private static class TableKey {
        final String schema;
        final String table;
        final Map<String, Serializable> keyValues;
        private final int cachedHashCode;

        TableKey(String schema, String table, Map<String, Serializable> keyValues) {
            this.schema = schema;
            this.table = table;
            this.keyValues = Collections.unmodifiableMap(new HashMap<>(keyValues));
            this.cachedHashCode = Objects.hash(schema, table, deepMapHashCode(this.keyValues));
        }

        @Override
        public int hashCode() {
            return cachedHashCode;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof TableKey)) {
                return false;
            }
            TableKey other = (TableKey) obj;
            return Objects.equals(this.schema, other.schema)
                && Objects.equals(this.table, other.table)
                && deepMapEquals(this.keyValues, other.keyValues);
        }

        private static boolean deepMapEquals(Map<String, Serializable> left,
                                             Map<String, Serializable> right) {
            if (left.size() != right.size()) {
                return false;
            }
            for (Map.Entry<String, Serializable> entry : left.entrySet()) {
                if (!right.containsKey(entry.getKey())
                    || !Objects.deepEquals(entry.getValue(), right.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        }

        private static int deepMapHashCode(Map<String, Serializable> values) {
            int hashCode = 0;
            for (Map.Entry<String, Serializable> entry : values.entrySet()) {
                int valueHashCode = Arrays.deepHashCode(new Object[] {entry.getValue()});
                hashCode += Objects.hashCode(entry.getKey()) ^ valueHashCode;
            }
            return hashCode;
        }

        @Override
        public String toString() {
            return schema + "." + table + ":" + keyValues;
        }
    }

    /**
     * Per-key supersede 状态机链类型
     */
    private enum ChainType {
        INSERT,
        UPDATE
    }

    /**
     * Per-key supersede 状态机状态
     */
    private static class KeyChainState {
        ChainType chainType;
        DefaultRowChange originEvent;       // 链起点事件（INSERT 本身或第一个 UPDATE）
        DefaultRowChange pendingUpdate;     // 链中最新的 UPDATE（可被下一个 UPDATE/DELETE supersede）
        boolean hadSupersede;               // 是否发生过 supersede

        KeyChainState(ChainType type, DefaultRowChange origin) {
            this.chainType = type;
            this.originEvent = origin;
        }
    }

    /**
     * Signals that a logical INSERT into an externalized table hit a duplicate-key conflict.
     * The current target transaction must be rolled back before any conflict compensation runs,
     * because CN external-column staging may retain locks after the failed statement.
     */
    private static class ExternalizedInsertConflictException extends Exception {
        private static final long serialVersionUID = 1L;

        private final String schema;
        private final String table;
        private final int batchSize;

        ExternalizedInsertConflictException(DefaultRowChange rowChange, int batchSize, SQLException cause) {
            super(cause);
            this.schema = rowChange.getSchema();
            this.table = rowChange.getTable();
            this.batchSize = batchSize;
        }
    }

    // ======================== Constructor ========================

    public TransactionParallelApplierV3(ApplierConfig applierConfig, HostInfo hostInfo, HostInfo srcHostInfo) {
        super(applierConfig, hostInfo, srcHostInfo);
        // DIRECT_OVERWRITE 对 UPDATE 的语义是 DELETE+REPLACE（见 DmlApplyHelper.getDeleteThenReplaceSqlExecContext），
        // 而本 Applier 的批量/单条 UPDATE flush 路径（flushCaseWhenUpdate/flushSingleRowUpdate）始终生成
        // 绝对值 UPDATE SQL，从不切换为 DELETE+REPLACE：语义与 DIRECT_OVERWRITE 不一致，
        // 且会导致 UPDATE miss 场景被静默跳过（违反数据完整性强制要求）。
        // 因此本 Applier 禁止使用 DIRECT_OVERWRITE，请改用 OVERWRITE。
        if (applierConfig.getConflictStrategy() == ConflictStrategy.DIRECT_OVERWRITE) {
            throw new PolardbxException(
                "TransactionParallelApplierV3 does not support DIRECT_OVERWRITE conflict strategy: "
                    + "its UPDATE flush paths always generate absolute-value UPDATE SQL and never "
                    + "fall back to DELETE+REPLACE, which would silently skip UPDATE-miss rows. "
                    + "Please use OVERWRITE instead.");
        }
        this.compactionEnabled = DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_COMPACTION_ENABLED);
        this.batchEnabled = DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_BATCH_ENABLED);
    }

    // ======================== Entry Point ========================

    @Override
    public void tranApply(List<Transaction> transactions) throws Exception {
        if (transactions.isEmpty()) {
            return;
        }
        for (Transaction tx : transactions) {
            if (tx.getEventCount() > 0) {
                StatMetrics.getInstance().addApplyAttemptCount(1);
                break;
            }
        }
        // 按 DDL 边界切分：DDL 作为串行屏障，DML 段走 DAG 并行
        List<Transaction> dmlBatch = new ArrayList<>();
        for (Transaction tx : transactions) {
            if (tx.getEventCount() > 0 && DdlApplyHelper.isDdl(tx.peekFirst())) {
                // 先 flush 积累的 DML
                if (!dmlBatch.isEmpty()) {
                    applyDmlBatch(dmlBatch);
                    dmlBatch = new ArrayList<>();
                }
                // 串行执行 DDL
                ddlApply(tx.peekFirst());
                logCommitInfo(Collections.singletonList(tx.peekFirst()));
            } else {
                dmlBatch.add(tx);
            }
        }
        // flush 剩余 DML
        if (!dmlBatch.isEmpty()) {
            applyDmlBatch(dmlBatch);
        }
    }

    // ======================== Core Pipeline ========================

    /**
     * 对纯 DML 事务批次执行 DAG 并行 apply（含执行时 window compact）
     */
    private void applyDmlBatch(List<Transaction> dmlTransactions) throws Exception {
        // 无稳定资源键，或 Java 侧无法精确表达数据库 key 等价关系时，整批回退父类串行路径。
        // 这里也同时绕开单事务 batch，确保同一事务内的事件保持原始执行顺序。
        if (containsTableRequiringSerialApply(dmlTransactions)) {
            if (containsExternalizedTable(dmlTransactions)
                && containsExternalizedInsertTransaction(dmlTransactions)) {
                applySerialFallback(dmlTransactions);
            } else {
                super.tranApply(dmlTransactions);
            }
            return;
        }

        // compaction 开启时，单个事务也需要进入 DAG/compaction 路径，
        // 以识别同一事务内同一 PK 的连续事件。
        if (dmlTransactions.size() == 1 && !compactionEnabled) {
            applySingle(dmlTransactions.get(0));
            return;
        }

        boolean compactionLogEnabled =
            DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_COMPACTION_LOG_ENABLED);
        long graphStart = compactionLogEnabled ? System.currentTimeMillis() : 0;
        List<TxNode> graph = buildDependencyGraph(dmlTransactions, compactionLogEnabled);
        if (compactionLogEnabled) {
            log.info("[Graph] Build DAG completed, cost={}ms", System.currentTimeMillis() - graphStart);
        }

        parallelApplyWithCompaction(graph, compactionLogEnabled);
    }

    /**
     * Keep the original serial fallback for ordinary transactions. A transaction containing an externalized
     * INSERT still needs the duplicate-key transaction boundary enforced by this applier, so execute it through
     * the externalized-aware path without statement batching.
     */
    private void applySerialFallback(List<Transaction> transactions) throws Exception {
        for (Transaction transaction : transactions) {
            applySingle(transaction, true);
        }
    }

    private boolean containsExternalizedInsertTransaction(List<Transaction> transactions) throws Exception {
        for (Transaction transaction : transactions) {
            if (containsExternalizedInsert(collectRowChanges(transaction))) {
                return true;
            }
        }
        return false;
    }

    private boolean containsTableRequiringSerialApply(List<Transaction> transactions) throws Exception {
        Set<String> checkedTables = new HashSet<>();
        for (Transaction transaction : transactions) {
            for (String fullTableName : transaction.getTables()) {
                if (!checkedTables.add(fullTableName)) {
                    continue;
                }
                TableInfo tableInfo = dbMetaCache.getTableInfo(fullTableName);
                if (CollectionUtils.isEmpty(tableInfo.getPks())
                    && CollectionUtils.isEmpty(tableInfo.getUks())
                    && CollectionUtils.isEmpty(tableInfo.getUkGroups())) {
                    warnSerialFallbackRateLimited(fullTableName, "no PK/UK");
                    return true;
                }
                if (tableInfo.isParallelApplyKeyUnsupported()) {
                    warnSerialFallbackRateLimited(fullTableName,
                        "unsupported key: " + tableInfo.getParallelApplyKeyIncompatibleReason());
                    return true;
                }
            }
        }
        return false;
    }

    private void warnSerialFallbackRateLimited(String fullTableName, String reason) {
        String warningKey = fullTableName + '\0' + reason;
        if (serialFallbackWarningCache.asMap().putIfAbsent(warningKey, Boolean.TRUE) == null) {
            log.warn("[Graph] Fallback to serial apply because table {} has {}", fullTableName, reason);
        }
    }

    /**
     * 构建依赖图（DAG）。
     * <p>
     * 边的构建基于 PK + UK 全部 key（保证完整的依赖关系）。
     */
    private List<TxNode> buildDependencyGraph(List<Transaction> transactions,
                                              boolean compactionLogEnabled) throws Exception {
        Map<TableKey, TxNode> lastAccess = new HashMap<>();
        Map<TableKey, AtomicInteger> keyFrequency = compactionLogEnabled ? new HashMap<>() : null;
        Map<TableInfo, Set<String>> characterDagKeyColumnsCache = new IdentityHashMap<>();

        List<TxNode> nodes = new ArrayList<>(transactions.size());
        List<Set<TableKey>> transactionAllKeys = new ArrayList<>(transactions.size());

        // Step1: 建节点并提取 PK+UK 依赖 key，同时缓存 compaction 所需的 PK 事件索引
        for (int i = 0; i < transactions.size(); i++) {
            TxNode node = new TxNode(transactions.get(i), i);
            nodes.add(node);

            Set<TableKey> allKeys = new HashSet<>();
            extractAffectedKeys(transactions.get(i), allKeys, characterDagKeyColumnsCache);
            transactionAllKeys.add(allKeys);

            extractPkKeysAndBuildIndex(node, transactions.get(i));

            if (compactionLogEnabled) {
                for (TableKey key : allKeys) {
                    keyFrequency.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
                }
            }
        }

        // Step2: 构边（去重，避免同一对节点间产生重复边）
        for (int i = 0; i < transactions.size(); i++) {
            TxNode current = nodes.get(i);
            Set<TableKey> allKeys = transactionAllKeys.get(i);
            Set<TxNode> alreadyLinked = new HashSet<>();

            for (TableKey key : allKeys) {
                TxNode prev = lastAccess.get(key);
                if (prev != null && prev != current) {
                    if (alreadyLinked.add(prev)) {
                        // 加边 prev -> current（每对节点仅加一条边）
                        prev.downstream.add(current);
                        current.indegree.incrementAndGet();
                    }
                }
                lastAccess.put(key, current);
            }
        }

        // 记录原始 indegree（用于 window 构建，避免并发递减干扰）
        for (TxNode node : nodes) {
            node.originalIndegree = node.indegree.get();
        }

        if (compactionLogEnabled) {
            int zeroIndegreeCount = (int) nodes.stream().filter(n -> n.indegree.get() == 0).count();
            int longestPath = calcLongestPath(nodes);
            log.info("[Graph] TotalTx={}, InitialReady={}, LongestChainLen={}", nodes.size(),
                zeroIndegreeCount, longestPath);

            List<Map.Entry<TableKey, AtomicInteger>> hotKeys = keyFrequency.entrySet().stream()
                .sorted((a, b) -> b.getValue().get() - a.getValue().get())
                .limit(10)
                .collect(Collectors.toList());
            log.info("[Graph] Top Hot Keys: {}", hotKeys);
        }

        return nodes;
    }

    /**
     * 并行执行（内含执行时 window compact 逻辑）。
     * <p>
     * Dispatcher 批量分发模式：主线程批量获取就绪节点并分发到线程池执行。
     * 执行时通过 applyWithCompaction 尝试从当前 root 构建 compaction window。
     * 被合并的节点标记为 consumed，后续调度时跳过 DB 执行但仍触发下游 indegree 递减。
     */
    private void parallelApplyWithCompaction(List<TxNode> graph,
                                             boolean compactionLogEnabled) throws Exception {
        long startTime = System.currentTimeMillis();
        LinkedBlockingQueue<TxNode> readyQueue = new LinkedBlockingQueue<>();
        AtomicInteger remaining = new AtomicInteger(graph.size());
        AtomicBoolean hasError = new AtomicBoolean(false);
        Set<TxNode> consumedNodes = ConcurrentHashMap.newKeySet();

        // 统计指标
        LongAdder totalTxExecTimeNanos = new LongAdder();
        LongAdder totalDmlCount = new LongAdder();
        LongAdder compactWindowCount = new LongAdder();
        LongAdder compactNodeCount = new LongAdder();

        int workerCount = dbMetaCache.getMaxPoolSize();

        // 初始化 ready 队列
        for (TxNode node : graph) {
            if (node.indegree.get() == 0) {
                readyQueue.offer(node);
            }
        }

        // Dispatcher：主线程批量 drain 就绪节点，分发到线程池
        List<Future<Void>> futures = new ArrayList<>();

        while (remaining.get() > 0 && !hasError.get()) {
            List<TxNode> batch = new ArrayList<>();
            readyQueue.drainTo(batch, workerCount);

            if (batch.isEmpty()) {
                LockSupport.parkNanos(100_000); // 100μs
                continue;
            }

            for (TxNode node : batch) {
                Future<Void> future = executorService.submit(() -> {
                    long txStart = System.nanoTime();
                    try {
                        if (consumedNodes.contains(node)) {
                            // 已被上游 window 合并，跳过 DB 执行
                        } else {
                            applyWithCompaction(node, consumedNodes, compactWindowCount, compactNodeCount,
                                compactionLogEnabled);
                        }
                    } catch (Exception e) {
                        log.error("Apply transaction failed, remaining={}, readyQueue.size()={}",
                            remaining.get(), readyQueue.size(), e);
                        hasError.set(true);
                        throw new RuntimeException(e);
                    } finally {
                        remaining.decrementAndGet();
                    }

                    long txCostNanos = System.nanoTime() - txStart;
                    totalTxExecTimeNanos.add(txCostNanos);
                    totalDmlCount.add(node.tx.getEventCount());

                    // 触发下游（consumed 节点也要触发，保证 indegree 正确递减）
                    for (TxNode downstream : node.downstream) {
                        if (downstream.indegree.decrementAndGet() == 0) {
                            readyQueue.offer(downstream);
                        }
                    }
                    return null;
                });
                futures.add(future);
            }
        }

        // 等待所有已提交的任务完成
        PolardbxException exception = CommonUtil.waitAllTaskFinishedAndReturn(futures);
        if (exception != null) {
            throw exception;
        }

        long totalElapsedMs = System.currentTimeMillis() - startTime;
        long avgTxExecTimeMicros = graph.isEmpty() ? 0 :
            totalTxExecTimeNanos.sum() / graph.size() / 1_000;

        double idealParallelTimeMs = (double) totalTxExecTimeNanos.sum() / 1_000_000 / workerCount;
        double parallelEfficiency = totalElapsedMs > 0 ? Math.min(1.0, idealParallelTimeMs / totalElapsedMs) : 0.0;

        if (compactionLogEnabled) {
            log.info(
                "[Exec] All TX done, totalCost={}ms, txCount={}, totalDml={}, avgTxExecTime={}μs, "
                    + "parallelEfficiency={}, compactWindows={}, compactedNodes={}",
                totalElapsedMs,
                graph.size(),
                totalDmlCount.sum(),
                avgTxExecTimeMicros,
                String.format("%.2f", parallelEfficiency),
                compactWindowCount.sum(),
                compactNodeCount.sum()
            );
        }
    }

    // ======================== Compaction Logic ========================

    /**
     * 执行时 window compact：从当前 root 节点出发构建 compaction window，
     * 识别热点 PK 链并执行合并。
     */
    private void applyWithCompaction(TxNode root, Set<TxNode> consumedNodes,
                                     LongAdder compactWindowCount, LongAdder compactNodeCount,
                                     boolean compactionLogEnabled) throws Exception {
        if (!compactionEnabled) {
            applySingle(root.tx);
            return;
        }

        // Phase 1: 从此 root 构建独立 compaction window
        Set<TxNode> window = buildCompactionWindow(root);

        // Phase 2: 在 window 内识别热点 PK 链（含跨事务和事务内热点）
        Map<TableKey, List<TxNode>> hotPkChains;
        try {
            hotPkChains = identifyHotPkChains(window);
        } catch (Exception e) {
            log.warn("[Compact] Failed to identify hot PK chains, fallback to applySingle", e);
            applySingle(root.tx);
            return;
        }

        if (hotPkChains.isEmpty()) {
            applySingle(root.tx);
            return;
        }

        // Phase 3: 对热点 PK 链应用 supersede 状态机
        Set<DefaultRowChange> supersededEvents = Collections.newSetFromMap(new IdentityHashMap<>());
        try {
            applySupersedeMachine(hotPkChains, window, supersededEvents);
        } catch (Exception e) {
            log.warn("[Compact] Failed to apply supersede machine, fallback to applySingle", e);
            applySingle(root.tx);
            return;
        }

        if (supersededEvents.isEmpty()) {
            applySingle(root.tx);
            return;
        }

        // Phase 4: 构建合并事务并执行
        Transaction merged = createMergedTransaction(window, supersededEvents);
        applySingle(merged);

        // Phase 5: 标记 window 内非 root 节点为 consumed
        for (TxNode node : window) {
            if (node != root) {
                consumedNodes.add(node);
            }
        }

        compactWindowCount.increment();
        compactNodeCount.add(window.size() - 1);
        if (compactionLogEnabled) {
            log.info("[Compact] window size={}, hotPks={}, superseded={}",
                window.size(), hotPkChains.size(), supersededEvents.size());
        }
    }

    /**
     * 从单个 indegree=0 节点出发，BFS 扩展构建 compaction window。
     * <p>
     * 停止条件：节点数 >= MAX_COMPACT_TRANSACTIONS 或 event 总数 >= MAX_COMPACT_EVENTS。
     * 仅当节点的所有 upstream 都在 window 内时才能加入（upstream 封闭条件）。
     * 使用 originalIndegree 而非实时 indegree，避免并发递减干扰。
     */
    private Set<TxNode> buildCompactionWindow(TxNode root) {
        Set<TxNode> window = new LinkedHashSet<>();
        // BFS 队列按 originalOrder 排序
        PriorityQueue<TxNode> queue = new PriorityQueue<>(Comparator.comparingInt(n -> n.index));

        window.add(root);
        queue.add(root);
        int totalEvents = (int) root.tx.getEventCount();

        // 记录 window 内节点对下游节点的"已满足 upstream 计数"
        Map<TxNode, Integer> satisfiedUpstreamCount = new HashMap<>();

        while (!queue.isEmpty()) {
            TxNode current = queue.poll();
            for (TxNode downstream : current.downstream) {
                if (window.contains(downstream)) {
                    continue;
                }
                int satisfied = satisfiedUpstreamCount.getOrDefault(downstream, 0) + 1;
                satisfiedUpstreamCount.put(downstream, satisfied);

                // 只有当所有 upstream 都在 window 中时才能加入
                if (satisfied == downstream.originalIndegree) {
                    int nodeEvents = (int) downstream.tx.getEventCount();
                    if (window.size() >= MAX_COMPACT_TRANSACTIONS) {
                        return window;
                    }
                    if (totalEvents + nodeEvents > MAX_COMPACT_EVENTS) {
                        return window;
                    }
                    window.add(downstream);
                    queue.add(downstream);
                    totalEvents += nodeEvents;
                }
            }
        }

        return window;
    }

    /**
     * 在 window 内识别热点 PK 链（被多个节点访问的 PK）。
     * 使用 DAG 构建阶段缓存的 cachedPkKeys，避免重复提取。
     */
    private Map<TableKey, List<TxNode>> identifyHotPkChains(Set<TxNode> window) {
        Map<TableKey, List<TxNode>> pkToNodes = new HashMap<>();

        for (TxNode node : window) {
            for (TableKey pk : node.cachedPkKeys) {
                pkToNodes.computeIfAbsent(pk, k -> new ArrayList<>()).add(node);
            }
        }

        // 过滤出热点 PK：跨事务（同一 PK 出现在多个 TxNode）或事务内（同一 TxNode 内同一 PK 有多个事件）
        Map<TableKey, List<TxNode>> hotChains = new HashMap<>();
        for (Map.Entry<TableKey, List<TxNode>> entry : pkToNodes.entrySet()) {
            List<TxNode> nodes = entry.getValue();
            boolean isHot = nodes.size() > 1;
            if (!isHot && nodes.size() == 1) {
                // intra-TX 热点：同一 TxNode 内同一 PK 有多个事件（如 INSERT A → DELETE A → INSERT A）
                List<DefaultRowChange> events = nodes.get(0).eventsByPk.get(entry.getKey());
                isHot = events != null && events.size() > 1;
            }
            if (isHot) {
                nodes.sort(Comparator.comparingInt(n -> n.index));
                hotChains.put(entry.getKey(), nodes);
            }
        }

        return hotChains;
    }

    /**
     * 对热点 PK 链应用 supersede 状态机，收集被消除的 events。
     */
    private void applySupersedeMachine(Map<TableKey, List<TxNode>> hotPkChains,
                                       Set<TxNode> window,
                                       Set<DefaultRowChange> supersededEvents) throws Exception {
        for (Map.Entry<TableKey, List<TxNode>> entry : hotPkChains.entrySet()) {
            TableKey pk = entry.getKey();
            List<TxNode> chain = entry.getValue();

            KeyChainState state = null;

            for (TxNode node : chain) {
                List<DefaultRowChange> matchingEvents = findEventsForPk(node, pk);
                for (DefaultRowChange event : matchingEvents) {
                    DBMSAction action = event.getAction();

                    if (action == DBMSAction.INSERT) {
                        // INSERT 建新链
                        state = new KeyChainState(ChainType.INSERT, event);

                    } else if (action == DBMSAction.UPDATE) {
                        // identity/UK changes preserve routing and unique-key transition semantics. A changed
                        // externalized column has the same compaction-barrier role for a different reason: its
                        // retained event is the only event guaranteed to carry the restored logical payload.
                        TableInfo dstTbInfo = dbMetaCache.getTableInfo(event.getSchema(), event.getTable());
                        if (isIdentityOrUkChanged(event, dstTbInfo)
                            || isExternalizedColumnChanged(event)) {
                            // A key or externalized-column change breaks the chain. Finalize any preceding
                            // segment that has already superseded events before clearing its state.
                            finalizeKeyChainSegment(state);
                            state = null;
                        } else if (state == null) {
                            // 无链，新建 UPDATE 链
                            state = new KeyChainState(ChainType.UPDATE, event);
                        } else if (state.chainType == ChainType.INSERT) {
                            // INSERT 链 + UPDATE
                            if (state.pendingUpdate != null) {
                                supersededEvents.add(state.pendingUpdate);
                                state.hadSupersede = true;
                            }
                            state.pendingUpdate = event;
                        } else {
                            // UPDATE 链 + UPDATE
                            if (state.pendingUpdate != null) {
                                supersededEvents.add(state.pendingUpdate);
                            } else {
                                supersededEvents.add(state.originEvent);
                            }
                            state.hadSupersede = true;
                            state.pendingUpdate = event;
                        }

                    } else if (action == DBMSAction.DELETE) {
                        if (state == null) {
                            // DELETE 不建链
                        } else if (state.chainType == ChainType.INSERT) {
                            // INSERT 链 + DELETE: supersede origin + pending, 保留 DELETE
                            supersededEvents.add(state.originEvent);
                            if (state.pendingUpdate != null) {
                                supersededEvents.add(state.pendingUpdate);
                            }
                            state = null;
                        } else {
                            // UPDATE 链 + DELETE: supersede pending (or origin)
                            if (state.pendingUpdate != null) {
                                supersededEvents.add(state.pendingUpdate);
                            } else {
                                supersededEvents.add(state.originEvent);
                            }
                            state = null;
                        }
                    }
                }
            }

            finalizeKeyChainSegment(state);
        }
    }

    /**
     * 收尾当前 per-key compaction segment。
     * 如果 segment 内确实发生过 supersede，幸存 UPDATE 必须写入全部非 identity 列，
     * 以恢复被删除事件与幸存事件的联合终态。
     */
    private void finalizeKeyChainSegment(KeyChainState state) {
        if (state == null || !state.hadSupersede) {
            return;
        }

        DefaultRowChange survivor = state.pendingUpdate != null ? state.pendingUpdate : state.originEvent;
        if (survivor.getAction() == DBMSAction.UPDATE) {
            survivor.setForceAllColumns(true);
        }
    }

    /**
     * 将 window 内所有 non-superseded events 合并为单个事务。
     * 事件顺序按节点 originalOrder 排列。
     */
    private Transaction createMergedTransaction(Set<TxNode> window,
                                                Set<DefaultRowChange> supersededEvents) {
        Transaction compacted = new Transaction(null, null);

        for (TxNode node : window) {
            // 复用 DAG 构建阶段缓存的事件对象，确保与 supersededEvents 内的引用一致。
            // persist=ON 防御：持久化事务的 rangeIterator() 每次调用会反序列化出新对象，
            // 若再次调用则 supersededEvents(IdentityHashMap) 的引用判等会失效。
            for (DefaultRowChange event : node.cachedEvents) {
                if (!supersededEvents.contains(event)) {
                    compacted.appendRowChange(event);
                }
            }
        }

        return compacted;
    }

    // ======================== Batch Execution ========================

    /**
     * 单事务执行逻辑（含批量 INSERT/DELETE 优化）。
     * <p>
     * 对于多 event 事务，将同表同操作的 INSERT/DELETE/UPDATE 合并为批量 SQL 执行，
     * 减少网络 round-trip 和 SQL 解析开销。
     */
    private void applySingle(Transaction tx) throws Exception {
        applySingle(tx, false);
    }

    private void applySingle(Transaction tx, boolean forceSerial) throws Exception {
        if (!containsExternalizedTable(tx)) {
            if (forceSerial || tx.getEventCount() <= 1 || !batchEnabled) {
                super.tranApply(Collections.singletonList(tx));
            } else {
                applyBatchedSingle(tx, collectRowChanges(tx));
            }
            return;
        }

        List<DefaultRowChange> allRowChanges = collectRowChanges(tx);
        if (allRowChanges.isEmpty()) {
            return;
        }

        if (containsExternalizedInsert(allRowChanges)) {
            applyWithExternalizedInsertFallback(tx, allRowChanges,
                !forceSerial && tx.getEventCount() > 1 && batchEnabled);
            return;
        }

        if (forceSerial || tx.getEventCount() <= 1 || !batchEnabled) {
            super.tranApply(Collections.singletonList(tx));
            return;
        }
        applyBatchedSingle(tx, allRowChanges);
    }

    private boolean containsExternalizedTable(Transaction tx) {
        return tx.containsExternalizedTable();
    }

    private boolean containsExternalizedTable(List<Transaction> transactions) {
        for (Transaction transaction : transactions) {
            if (transaction.containsExternalizedTable()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 批量执行事务：对同表同操作的 INSERT/DELETE/UPDATE 合并为批量 SQL，
     * 管理连接、事务提交、指标和日志。
     */
    @SuppressWarnings("unchecked")
    private void applyBatchedSingle(Transaction tx, List<DefaultRowChange> allRowChanges) throws Exception {
        DataSource dataSource = dbMetaCache.getBuiltInDefaultDataSource();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                executeBatchedDml(conn, allRowChanges);
                // metrics
                updateMetrics(Collections.singletonList(tx));
                conn.commit();
                // logging
                logCommitInfo((List<DBMSEvent>) (List<?>) allRowChanges);
            } catch (Exception e) {
                rollbackBeforeRethrow(conn, e);
                throw e;
            }
        }
    }

    private List<DefaultRowChange> collectRowChanges(Transaction tx) {
        List<DefaultRowChange> allRowChanges = new ArrayList<>();
        Transaction.RangeIterator iterator = tx.rangeIterator();
        while (iterator.hasNext()) {
            Transaction.Range range = iterator.next();
            for (DBMSEvent event : range.getEvents()) {
                allRowChanges.add((DefaultRowChange) event);
            }
        }
        return allRowChanges;
    }

    private boolean containsExternalizedInsert(List<DefaultRowChange> rowChanges) throws Exception {
        for (DefaultRowChange rowChange : rowChanges) {
            if (isExternalizedInsert(rowChange)) {
                return true;
            }
        }
        return false;
    }

    private boolean isExternalizedInsert(DefaultRowChange rowChange) throws Exception {
        return rowChange.getAction() == DBMSAction.INSERT && rowChange.hasExternalizedColumns();
    }

    /**
     * Run the normal V3 path first. Once an externalized INSERT reports duplicate key, the normal transaction is
     * considered poisoned: it is fully rolled back and closed before the current apply transaction is replayed
     * row by row in a fresh target transaction.
     */
    private void applyWithExternalizedInsertFallback(Transaction tx, List<DefaultRowChange> rowChanges,
                                                     boolean useBatch) throws Exception {
        try {
            applyExternalizedInsertAwareAttempt(tx, rowChanges, useBatch);
        } catch (ExternalizedInsertConflictException conflict) {
            log.warn("[ExternalizedInsertFallback] duplicate key for {}.{}, batchSize={}; "
                    + "normal target transaction was rolled back, replaying current apply transaction row by row",
                conflict.schema, conflict.table, conflict.batchSize);
            replayExternalizedInsertConflict(tx, rowChanges, conflict);
        }
    }

    @SuppressWarnings("unchecked")
    private void applyExternalizedInsertAwareAttempt(Transaction tx, List<DefaultRowChange> rowChanges,
                                                     boolean useBatch) throws Exception {
        DataSource dataSource = dbMetaCache.getBuiltInDefaultDataSource();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                if (useBatch) {
                    executeBatchedDml(conn, rowChanges);
                } else {
                    executeDmlDetectingExternalizedInsertConflict(conn, rowChanges);
                }
                updateMetrics(Collections.singletonList(tx));
                conn.commit();
                logCommitInfo((List<DBMSEvent>) (List<?>) rowChanges);
            } catch (Exception e) {
                rollbackBeforeRethrow(conn, e);
                throw e;
            }
        }
    }

    private void executeDmlDetectingExternalizedInsertConflict(Connection conn,
                                                               List<DefaultRowChange> rowChanges) throws Exception {
        for (DefaultRowChange rowChange : rowChanges) {
            if (isExternalizedInsert(rowChange)) {
                executeExternalizedInsertNormally(conn, rowChange);
            } else {
                DmlApplyHelper.executeDML(conn, Collections.singletonList(rowChange), conflictStrategy);
            }
        }
    }

    private void executeExternalizedInsertNormally(Connection conn, DefaultRowChange rowChange) throws Exception {
        ensureSingleRowChange(rowChange);
        TableInfo tableInfo = dbMetaCache.getTableInfo(rowChange.getSchema(), rowChange.getTable());
        SqlContext sqlContext = DmlApplyHelper.getInsertSqlExecContext(rowChange, tableInfo,
            RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);
        try {
            DmlApplyHelper.execSqlContext(conn, sqlContext);
        } catch (SQLException e) {
            if (DmlApplyHelper.isDuplicateKeyException(e)) {
                throw new ExternalizedInsertConflictException(rowChange, 1, e);
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void replayExternalizedInsertConflict(Transaction tx, List<DefaultRowChange> rowChanges,
                                                  ExternalizedInsertConflictException originalConflict)
        throws Exception {
        DataSource dataSource = dbMetaCache.getBuiltInDefaultDataSource();
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                for (DefaultRowChange rowChange : rowChanges) {
                    if (isExternalizedInsert(rowChange)) {
                        executeExternalizedInsertFinalDml(conn, rowChange);
                    } else {
                        DmlApplyHelper.executeDML(conn, Collections.singletonList(rowChange), conflictStrategy);
                    }
                }
                updateMetrics(Collections.singletonList(tx));
                conn.commit();
                logCommitInfo((List<DBMSEvent>) (List<?>) rowChanges);
                log.info("[ExternalizedInsertFallback] replay committed for {}.{}, events={}",
                    originalConflict.schema, originalConflict.table, rowChanges.size());
            } catch (Exception e) {
                rollbackBeforeRethrow(conn, e);
                log.error("[ExternalizedInsertFallback] replay failed for {}.{}, events={}; "
                        + "target transaction was rolled back",
                    originalConflict.schema, originalConflict.table, rowChanges.size(), e);
                throw e;
            }
        }
    }

    private void executeExternalizedInsertFinalDml(Connection conn, DefaultRowChange rowChange) throws Exception {
        ensureSingleRowChange(rowChange);
        TableInfo tableInfo = dbMetaCache.getTableInfo(rowChange.getSchema(), rowChange.getTable());
        int insertMode;
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV) && tableInfo.isHasGeneratedUk()) {
            // Preserve DmlApplyHelper's lab-only generated-UK behavior: CN cannot safely execute REPLACE here.
            insertMode = RplConstants.INSERT_MODE_INSERT_IGNORE;
        } else {
            switch (conflictStrategy) {
            case OVERWRITE:
                insertMode = RplConstants.INSERT_MODE_REPLACE;
                break;
            case IGNORE:
                insertMode = RplConstants.INSERT_MODE_INSERT_IGNORE;
                break;
            case INTERRUPT:
                insertMode = RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE;
                break;
            case DIRECT_OVERWRITE:
            default:
                throw new PolardbxException(
                    "unsupported conflict strategy for externalized INSERT replay: " + conflictStrategy);
            }
        }
        SqlContext sqlContext = DmlApplyHelper.getInsertSqlExecContext(rowChange, tableInfo, insertMode);
        DmlApplyHelper.execSqlContext(conn, sqlContext);
    }

    private void ensureSingleRowChange(DefaultRowChange rowChange) {
        if (rowChange.getRowSize() != 1) {
            throw new PolardbxException("row change should not has more than 1 row here");
        }
    }

    private void rollbackBeforeRethrow(Connection conn, Exception original) throws Exception {
        try {
            conn.rollback();
        } catch (SQLException rollbackError) {
            original.addSuppressed(rollbackError);
            throw new PolardbxException("target transaction rollback failed", original);
        }
    }

    /**
     * 对 row changes 进行 multi-table accumulator batching 执行。
     * <p>
     * 策略：不同表之间的操作可自由重排（无外键依赖），同表内保序。
     * 维护每张表的待执行 batch，当同表出现不同操作类型时先 flush 该表已有 batch。
     * INSERT/DELETE/UPDATE 均支持批量合并：
     * - INSERT → multi-row INSERT/REPLACE
     * - DELETE → DELETE WHERE pk IN (...)
     * - UPDATE → CASE WHEN 批量 UPDATE
     */
    private void executeBatchedDml(Connection conn, List<DefaultRowChange> rowChanges) throws Exception {
        // 构造函数已禁止 DIRECT_OVERWRITE（见构造函数校验），conflictStrategy 恒不为 DIRECT_OVERWRITE，
        // 因此 insertMode 恒为 SIMPLE_INSERT_OR_DELETE，不再需要按 conflictStrategy 切换为 REPLACE。
        int insertMode = RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE;

        // key = "schema.table", value = 当前累积的同操作 batch
        Map<String, List<DefaultRowChange>> pendingBatches = new HashMap<>();
        // 记录每个表当前 pending batch 的操作类型
        Map<String, DBMSAction> pendingActions = new HashMap<>();

        for (DefaultRowChange current : rowChanges) {
            DBMSAction action = current.getAction();
            String tableFullName = current.getSchema() + "." + current.getTable();

            // 同表不同操作：先 flush 已有 batch（保证同表内操作顺序）
            DBMSAction pendingAction = pendingActions.get(tableFullName);
            if (pendingAction != null && pendingAction != action) {
                flushPendingBatch(conn, pendingBatches.remove(tableFullName),
                    pendingActions.remove(tableFullName), insertMode);
            }

            // 累积到该表的 batch
            pendingBatches.computeIfAbsent(tableFullName, k -> new ArrayList<>()).add(current);
            pendingActions.put(tableFullName, action);

            // 达到批次上限时立即 flush
            List<DefaultRowChange> batch = pendingBatches.get(tableFullName);
            if (batch.size() >= applierConfig.getDmlBatchSize()) {
                flushPendingBatch(conn, pendingBatches.remove(tableFullName),
                    pendingActions.remove(tableFullName), insertMode);
            }
        }

        // flush 所有剩余 pending batches
        for (Map.Entry<String, List<DefaultRowChange>> entry : pendingBatches.entrySet()) {
            String tableFullName = entry.getKey();
            flushPendingBatch(conn, entry.getValue(), pendingActions.get(tableFullName), insertMode);
        }
    }

    /**
     * Flush 一个 pending batch：单条走普通路径，多条走批量 SQL。
     */
    private void flushPendingBatch(Connection conn, List<DefaultRowChange> batch,
                                   DBMSAction action, int insertMode) throws Exception {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        if (batch.size() == 1) {
            DefaultRowChange rowChange = batch.get(0);
            if (isExternalizedInsert(rowChange)) {
                executeExternalizedInsertNormally(conn, rowChange);
            } else {
                DmlApplyHelper.executeDML(conn, batch, conflictStrategy);
            }
        } else {
            try {
                flushBatch(conn, batch, action, insertMode);
            } catch (SQLException e) {
                if (DmlApplyHelper.isDuplicateKeyException(e)) {
                    if (action == DBMSAction.INSERT && isExternalizedInsert(batch.get(0))) {
                        throw new ExternalizedInsertConflictException(batch.get(0), batch.size(), e);
                    }
                    // Ordinary-table duplicate handling keeps the existing same-transaction fallback.
                    log.warn("[Batch] Batch {} dup key for {}.{}, size={}, fallback to one-by-one: {}",
                        action, batch.get(0).getSchema(), batch.get(0).getTable(),
                        batch.size(), e.getMessage());
                    DmlApplyHelper.executeDML(conn, batch, conflictStrategy);
                } else {
                    // 其他异常（死锁/超时/网络等）：事务可能已被 CN 回滚，不能 fallback
                    log.error(
                        "[Batch] Batch {} failed for {}.{}, size={}, will NOT fallback (tx may be rolled back): {}",
                        action, batch.get(0).getSchema(), batch.get(0).getTable(),
                        batch.size(), e.getMessage());
                    throw e;
                }
            }
        }
    }

    /**
     * 将多条同表同操作的单行 row changes 合并为一条批量 SQL 并执行。
     * INSERT → INSERT INTO t(cols) VALUES(...),(...),...
     * DELETE → DELETE FROM t WHERE (pk) IN ((...),(...),...)
     * UPDATE → CASE WHEN UPDATE; identity/UK changes are executed as single-row UPDATE
     */
    private void flushBatch(Connection conn, List<DefaultRowChange> batch,
                            DBMSAction action, int insertMode) throws Exception {
        DefaultRowChange first = batch.get(0);
        TableInfo dstTbInfo = dbMetaCache.getTableInfo(first.getSchema(), first.getTable());

        if (action == DBMSAction.UPDATE) {
            flushUpdateBatch(conn, batch, dstTbInfo);
            return;
        }

        // INSERT / DELETE
        DefaultRowChange merged = new DefaultRowChange(action, first.getSchema(),
            first.getTable(), first.getColumnSet());

        for (DefaultRowChange rc : batch) {
            if (rc.getRowSize() != 1) {
                throw new PolardbxException(
                    String.format("[Batch] batch element should contain exactly 1 row, but got %d rows for %s.%s",
                        rc.getRowSize(), rc.getSchema(), rc.getTable()));
            }
            merged.addRowData(rc.getRowData(1));
        }

        MergeDmlSqlContext ctx;
        if (action == DBMSAction.INSERT) {
            ctx = DmlApplyHelper.getMergeInsertSqlExecContext(merged, dstTbInfo, insertMode);
        } else {
            ctx = DmlApplyHelper.getMergeDeleteSqlExecContext(merged, dstTbInfo);
        }
        DmlApplyHelper.execSqlContext(conn, ctx);
    }

    /**
     * 批量 UPDATE 实现：分段顺序执行模式。
     * <p>
     * 按顺序遍历 batch，遇到 identity（PK+分区键）、UK 或外列变更的行就切断：
     * - identity/UK/外列均不变的普通行累积后使用 CASE WHEN 批量 UPDATE
     * - identity/UK 变更行单独执行，保留行迁移或唯一键让位的原始事件顺序
     * - 外列变更行单独执行，确保只消费该事件携带的恢复后逻辑数据
     * <p>
     * 例：[normal1, normal2, special3, normal4, normal5]
     * → CASE WHEN batch [normal1, normal2]
     * → 单条 UPDATE [special3]
     * → CASE WHEN batch [normal4, normal5]
     */
    private void flushUpdateBatch(Connection conn, List<DefaultRowChange> batch,
                                  TableInfo dstTbInfo) throws Exception {
        DefaultRowChange first = batch.get(0);
        List<? extends DBMSColumn> columns = first.getColumns();
        String fullTbName = dstTbInfo.getSchema() + "." + dstTbInfo.getName();

        // rowSize 校验
        for (DefaultRowChange rc : batch) {
            if (rc.getRowSize() != 1) {
                throw new PolardbxException(
                    String.format("[Batch] batch element should contain exactly 1 row, but got %d rows for %s.%s",
                        rc.getRowSize(), rc.getSchema(), rc.getTable()));
            }
        }

        // 检测是否存在不支持比较的列类型（VECTOR/GEOMETRY等）在 identity columns 中，有则整批降级逐条执行
        List<String> identityColumns = dstTbInfo.getKeyList();
        // This set is used for SET-column selection only. In particular, externalized columns must never
        // leak into appendIdentityCondition(), because a BlobRef/raw payload is not a row locator.
        Set<String> blindUpdateExcludedColumns = DmlApplyHelper.getBlindUpdateExcludedColumns(dstTbInfo,
            first.getExternalizedColumnNames());
        for (String idCol : identityColumns) {
            ColumnInfo colInfo = dstTbInfo.getColumnInfoOrNull(idCol);
            if (colInfo != null && DmlApplyHelper.isNonComparableType(colInfo)) {
                DmlApplyHelper.executeDML(conn, batch, conflictStrategy);
                return;
            }
        }

        // 分段顺序执行主循环
        List<DefaultRowChange> pendingBatch = new ArrayList<>();

        for (DefaultRowChange rc : batch) {
            // identity/UK changes retain transition order; an external-column change must retain the one
            // row event whose after-image contains the restored payload. Both forms execute row by row.
            if (isIdentityOrUkChanged(rc, dstTbInfo) || isExternalizedColumnChanged(rc)) {
                // 先 flush 之前积累的普通行
                if (!pendingBatch.isEmpty()) {
                    flushCaseWhenUpdate(conn, dstTbInfo, pendingBatch, identityColumns,
                        blindUpdateExcludedColumns, columns, fullTbName);
                    pendingBatch.clear();
                }
                // Single-row SET includes changed protected columns, but never copies an unchanged external
                // BlobRef address. WHERE remains based on the true identity columns only.
                flushSingleRowUpdate(conn, dstTbInfo, rc, identityColumns,
                    blindUpdateExcludedColumns, columns, fullTbName);
            } else {
                pendingBatch.add(rc);
            }
        }

        // flush 剩余的普通行
        if (!pendingBatch.isEmpty()) {
            flushCaseWhenUpdate(conn, dstTbInfo, pendingBatch, identityColumns,
                blindUpdateExcludedColumns, columns, fullTbName);
        }
    }

    /**
     * 使用 CASE WHEN 语法生成批量 UPDATE SQL 并执行。
     * SET 列 = 所有列 - identity columns - externalized columns
     * WHERE = before-image identity columns 用 OR 连接
     * CASE WHEN 条件 = before-image identity columns
     */
    private void flushCaseWhenUpdate(Connection conn, TableInfo dstTbInfo,
                                     List<DefaultRowChange> rows, List<String> identityColumns,
                                     Set<String> blindUpdateExcludedColumns,
                                     List<? extends DBMSColumn> columns, String fullTbName) throws Exception {
        // MySQL CASE WHEN 取【第一个】匹配分支，
        // 会用旧 after-image 覆盖新的。这里按 before-image identity 去重并保留【最后一次】
        // (last-writer-wins)，与绝对值 after-image 语义一致，确保末次事件绝不被丢弃。
        if (rows.size() > 1) {
            LinkedHashMap<TableKey, DefaultRowChange> dedup = new LinkedHashMap<>();
            for (DefaultRowChange rc : rows) {
                Map<String, Serializable> identityValues = new LinkedHashMap<>();
                for (String idCol : identityColumns) {
                    identityValues.put(idCol, rc.getRowValue(1, idCol));
                }
                dedup.put(new TableKey(rc.getSchema(), rc.getTable(), identityValues), rc);
            }
            if (dedup.size() < rows.size()) {
                log.warn("[Dedup] TRIGGERED table={}, before={}, after={}", fullTbName, rows.size(), dedup.size());
                rows = new ArrayList<>(dedup.values());
            }
        }

        // CASE batching writes absolute after-images for every selected column. Exclude both true identity
        // columns and externalized columns: the latter may contain an unchanged canonical BlobRef address.
        List<DBMSColumn> setColumns = new ArrayList<>();
        boolean externalizedTable = rows.get(0).hasExternalizedColumns();
        for (DBMSColumn column : columns) {
            if (DmlApplyHelper.isFiltered(fullTbName, column)) {
                continue;
            }
            String lookupColumnName = externalizedTable ? column.getName().toLowerCase(Locale.ROOT) : column.getName();
            if (!blindUpdateExcludedColumns.contains(lookupColumnName)) {
                setColumns.add(column);
            }
        }

        // External-column changes have already been routed to the single-row path.
        // For an externalized table, an empty setColumns means this batch has no effective update.
        // Preserve the existing row-by-row fallback for ordinary tables.
        if (setColumns.isEmpty()) {
            if (!rows.get(0).hasExternalizedColumns()) {
                DmlApplyHelper.executeDML(conn, rows, conflictStrategy);
            }
            return;
        }

        // 构建 CASE WHEN 批量 UPDATE SQL
        List<Serializable> params = new ArrayList<>();
        StringBuilder sqlSb = new StringBuilder();
        sqlSb.append(String.format("UPDATE `%s`.`%s` SET ",
            CommonUtils.escape(dstTbInfo.getSchema()),
            CommonUtils.escape(dstTbInfo.getName())));

        // SET col1 = CASE WHEN <cond1> THEN ? WHEN <cond2> THEN ? END, col2 = ...
        for (int c = 0; c < setColumns.size(); c++) {
            DBMSColumn setCol = setColumns.get(c);
            String colName = repairDMLName(setCol.getName());
            if (c > 0) {
                sqlSb.append(", ");
            }
            sqlSb.append(colName).append(" = CASE");

            for (DefaultRowChange rc : rows) {
                sqlSb.append(" WHEN ");
                appendIdentityCondition(sqlSb, params, rc, identityColumns);
                sqlSb.append(" THEN ?");
                params.add(rc.getChangeValue(1, setCol.getName()));
            }
            sqlSb.append(" END");
        }

        // WHERE (id=? AND c1=?) OR (id=? AND c1=?)
        sqlSb.append(" WHERE ");
        for (int i = 0; i < rows.size(); i++) {
            DefaultRowChange rc = rows.get(i);
            if (i > 0) {
                sqlSb.append(" OR ");
            }
            sqlSb.append('(');
            appendIdentityCondition(sqlSb, params, rc, identityColumns);
            sqlSb.append(')');
        }

        MergeDmlSqlContext ctx = new MergeDmlSqlContext(sqlSb.toString(),
            dstTbInfo.getSchema(), dstTbInfo.getName(), params);
        int affectedRows = DmlApplyHelper.execSqlContext(conn, ctx);

        // 批量 UPDATE miss 检测：affected_rows < 预期行数说明部分行未命中，
        // CASE WHEN 无法定位具体 miss 行，整批降级逐条执行以复用单条路径的
        // UPDATE_MISSED → conflict strategy 补偿逻辑
        if (affectedRows < rows.size()) {
            log.warn("[Batch] CASE WHEN UPDATE affected {} rows, expected {}, fallback to row-by-row for {}",
                affectedRows, rows.size(), fullTbName);
            DmlApplyHelper.executeDML(conn, rows, conflictStrategy);
        }
    }

    /**
     * 生成并执行单条 UPDATE：
     * UPDATE `schema`.`table` SET col1=?, col2=?, ... WHERE old_id=? AND old_c1=?
     * SET = 普通列 + 发生变化的 identity/externalized protected columns
     * WHERE = before-image identity columns
     */
    private void flushSingleRowUpdate(Connection conn, TableInfo dstTbInfo,
                                      DefaultRowChange rc, List<String> identityColumns,
                                      Set<String> blindUpdateExcludedColumns,
                                      List<? extends DBMSColumn> columns, String fullTbName) throws Exception {
        List<Serializable> params = new ArrayList<>();
        StringBuilder sqlSb = new StringBuilder();
        sqlSb.append(String.format("UPDATE `%s`.`%s` SET ",
            CommonUtils.escape(dstTbInfo.getSchema()),
            CommonUtils.escape(dstTbInfo.getName())));

        // Both sets contain normalized names. blindUpdateExcludedColumns is the union of true identities and
        // externalized columns, while identityColumnSet is kept separate so WHERE semantics remain unchanged.
        boolean externalizedTable = rc.hasExternalizedColumns();
        Set<String> identityColumnSet = externalizedTable ? identityColumns.stream()
            .map(column -> column.toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet()) : new HashSet<>(identityColumns);

        // SET = 所有非 identity 列 + 仅变更了的 identity 列
        boolean firstSetCol = true;
        for (DBMSColumn column : columns) {
            if (DmlApplyHelper.isFiltered(fullTbName, column)) {
                continue;
            }
            String colName = column.getName();
            String lookupColName = externalizedTable ? colName.toLowerCase(Locale.ROOT) : colName;
            if (identityColumnSet.contains(lookupColName)) {
                // identity 列：只有 before != after 时才加入 SET
                int colIdx = rc.getColumnIndex(colName);
                if (colIdx < 0) {
                    continue;
                }
                Serializable beforeVal = rc.getRowValue(1, colIdx);
                Serializable afterVal = rc.getChangeValue(1, colIdx);
                if (Objects.equals(beforeVal, afterVal)) {
                    continue; // 值没变，排除出 SET
                }
            } else if (blindUpdateExcludedColumns.contains(lookupColName)
                && !rc.hasChangeColumn(column.getColumnIndex())) {
                // Externalized columns are protected SET columns, not identities: include the restored value
                // only when the source change bitmap says this column changed. Never compare the BlobRef
                // before-image with a restored after-image, and never add the column to the WHERE predicate.
                continue;
            }
            // 非 identity 列 或 变更了的 identity 列：加入 SET
            if (!firstSetCol) {
                sqlSb.append(", ");
            }
            sqlSb.append(repairDMLName(colName)).append("=?");
            params.add(rc.getChangeValue(1, colName));
            firstSetCol = false;
        }

        // WHERE = before-image identity columns
        sqlSb.append(" WHERE ");
        appendIdentityCondition(sqlSb, params, rc, identityColumns);

        MergeDmlSqlContext ctx = new MergeDmlSqlContext(sqlSb.toString(),
            dstTbInfo.getSchema(), dstTbInfo.getName(), params);
        int affectedRows = DmlApplyHelper.execSqlContext(conn, ctx);

        // 单条 UPDATE miss：按 conflict strategy 处理（与 DmlApplyHelper.executeDML 行为对齐）。
        // 构造函数已禁止 DIRECT_OVERWRITE，此处 conflictStrategy 恒不为 DIRECT_OVERWRITE，无需再判断。
        if (affectedRows == 0) {
            DmlApplyHelper.handleDupException(conn, rc, conflictStrategy,
                ConflictType.UPDATE_MISSED, null);
        }
    }

    /**
     * 向 SQL 追加 identity column 条件（col1=? AND col2=? AND ...），
     * 使用 before-image 定位旧行，同时将参数加入 params 列表。
     */
    private void appendIdentityCondition(StringBuilder sb, List<Serializable> params,
                                         DefaultRowChange rc, List<String> identityColumns) {
        for (int i = 0; i < identityColumns.size(); i++) {
            String colName = identityColumns.get(i);
            if (i > 0) {
                sb.append(" AND ");
            }
            Serializable value = rc.getRowValue(1, colName);
            if (value == null) {
                sb.append(repairDMLName(colName)).append(" IS NULL");
            } else {
                sb.append(repairDMLName(colName)).append("=?");
                params.add(value);
            }
        }
    }

    // ======================== Helper Methods ========================

    /**
     * 提取事务中的 PK + UK keys（用于 DAG 边构建）
     */
    private void extractAffectedKeys(Transaction transaction, Set<TableKey> affectedKeys) throws Exception {
        extractAffectedKeys(transaction, affectedKeys, new IdentityHashMap<>());
    }

    private void extractAffectedKeys(Transaction transaction, Set<TableKey> affectedKeys,
                                     Map<TableInfo, Set<String>> characterDagKeyColumnsCache) throws Exception {
        Transaction.RangeIterator iterator = transaction.rangeIterator();
        while (iterator.hasNext()) {
            Transaction.Range range = iterator.next();
            List<DBMSEvent> events = range.getEvents();

            for (DBMSEvent event : events) {
                DefaultRowChange rowChange = (DefaultRowChange) event;
                String schema = rowChange.getSchema();
                String table = rowChange.getTable();
                DBMSAction action = rowChange.getAction();

                TableInfo dstTbInfo = dbMetaCache.getTableInfo(schema, table);
                Set<String> characterDagKeyColumns = characterDagKeyColumnsCache.computeIfAbsent(dstTbInfo,
                    TransactionParallelApplierV3::extractCharacterDagKeyColumns);
                List<String> pks = dstTbInfo.getPks();
                List<Integer> pkColumnsIndex = dstTbInfo.getPkColumnsIndex(rowChange);

                // 提取 PK affected keys
                if (!CollectionUtils.isEmpty(pkColumnsIndex)) {
                    if (action == DBMSAction.UPDATE) {
                        Map<String, Serializable> beforeKeyValues = new HashMap<>(pkColumnsIndex.size());
                        for (int i = 0; i < pkColumnsIndex.size(); i++) {
                            Serializable value = rowChange.getRowValue(1, pkColumnsIndex.get(i));
                            beforeKeyValues.put(pks.get(i),
                                normalizeDagKeyValue(characterDagKeyColumns, pks.get(i), value));
                        }
                        affectedKeys.add(new TableKey(schema, table, beforeKeyValues));

                        Map<String, Serializable> afterKeyValues = new HashMap<>(pkColumnsIndex.size());
                        for (int i = 0; i < pkColumnsIndex.size(); i++) {
                            Serializable value = rowChange.getChangeValue(1, pkColumnsIndex.get(i));
                            afterKeyValues.put(pks.get(i),
                                normalizeDagKeyValue(characterDagKeyColumns, pks.get(i), value));
                        }
                        affectedKeys.add(new TableKey(schema, table, afterKeyValues));
                    } else {
                        Map<String, Serializable> keyValues = new HashMap<>(pkColumnsIndex.size());
                        for (int i = 0; i < pkColumnsIndex.size(); i++) {
                            Serializable value = rowChange.getRowValue(1, pkColumnsIndex.get(i));
                            keyValues.put(pks.get(i),
                                normalizeDagKeyValue(characterDagKeyColumns, pks.get(i), value));
                        }
                        affectedKeys.add(new TableKey(schema, table, keyValues));
                    }
                }

                // 提取 UK affected keys
                List<List<String>> ukGroups = dstTbInfo.getUkGroups();
                List<List<Integer>> ukGroupColumnsIndex = dstTbInfo.getUkGroupColumnsIndex(rowChange);
                if (!CollectionUtils.isEmpty(ukGroupColumnsIndex)) {
                    for (int i = 0; i < ukGroupColumnsIndex.size(); i++) {
                        List<Integer> oneGroupIndex = ukGroupColumnsIndex.get(i);

                        if (action == DBMSAction.UPDATE) {
                            Map<String, Serializable> beforeKeyValues = new HashMap<>(oneGroupIndex.size());
                            boolean beforeHasNull = false;
                            for (int j = 0; j < oneGroupIndex.size(); j++) {
                                Serializable value = rowChange.getRowValue(1, oneGroupIndex.get(j));
                                if (value == null) {
                                    beforeHasNull = true;
                                    break;
                                }
                                String columnName = ukGroups.get(i).get(j);
                                beforeKeyValues.put(columnName,
                                    normalizeDagKeyValue(characterDagKeyColumns, columnName, value));
                            }
                            if (!beforeHasNull) {
                                affectedKeys.add(new TableKey(schema, table, beforeKeyValues));
                            }

                            Map<String, Serializable> afterKeyValues = new HashMap<>(oneGroupIndex.size());
                            boolean afterHasNull = false;
                            for (int j = 0; j < oneGroupIndex.size(); j++) {
                                Serializable value = rowChange.getChangeValue(1, oneGroupIndex.get(j));
                                if (value == null) {
                                    afterHasNull = true;
                                    break;
                                }
                                String columnName = ukGroups.get(i).get(j);
                                afterKeyValues.put(columnName,
                                    normalizeDagKeyValue(characterDagKeyColumns, columnName, value));
                            }
                            if (!afterHasNull) {
                                affectedKeys.add(new TableKey(schema, table, afterKeyValues));
                            }
                        } else {
                            Map<String, Serializable> keyValues = new HashMap<>(oneGroupIndex.size());
                            boolean hasNull = false;
                            for (int j = 0; j < oneGroupIndex.size(); j++) {
                                Serializable value = rowChange.getRowValue(1, oneGroupIndex.get(j));
                                if (value == null) {
                                    hasNull = true;
                                    break;
                                }
                                String columnName = ukGroups.get(i).get(j);
                                keyValues.put(columnName,
                                    normalizeDagKeyValue(characterDagKeyColumns, columnName, value));
                            }
                            if (!hasNull) {
                                affectedKeys.add(new TableKey(schema, table, keyValues));
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Builds a derived value used only by DAG resource keys. The row image itself is never changed, so SQL SET,
     * WHERE parameters and compaction output retain the exact source value. A coarser DAG key is safe:
     * it may add dependencies, but cannot merge compaction chains because cached PK keys keep their original values.
     */
    private static Serializable normalizeDagKeyValue(Set<String> characterDagKeyColumns, String columnName,
                                                     Serializable value) {
        if (!(value instanceof String)) {
            return value;
        }
        if (!characterDagKeyColumns.contains(columnName)
            && !characterDagKeyColumns.contains(columnName.toLowerCase(Locale.ROOT))) {
            return value;
        }

        String stringValue = (String) value;
        int end = stringValue.length();
        while (end > 0 && stringValue.charAt(end - 1) == ' ') {
            end--;
        }
        return stringValue.substring(0, end).toLowerCase(Locale.ROOT);
    }

    private static Set<String> extractCharacterDagKeyColumns(TableInfo tableInfo) {
        List<ColumnInfo> columns = tableInfo.getColumns();
        if (CollectionUtils.isEmpty(columns)) {
            return Collections.emptySet();
        }
        Set<String> result = new HashSet<>();
        for (ColumnInfo columnInfo : columns) {
            if (isCharacterDagKeyType(columnInfo.getType())) {
                result.add(columnInfo.getName().toLowerCase(Locale.ROOT));
            }
        }
        return result;
    }

    private static boolean isCharacterDagKeyType(int jdbcType) {
        return jdbcType == Types.CHAR || jdbcType == Types.VARCHAR
            || jdbcType == Types.NCHAR || jdbcType == Types.NVARCHAR;
    }

    /**
     * 提取事务的 identity keys（PK + 分区键）并构建 event-to-key 索引，缓存到 TxNode 上。
     * 使用 getKeyList() 获取 PK + dbShardKey + tbShardKey，与 MergeApplier 的 RowKey 语义对齐，
     * 避免跨分片同主键数据被错误合并。
     * 在 DAG 构建阶段调用一次，后续 compaction 阶段直接使用缓存结果，
     * 避免在 identifyHotPkChains 和 findEventsForPk 中重复提取。
     */
    private void extractPkKeysAndBuildIndex(TxNode node, Transaction transaction) throws Exception {
        Set<TableKey> pkKeys = new HashSet<>();
        Map<TableKey, List<DefaultRowChange>> eventsByPk = new HashMap<>();
        // 按原始顺序缓存所有事件对象，供 createMergedTransaction 复用。
        // persist=ON 时避免再次 rangeIterator() 反序列化出新对象导致 supersede 引用判等失效。
        List<DefaultRowChange> orderedEvents = new ArrayList<>();

        Transaction.RangeIterator iterator = transaction.rangeIterator();
        while (iterator.hasNext()) {
            Transaction.Range range = iterator.next();
            List<DBMSEvent> events = range.getEvents();

            for (DBMSEvent event : events) {
                DefaultRowChange rowChange = (DefaultRowChange) event;
                String schema = rowChange.getSchema();
                String table = rowChange.getTable();
                DBMSAction action = rowChange.getAction();
                orderedEvents.add(rowChange);

                TableInfo dstTbInfo = dbMetaCache.getTableInfo(schema, table);
                // 使用 PK + 分区键作为 identity columns，与 MergeApplier 对齐
                List<String> identityColumns = dstTbInfo.getKeyList();
                // 过滤出 binlog 中存在的列名及其索引，保持一一对应
                List<String> validColumns = new ArrayList<>(identityColumns.size());
                List<Integer> validColumnsIndex = new ArrayList<>(identityColumns.size());
                for (String colName : identityColumns) {
                    int idx = rowChange.getColumnIndex(colName);
                    if (idx >= 0) {
                        validColumns.add(colName);
                        validColumnsIndex.add(idx);
                    }
                }

                if (CollectionUtils.isEmpty(validColumnsIndex)) {
                    continue;
                }

                // Before key (PK + 分区键)
                Map<String, Serializable> beforeKeyValues = new HashMap<>(validColumnsIndex.size());
                for (int i = 0; i < validColumnsIndex.size(); i++) {
                    Serializable value = rowChange.getRowValue(1, validColumnsIndex.get(i));
                    beforeKeyValues.put(validColumns.get(i), value);
                }
                TableKey beforeKey = new TableKey(schema, table, beforeKeyValues);
                pkKeys.add(beforeKey);
                eventsByPk.computeIfAbsent(beforeKey, k -> new ArrayList<>()).add(rowChange);

                if (action == DBMSAction.UPDATE) {
                    // After key (PK + 分区键)
                    Map<String, Serializable> afterKeyValues = new HashMap<>(validColumnsIndex.size());
                    for (int i = 0; i < validColumnsIndex.size(); i++) {
                        Serializable value = rowChange.getChangeValue(1, validColumnsIndex.get(i));
                        afterKeyValues.put(validColumns.get(i), value);
                    }
                    TableKey afterKey = new TableKey(schema, table, afterKeyValues);
                    pkKeys.add(afterKey);
                    // 仅当 after-key 与 before-key 不同时追加索引，避免重复
                    if (!afterKey.equals(beforeKey)) {
                        eventsByPk.computeIfAbsent(afterKey, k -> new ArrayList<>()).add(rowChange);
                    }
                }
            }
        }

        node.cachedPkKeys = pkKeys;
        node.eventsByPk = eventsByPk;
        node.cachedEvents = orderedEvents;
    }

    /**
     * 在节点的事务中查找匹配指定 PK 的事件。
     * 使用 DAG 构建阶段缓存的 eventsByPk 索引，O(1) 查找替代 O(N) 扫描。
     */
    private List<DefaultRowChange> findEventsForPk(TxNode node, TableKey targetPk) {
        List<DefaultRowChange> result = node.eventsByPk.get(targetPk);
        return result != null ? result : Collections.emptyList();
    }

    /**
     * 判断 UPDATE 是否修改了 identity key 或任意 UK 列。
     * <p>
     * identity key 变化表示行定位或分片位置发生变化；UK 变化还可能是其他行后续占用该唯一键前
     * 必需的让位步骤。两者都不能被 per-PK supersede 删除或进入无序的 CASE WHEN 批量更新。
     */
    private boolean isIdentityOrUkChanged(DefaultRowChange rowChange, TableInfo dstTbInfo) {
        Set<String> keyColumns = new LinkedHashSet<>(dstTbInfo.getKeyList());
        List<List<String>> ukGroups = dstTbInfo.getUkGroups();
        if (!CollectionUtils.isEmpty(ukGroups)) {
            for (List<String> ukGroup : ukGroups) {
                if (!CollectionUtils.isEmpty(ukGroup)) {
                    keyColumns.addAll(ukGroup);
                }
            }
        }

        for (String colName : keyColumns) {
            int colIndex = rowChange.getColumnIndex(colName);
            if (colIndex < 0) {
                continue;
            }
            Serializable beforeVal = rowChange.getRowValue(1, colIndex);
            Serializable afterVal = rowChange.getChangeValue(1, colIndex);
            if (!Objects.deepEquals(beforeVal, afterVal)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether this UPDATE changes at least one logical EXTERNALIZE column.
     * <p>
     * Externalized columns are deliberately not added to {@link TableInfo#getKeyList()}: they must not
     * participate in row identity, WHERE generation or DAG keys. They only act as compaction/batch barriers
     * so the original event carrying the restored logical value cannot be superseded or rewritten from an
     * unchanged BlobRef address. The source change bitmap is authoritative here; comparing before/after
     * objects would mix physical-address and restored-value representations.
     */
    private boolean isExternalizedColumnChanged(DefaultRowChange rowChange) {
        Set<String> externalizedColumns = rowChange.getExternalizedColumnNames();
        if (CollectionUtils.isEmpty(externalizedColumns)) {
            return false;
        }

        for (String columnName : externalizedColumns) {
            int columnIndex = rowChange.getColumnIndex(columnName);
            if (columnIndex >= 0 && rowChange.hasChangeColumn(columnIndex)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 简单计算最长依赖链长度（DFS）
     */
    private int calcLongestPath(List<TxNode> nodes) {
        Map<TxNode, Integer> memo = new HashMap<>();
        int maxLen = 0;
        for (TxNode node : nodes) {
            maxLen = Math.max(maxLen, dfsLen(node, memo));
        }
        return maxLen;
    }

    private int dfsLen(TxNode node, Map<TxNode, Integer> memo) {
        if (memo.containsKey(node)) {
            return memo.get(node);
        }
        int maxChild = 0;
        for (TxNode child : node.downstream) {
            maxChild = Math.max(maxChild, dfsLen(child, memo));
        }
        memo.put(node, 1 + maxChild);
        return 1 + maxChild;
    }
}
