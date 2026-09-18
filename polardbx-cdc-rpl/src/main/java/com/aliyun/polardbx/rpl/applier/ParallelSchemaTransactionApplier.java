/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.dao.RplDdlMapperExt;
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.binlog.domain.po.RplDdlSub;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PARALLEL_SCHEMA_CHANNEL_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PARALLEL_SCHEMA_CHANNEL_PARALLELISM;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.rpl.common.LogUtil.getSkipDdlLogger;
import static com.aliyun.polardbx.rpl.pipeline.SerialPipeline.shouldSkip;

/**
 * 库级别并行 Transaction Applier
 * 参考 ParallelSchemaApplier 实现，支持按 Schema 分组并行执行事务
 *
 * @author mario
 */
@Slf4j
public class ParallelSchemaTransactionApplier {

    private final ExecutorService parallelSchemaExecutorService;
    private final ConcurrentHashMap<String, SchemaChannel> schemaChannels;
    private final ConcurrentHashMap<String, String> maxDdlTsoCheckpointMap;
    @Getter
    private final Semaphore schemaChannelSemaphore;
    @Getter
    private final AtomicReference<Throwable> schemaChannelError;

    private String lastFlushedPosition;
    private long lastCheckFlushTime = System.currentTimeMillis();
    private long batchId;

    // 复用 ParallelSchemaApplier 的元数据管理（max position/min position 等）
    private final ParallelSchemaApplier delegateApplier;

    public ParallelSchemaTransactionApplier() {
        this.parallelSchemaExecutorService = Executors.newCachedThreadPool(
            new ThreadFactoryBuilder().setNameFormat("parallel-schema-tran-executor-%d").build());

        this.schemaChannels = new ConcurrentHashMap<>();
        this.maxDdlTsoCheckpointMap = new ConcurrentHashMap<>();
        this.schemaChannelSemaphore = new Semaphore(getInt(RPL_PARALLEL_SCHEMA_CHANNEL_PARALLELISM));
        this.schemaChannelError = new AtomicReference<>();

        // 创建 delegate applier 用于复用元数据管理逻辑
        this.delegateApplier = new ParallelSchemaApplier();
        this.initMaxDdlTsoCheckPoint();
    }

    /**
     * 初始化 DDL TSO 检查点，用于过滤已执行过的 DML 和 DDL
     */
    private void initMaxDdlTsoCheckPoint() {
        RplDdlMapperExt rplDdlMapperExt = SpringContextHolder.getObject(RplDdlMapperExt.class);
        List<RplDdl> mainCheckPointList = rplDdlMapperExt.getCheckpointTsoListForDdlMain(
            TaskContext.getInstance().getStateMachineId(), TaskContext.getInstance().getTaskId());
        List<RplDdlSub> subCheckPointList = rplDdlMapperExt.getCheckpointTsoListForDdlSub(
            TaskContext.getInstance().getStateMachineId(), TaskContext.getInstance().getTaskId());
        mainCheckPointList.forEach(p -> {
            String key = buildCheckPointKey(p.getSchemaName(), p.getParallelSeq());
            this.maxDdlTsoCheckpointMap.put(key, p.getDdlTso());
        });
        subCheckPointList.forEach(p -> {
            String key = buildCheckPointKey(p.getSchemaName(), p.getParallelSeq());
            String tso = this.maxDdlTsoCheckpointMap.computeIfAbsent(key, k -> p.getDdlTso());
            if (StringUtils.compare(p.getDdlTso(), tso) > 0) {
                this.maxDdlTsoCheckpointMap.put(key, p.getDdlTso());
            }
        });
        log.info("max ddl tso checkpoint map is initialized , {} ", JSONObject.toJSONString(maxDdlTsoCheckpointMap));
    }

    private String buildCheckPointKey(String schemaName, long parallelSeq) {
        return schemaName.toLowerCase() + "." + parallelSeq;
    }

    public void stop() {
        if (parallelSchemaExecutorService != null) {
            parallelSchemaExecutorService.shutdownNow();
        }
        if (schemaChannels != null) {
            schemaChannels.values().forEach(s -> {
                try {
                    s.close(true);
                } catch (Throwable t) {
                    log.error("Close schema channel failed", t);
                }
            });
        }
        if (delegateApplier != null) {
            delegateApplier.stop();
        }
        log.info("parallel schema transaction applier stopped.");
    }

    /**
     * 按 Schema 分组并行执行事务
     */
    public void parallelApply(List<Transaction> transactionBatch) throws Exception {
        Map<String, List<Transaction>> groupedTransactions = new HashMap<>();
        long totalTransactionCount = 0;
        long ddlTransactionCount = 0;
        String firstPosition = "";
        String lastPosition = "";

        // Step1: 按 Schema 进行分组
        for (Transaction tx : transactionBatch) {
            DBMSEvent firstEvent = tx.peekFirst();
            if (firstEvent == null) {
                continue;
            }
            String schemaName = extractSchemaName(tx);

            if (StringUtils.isBlank(schemaName)) {
                throw new PolardbxException("schema name can`t be null or empty for transaction " + tx + " pos :"
                    + getTransactionPosition(tx));
                // todo 需要正确处理这个异常
            }

            schemaName = schemaName.toLowerCase();

            // 检查是否为需要串行执行的 DDL
            if (isSerialDdl(tx, schemaName)) {
                log.warn("meet a serial executing ddl transaction, with schema {} ", schemaName);

                // flush previous transactions
                parallelApplyInternal(groupedTransactions, totalTransactionCount, ddlTransactionCount,
                    firstPosition, lastPosition, true);
                groupedTransactions.clear();
                totalTransactionCount = 0;
                ddlTransactionCount = 0;

                // apply this ddl separately
                serialApplyInternal(schemaName, tx);
                continue;
            }

            if (isDdlTransaction(tx)) {
                ddlTransactionCount++;
            }

            // channel 模式下，遇到新 schema 时先 flush 当前分组
            if (getBoolean(RPL_PARALLEL_SCHEMA_CHANNEL_ENABLED) && !groupedTransactions.isEmpty()
                && !groupedTransactions.containsKey(schemaName)) {
                parallelApplyInternal(groupedTransactions, totalTransactionCount, ddlTransactionCount,
                    firstPosition, lastPosition, false);
                groupedTransactions.clear();
                totalTransactionCount = 0;
                ddlTransactionCount = 0;
            }

            // 按 Schema 进行分组
            if (groupedTransactions.isEmpty()) {
                firstPosition = getTransactionPosition(tx);
            }
            List<Transaction> list = groupedTransactions.computeIfAbsent(schemaName, k -> new ArrayList<>());
            list.add(tx);
            lastPosition = getTransactionPosition(tx);
            totalTransactionCount++;
        }

        parallelApplyInternal(groupedTransactions, totalTransactionCount, ddlTransactionCount,
            firstPosition, lastPosition, false);
    }

    /**
     * 从 Transaction 中提取 Schema 名称
     */
    private String extractSchemaName(Transaction tx) {
        DBMSEvent firstEvent = tx.peekFirst();
        if (firstEvent == null) {
            return null;
        }

        if (firstEvent instanceof DefaultRowChange) {
            return ((DefaultRowChange) firstEvent).getSchema();
        } else if (firstEvent instanceof DefaultQueryLog) {
            return ((DefaultQueryLog) firstEvent).getSchema();
        }

        return null;
    }

    /**
     * 获取 Transaction 的位点信息
     */
    private String getTransactionPosition(Transaction tx) {
        DBMSEvent lastEvent = tx.peekLast();
        return lastEvent != null ? lastEvent.getPosition() : "";
    }

    /**
     * 判断是否为 DDL 事务
     */
    private boolean isDdlTransaction(Transaction tx) {
        DBMSEvent firstEvent = tx.peekFirst();
        return firstEvent != null && DdlApplyHelper.isDdl(firstEvent);
    }

    /**
     * 判断是否为需要串行执行的 DDL
     */
    private boolean isSerialDdl(Transaction tx, String schemaName) {
        if (!isDdlTransaction(tx)) {
            return false;
        }

        DefaultQueryLog queryLog = (DefaultQueryLog) tx.peekFirst();
        String originalDdlSql = DdlApplyHelper.getOriginSql(queryLog.getQuery());

        // 实例级 DDL、跨库 DDL、同步点、函数 DDL 需要串行执行
        return StringUtils.equalsAnyIgnoreCase(schemaName, "cdc_token_db")
            || delegateApplier.isCrossDatabase(originalDdlSql, schemaName)
            || delegateApplier.isSyncPoint(originalDdlSql, schemaName)
            || ParallelSchemaApplier.isFunctionDdl(originalDdlSql);
    }

    /**
     * 串行执行单个事务
     */
    private void serialApplyInternal(String schemaName, Transaction tx) throws Exception {
        log.warn("start to apply transaction in serial mode separately, with schema {}, position {}.",
            schemaName, getTransactionPosition(tx));

        SchemaExecutor executor = new SchemaExecutor(schemaName, Collections.singletonList(tx));
        executor.call();

        recordAndFlushPosition(getTransactionPosition(tx), true);
    }

    /**
     * 并行执行事务（内部实现），根据配置切换 block 或 channel 模式
     */
    private void parallelApplyInternal(Map<String, List<Transaction>> groupedTransactions,
                                       long totalTransactionCount, long ddlTransactionCount,
                                       String firstPosition, String lastPosition,
                                       boolean waitAllComplete) throws Exception {
        batchId++;
        if (getBoolean(RPL_PARALLEL_SCHEMA_CHANNEL_ENABLED)) {
            parallelApplyWithChannel(groupedTransactions, totalTransactionCount, ddlTransactionCount,
                firstPosition, lastPosition, waitAllComplete);
        } else {
            parallelApplyWithBlock(groupedTransactions, totalTransactionCount, ddlTransactionCount, lastPosition);
        }
    }

    /**
     * 以 block 模式并行执行事务：提交任务后阻塞等待所有完成
     */
    private void parallelApplyWithBlock(Map<String, List<Transaction>> groupedTransactions,
                                        long totalTransactionCount, long ddlTransactionCount,
                                        String lastPosition) throws Exception {
        if (groupedTransactions.isEmpty()) {
            recordAndFlushPosition(lastPosition, false);
            return;
        }

        if (log.isDebugEnabled()) {
            log.debug("start to apply transactions in parallel schema block mode, batch id {}, "
                    + "batch group count is {}, batch transaction count is {},"
                    + " ddl count is {}, schema list is {}.",
                batchId, groupedTransactions.size(), totalTransactionCount, ddlTransactionCount,
                groupedTransactions.keySet());
        }

        // submit
        Map<String, Future<Void>> futures = new HashMap<>();
        groupedTransactions.forEach((key, value) ->
            futures.put(key, parallelSchemaExecutorService.submit(new SchemaExecutor(key, value))));

        // wait
        Set<String> failedSchemas = new HashSet<>();
        for (Map.Entry<String, Future<Void>> entry : futures.entrySet()) {
            try {
                entry.getValue().get();
            } catch (Throwable t) {
                log.error("apply transactions error for schema {}.", entry.getKey(), t);
                failedSchemas.add(entry.getKey());
            }
        }

        // retry once
        // 有些情况下的失败，可能是由于不同schema之间有依赖(主要是ddl)，这里尽最大努力进行一下重试
        initMaxDdlTsoCheckPoint();
        failedSchemas.forEach(s -> {
            log.warn("retry to apply transactions for schema {}", s);
            try {
                SchemaExecutor executor = new SchemaExecutor(s, groupedTransactions.get(s));
                executor.call();
            } catch (Exception e) {
                throw new PolardbxException("retry apply transactions failed for schema " + s, e);
            }
        });

        recordAndFlushPosition(lastPosition, true);
    }

    /**
     * 记录并刷新位点
     */
    private void recordAndFlushPosition(String position, boolean isFlush) {
        if (StringUtils.isBlank(position)) {
            return;
        }
        StatisticalProxy.getInstance().recordPosition(position);
        if (isFlush) {
            StatisticalProxy.getInstance().flushPosition();
        }
        lastFlushedPosition = position;
        log.info("successfully recording and flushing position: " + lastFlushedPosition);
    }

    /**
     * 以 channel 流式模式并行执行事务
     * 参考 ParallelSchemaApplier.parallelApplyWithChannel 实现
     */
    @SneakyThrows
    private void parallelApplyWithChannel(Map<String, List<Transaction>> groupedTransactions,
                                          long totalTransactionCount, long ddlTransactionCount,
                                          String firstPosition, String lastPosition,
                                          boolean waitAllCompleteBeforeApply) {
        if (groupedTransactions.isEmpty() && schemaChannels.isEmpty()) {
            recordAndFlushPosition(lastPosition, false);
            return;
        }

        if (groupedTransactions.size() > 1) {
            throw new PolardbxException(
                "submitted transactions should belong to only one schema, but actual is "
                    + groupedTransactions.keySet());
        }

        if (log.isDebugEnabled()) {
            log.debug("start to apply transactions in parallel schema channel mode, batch id {}, "
                    + "batch group count is {}, batch transaction count is {}, ddl count is {}, "
                    + "schema list is {}, first position {}, last position {}, wait all YN {}.",
                batchId, groupedTransactions.size(), totalTransactionCount, ddlTransactionCount,
                groupedTransactions.keySet(), firstPosition, lastPosition, waitAllCompleteBeforeApply);
        }

        tryFlushMinPosition();

        Iterator<Map.Entry<String, List<Transaction>>> iterator = groupedTransactions.entrySet().iterator();
        while (iterator.hasNext()) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            if (schemaChannelError.get() != null) {
                throw schemaChannelError.get();
            }

            double oldUsedRatio = JvmUtils.getOldUsedRatio();
            int totalRemaining = schemaChannels.values().stream().mapToInt(SchemaChannel::remaining).sum();
            if (oldUsedRatio < 0.7 || totalRemaining < 16384) {
                Map.Entry<String, List<Transaction>> entry = iterator.next();
                SchemaChannel schemaChannel = schemaChannels.computeIfAbsent(entry.getKey(), SchemaChannel::new);
                schemaChannel.add(entry.getValue());
                if (log.isDebugEnabled()) {
                    log.debug("group transactions is put to buffer, with batch id " + batchId);
                }
            } else {
                Thread.sleep(10);
                tryFlushMinPosition();
            }
        }

        if (waitAllCompleteBeforeApply) {
            while (true) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                if (schemaChannelError.get() != null) {
                    throw schemaChannelError.get();
                }

                Optional<SchemaChannel> optional =
                    schemaChannels.values().stream().filter(c -> !c.isEmpty()).findAny();
                if (!optional.isPresent()) {
                    schemaChannels.values().forEach(c -> c.close(false));
                    schemaChannels.clear();
                    break;
                } else {
                    Thread.sleep(10);
                    tryFlushMinPosition();
                }
            }
        }

        tryFlushMinPosition();
    }

    /**
     * 推进所有 schema channel 中的最小位点
     */
    private void tryFlushMinPosition() {
        if (System.currentTimeMillis() - lastCheckFlushTime < 10000) {
            return;
        }

        List<Triple<String, String, Integer>> snapshot = schemaChannels.values().stream()
            .map(s -> Triple.of(s.getSchemaName(), s.getPosition(), s.remaining()))
            .collect(Collectors.toList());

        Optional<Triple<String, String, Integer>> minOptional = snapshot.stream()
            .min((o1, o2) -> BinlogPosition.comparePositionString(o1.getMiddle(), o2.getMiddle()));
        Optional<Triple<String, String, Integer>> maxOptional = snapshot.stream()
            .max((o1, o2) -> BinlogPosition.comparePositionString(o1.getMiddle(), o2.getMiddle()));
        Pair<String, String> minMaxPair = Pair.of(
            minOptional.map(triple -> triple.getLeft() + ":" + triple.getMiddle()).orElse(""),
            maxOptional.map(triple -> triple.getLeft() + ":" + triple.getMiddle()).orElse(""));

        printDetail(snapshot, minMaxPair);

        if (minOptional.isPresent() && StringUtils.isNotBlank(minOptional.get().getMiddle())) {
            if (BinlogPosition.comparePositionString(minOptional.get().getMiddle(), lastFlushedPosition) < 0) {
                throw new PolardbxException(String.format("new position can`t be less than last position, %s, %s",
                    minOptional.get().getMiddle(), lastFlushedPosition));
            }

            if (!StringUtils.equals(minOptional.get().getMiddle(), lastFlushedPosition)) {
                recordAndFlushPosition(minOptional.get().getMiddle(), true);
            }

            snapshot.forEach(t -> tryRemoveTimeOutSchemaChannel(t.getLeft()));
        }

        lastCheckFlushTime = System.currentTimeMillis();
    }

    private void printDetail(List<Triple<String, String, Integer>> snapshot, Pair<String, String> minMaxPair) {
        int totalRemaining = snapshot.stream().mapToInt(Triple::getRight).sum();
        log.info("min position in schema channels is {}, max position is {}, total remaining transactions is {},"
                + " semaphore available permits {}, remaining schema list is {}.", minMaxPair.getKey(),
            minMaxPair.getValue(), totalRemaining, schemaChannelSemaphore.availablePermits(),
            JSONObject.toJSONString(snapshot, true));
    }

    private void tryRemoveTimeOutSchemaChannel(String schemaName) {
        SchemaChannel channel = schemaChannels.get(schemaName);
        if (System.currentTimeMillis() - channel.lastExecuteTime > 60000 && channel.isEmpty()) {
            channel.close(false);
            schemaChannels.remove(channel.getSchemaName());
        }
    }

    /**
     * Schema 执行器
     * 关键逻辑：将 DDL 和 DML 事务分离后分别执行，避免混合批次导致 ClassCastException
     * 参考 ParallelSchemaApplier.Executor 的实现
     */
    private class SchemaExecutor implements Callable<Void> {
        private final String schemaName;
        private final List<Transaction> transactions;
        private final String maxDdlTsoCheckpoint;

        public SchemaExecutor(String schemaName, List<Transaction> transactions) {
            this.schemaName = schemaName;
            this.transactions = transactions;
            this.maxDdlTsoCheckpoint = ParallelSchemaTransactionApplier.this.maxDdlTsoCheckpointMap
                .getOrDefault(buildCheckPointKey(schemaName, 0), "");
        }

        @Override
        public Void call() throws Exception {
            long start = System.currentTimeMillis();

            try {
                // 将 DDL 和 DML 事务分离后分别执行，确保 DDL 按顺序作为屏障点
                List<Transaction> dmlBatch = new ArrayList<>();
                for (Transaction tx : transactions) {
                    if (tx.getEventCount() == 0) {
                        continue;
                    }

                    // 过滤已经执行过的事务（TSO < checkpoint）
                    DBMSEvent firstEvent = tx.peekFirst();
                    if (firstEvent != null && shouldSkip(firstEvent, maxDdlTsoCheckpoint)) {
                        if (getSkipDdlLogger().isDebugEnabled()) {
                            getSkipDdlLogger().debug(
                                "transaction is skipped, with schema {}, position {}, checkpoint tso {}.",
                                schemaName, getTransactionPosition(tx), maxDdlTsoCheckpoint);
                        }
                        continue;
                    }

                    if (isDdlTransaction(tx)) {
                        // 先 flush 前面积累的 DML 事务
                        if (!dmlBatch.isEmpty()) {
                            StatisticalProxy.getInstance().tranApply(dmlBatch);
                            dmlBatch = new ArrayList<>();
                        }
                        // 单独执行 DDL 事务（size == 1，确保走 TransactionApplier 的 DDL 路径）
                        StatisticalProxy.getInstance().tranApply(Collections.singletonList(tx));
                    } else {
                        dmlBatch.add(tx);
                    }
                }
                // flush 剩余的 DML 事务
                if (!dmlBatch.isEmpty()) {
                    StatisticalProxy.getInstance().tranApply(dmlBatch);
                }

                long cost = System.currentTimeMillis() - start;
                log.info("Schema {} applied {} transactions, cost {}ms",
                    schemaName, transactions.size(), cost);

            } catch (Exception e) {
                log.error("Schema {} apply transactions failed, transaction count: {}",
                    schemaName, transactions.size(), e);
                throw e;
            }

            return null;
        }
    }

    /**
     * Schema 通道：每个 schema 一个独立线程，异步消费事务队列
     * 参考 ParallelSchemaApplier.SchemaChannel 实现
     */
    private class SchemaChannel {
        @Getter
        private final String schemaName;
        private final ConcurrentLinkedQueue<List<Transaction>> transactionQueue;
        private final ExecutorService executorService;
        private final AtomicInteger count;
        @Getter
        private volatile String position = "";
        private volatile long lastExecuteTime = System.currentTimeMillis();

        public SchemaChannel(String schemaName) {
            this.schemaName = schemaName;
            this.transactionQueue = new ConcurrentLinkedQueue<>();
            this.executorService = Executors.newSingleThreadExecutor(
                new ThreadFactoryBuilder().setNameFormat("schema-tran-channel-executor-" + schemaName).build());
            this.count = new AtomicInteger(0);

            this.executorService.submit(() -> {
                LinkedList<Transaction> batch = new LinkedList<>();

                while (true) {
                    if (transactionQueue.peek() == null) {
                        try {
                            executeBatch(batch);
                            Thread.sleep(10);
                            continue;
                        } catch (InterruptedException e) {
                            break;
                        }
                    }

                    List<Transaction> transactions = transactionQueue.poll();
                    batch.addAll(transactions);

                    if (batch.size() >= 8) {
                        executeBatch(batch);
                    }
                }
            });
        }

        @SneakyThrows
        public void executeBatch(LinkedList<Transaction> batch) {
            if (batch.isEmpty()) {
                return;
            }

            try {
                schemaChannelSemaphore.acquire();

                SchemaExecutor schemaExecutor = new SchemaExecutor(schemaName, new ArrayList<>(batch));
                schemaExecutor.call();

                lastExecuteTime = System.currentTimeMillis();
                position = getTransactionPosition(batch.getLast());
                count.addAndGet(-batch.size());

            } catch (Throwable t) {
                schemaChannelError.set(t);
                log.error("Fatal error in schema channel {}", schemaName, t);
                throw t;
            } finally {
                schemaChannelSemaphore.release();
                batch.clear();
            }
        }

        public void add(List<Transaction> transactions) {
            this.transactionQueue.add(transactions);
            this.count.addAndGet(transactions.size());
        }

        public int remaining() {
            return this.count.get();
        }

        public boolean isEmpty() {
            return this.count.get() == 0;
        }

        public void close(boolean force) {
            if (!force && !isEmpty()) {
                throw new PolardbxException("can`t close schema channel, because transaction buffer is not empty");
            }
            this.executorService.shutdownNow();
            log.info("schema transaction channel is closed with schema name " + schemaName);
        }
    }
}
