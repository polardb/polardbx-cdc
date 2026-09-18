/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.DecodeMode;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.util.RowsLogEventCompareCode;
import com.aliyun.polardbx.binlog.storage.util.RowsLogEventCompareUtil;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.rocksdb.RocksDBException;
import org.rocksdb.RocksIterator;
import org.rocksdb.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PARALLEL_RESTORE_BATCH_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PARALLEL_RESTORE_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PARALLEL_RESTORE_MAX_EVENT_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PARALLEL_RESTORE_PARALLELISM;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_EXTRACT_DISORDER_TRACE_ID_ALLOWED;

/**
 * Created by ziyang.lb
 **/
public class TxnBuffer implements Serializable {
    public static final AtomicLong CURRENT_TXN_BUFFER_COUNT = new AtomicLong(0);
    public static final AtomicLong CURRENT_TXN_BUFFER_PERSISTED_COUNT = new AtomicLong(0);

    private static final Logger logger = LoggerFactory.getLogger(TxnBuffer.class);
    private static final Logger traceIdLogger = LoggerFactory.getLogger("traceIdDisorderLogger");
    private static final String clusterType = DynamicApplicationConfig.getClusterType();
    private static final AtomicLong sequenceGenerator = new AtomicLong(0L);
    private static final AtomicLong entitySequenceGenerator = new AtomicLong(0L);
    private static final int beginKeySubSequence = 1;
    private static final String entityKeyPrefix = "TXN_BUFFER_ENTITY_";
    private Repository repository;

    private long entityPersistKey;
    private boolean entityPersisted;
    @Getter
    private TxnBufferEntity entity;
    @Getter
    private Long txnBufferId;
    @Setter
    private boolean returningFixEnabled;

    /**
     * 设置当前 TxnBuffer 的 partitionId，并传播到所有 TxnItemRef。
     * <p>确保在序列化为 TxnItem 时，每个 TxnItemRef 都携带其来源 DN 的 partitionId，
     * 以便 Dumper 在多流归并时进行确定性排序（不依赖 Dispatcher 拓扑）。</p>
     */
    public void setPartitionId(String partitionId) {
        if (entity != null && entity.refList != null) {
            for (TxnItemRef ref : entity.refList) {
                ref.setPartitionId(partitionId);
            }
        }
    }

    public TxnBuffer() {
    }

    TxnBuffer(TxnKey txnKey, Repository repository) {
        this.repository = repository;
        this.entity = new TxnBufferEntity();
        this.entity.txnKey = txnKey;
        this.entity.refList = new LinkedList<>();
        this.entity.started = new AtomicBoolean(false);
        this.entity.completed = new AtomicBoolean(false);
        this.entity.shouldPersist = false;
        this.entity.hasPersistingData = false;
        this.txnBufferId = nextSequence();
        this.entity.subSequenceGenerator = new AtomicInteger(beginKeySubSequence - 1);
        StorageMemoryLeakDectectorManager.getInstance().watch(this);
        CURRENT_TXN_BUFFER_COUNT.incrementAndGet();
    }

    /**
     * 将TxnBuffer标记为启动状态，处于启动状态的buffer才可以append数据
     */
    public boolean markStart() {
        return entity.started.compareAndSet(false, true);
    }

    /**
     * 将TxnBuffer标记为完成状态, 处于完成状态之后，不能再append数据
     */
    public void markComplete() {
        if (!entity.completed.compareAndSet(false, true)) {
            throw new PolardbxException("txn buffer has already completed, can't mark complete again.");
        }
        entity.itemSizeBeforeMerge = entity.refList.size();
    }

    public synchronized boolean persist() {
        if (entity != null && !entity.shouldPersist) {
            persistPreviousItems();
            entity.shouldPersist = true;
            CURRENT_TXN_BUFFER_PERSISTED_COUNT.incrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * 关闭TxnBuffer，如果有持久化数据，删除数据
     */
    void close() {
        StorageMemoryLeakDectectorManager.getInstance().unwatch(this);
        if (entity.started.compareAndSet(true, false)) {
            if (entity.hasPersistingData) {
                if (repository.getDeleteMode() == DeleteMode.RANGE) {
                    try {
                        byte[] beginKey = buildTxnItemRefKeyWithSubSequence(beginKeySubSequence);
                        getRepoUnit().deleteRange(beginKey, peekNextTxnItemRefKey().getRight());
                    } catch (RocksDBException e) {
                        throw new PolardbxException("delete rang failed", e);
                    }
                } else if (repository.getDeleteMode() == DeleteMode.SINGLE) {
                    entity.refList.forEach(r -> {
                        //在merge阶段，选中为delegate的buffer会包含所有的TxnItem，在close的时候，让每个buffer各司其职，只清理自己的TxnItem
                        if (r.getTxnBuffer() == this && r.isPersisted()) {
                            try {
                                r.delete();
                            } catch (RocksDBException e) {
                                throw new PolardbxException("delete txn item failed.", e);
                            }
                        }
                    });
                } else if (repository.getDeleteMode() == DeleteMode.NONE) {
                    // for test, do nothing
                } else {
                    throw new PolardbxException("Invalid Delete Mode : " + repository.getDeleteMode());
                }
            } else {
                entity.refList.forEach(r -> {
                    if (r.getTxnBuffer() == this) {
                        try {
                            r.delete();
                        } catch (RocksDBException e) {
                            throw new PolardbxException("delete txn item failed.", e);
                        }
                    }
                });
            }

            CURRENT_TXN_BUFFER_COUNT.decrementAndGet();
            if (entity.shouldPersist) {
                CURRENT_TXN_BUFFER_PERSISTED_COUNT.decrementAndGet();
            }
        }
    }

    /**
     * 将一批事务数据append到缓存队列
     */
    public void push(List<TxnBufferItem> txnItems) {
        txnItems.forEach(this::push);
    }

    /**
     * 将单个事务数据append到缓存队列
     */
    public void push(TxnBufferItem txnItem) {
        if (!entity.started.get()) {
            throw new PolardbxException("can't push item to not started txn buffer.");
        }

        if (isCompleted()) {
            throw new PolardbxException("can't push item to completed txn buffer.");
        }

        // traceId是允许重复的，但不能回跳，所以此处只对乱序的情况进行校验
        // 但有例外，即returning delete，原因见com.aliyun.polardbx.binlog.canal.LogEventUtil.buildTrace内注释
        if (!txnItem.isReturningEvent() && StringUtils.isNotBlank(entity.lastTraceId)
            && txnItem.getTraceId().compareTo(entity.lastTraceId) < 0) {
            boolean ignoreDisorderedTraceId = DynamicApplicationConfig.getBoolean(
                TASK_EXTRACT_DISORDER_TRACE_ID_ALLOWED);
            if (!ignoreDisorderedTraceId) {
                throw new PolardbxException("detected disorderly traceId，current traceId is " + txnItem.getTraceId()
                    + ",last traceId is " + entity.lastTraceId);
            } else {
                traceIdLogger.warn("detected disorderly traceId，current traceId is " + txnItem.getTraceId()
                    + " , last traceId is " + entity.lastTraceId + " , origin traceId is " + txnItem.getOriginTraceId()
                    + " , binlog file name is " + txnItem.getBinlogFile() + " , binlog position is " + txnItem
                    .getBinlogPosition());
            }
        }

        doAdd(txnItem);
    }

    public boolean isPersisted() {
        return entity.shouldPersist;
    }

    public void doAdd(TxnBufferItem txnItem) {
        //add to list && try persist
        TxnItemRef ref = new TxnItemRef(this, txnItem.getTraceId(), txnItem.getRowsQuery(),
            txnItem.getEventType(), txnItem.getPayload(), txnItem.getSchema(), txnItem.getTable(),
            txnItem.getHashKey(), txnItem.getPrimaryKey());
        ref.setLogicSqlId(txnItem.getLogicSqlId());

        entity.memSize += txnItem.size();
        entity.lastTraceId = txnItem.getTraceId();
        tryPersist(ref, txnItem.size());

        if (returningFixEnabled && txnItem.isReturningEvent() && (
            txnItem.getEventType() == LogEvent.DELETE_ROWS_EVENT_V1
                || txnItem.getEventType() == LogEvent.DELETE_ROWS_EVENT)) {
            // fix-delete event：打标并交由 addReturningEvent 进行重排序
            ref.setReturningEvent(true);
            addReturningEvent(ref);
        } else {
            entity.refList.add(ref);
        }

        if (logger.isDebugEnabled()) {
            logger.debug("accept an item for txn buffer " + entity.txnKey);
        }
    }

    /**
     * 处理 replace returning / insert ignore returning 优化产生的 fix-delete event，
     * 在 {@code refList} 中对其进行重排序，以避免下游 MySQL 消费 binlog 时出现 UK 冲突。
     *
     * <h2>背景</h2>
     * <p>replace returning 优化会先向 DN 乐观地执行 REPLACE，再由 CN 下发 fix-delete 物理 SQL。
     * DN 侧 binlog 中，fix-delete 对应的 DELETE event 天然在 INSERT/UPDATE event 之后，
     * 下游 MySQL 消费时会先看到 INSERT，触发 duplicate key 报错。</p>
     *
     * <h2>三种情况的处理（20260116 final 方案）</h2>
     * <ol>
     *   <li><b>情况①：fix-delete 的行在本逻辑 SQL 中没有对应的 INSERT/UPDATE</b><br>
     *       说明删除的是表中原有数据，将 delete event 及其 TABLE_MAP 提前到本逻辑 SQL 内
     *       物理 SQL id 恰好比 delete 小的那个 event 之前（即 startIdx 位置）；
     *       若找不到，则放到整个 refList 的最前面（startIdx=0）。</li>
     *   <li><b>情况②：fix-delete 的行与某 WRITE_ROWS_EVENT 完全/部分匹配</b><br>
     *       说明删除的是本 batch 中刚 INSERT 的数据，与对应 INSERT 做"消消乐"：
     *       <ul>
     *         <li>完全相同（TOTALLY_EQUAL）或 delete 包含全部 INSERT 行（LEFT_CONTAINS）：
     *             INSERT event 从 refList 中移除，delete 自身也不插入。</li>
     *         <li>INSERT 包含全部 delete 行（RIGHT_CONTAINS）：
     *             INSERT 被截短，delete 不插入。</li>
     *         <li>部分相同（PARTLY_EQUAL）：双方均被截短，继续向前处理剩余行。</li>
     *       </ul></li>
     *   <li><b>情况③：fix-delete 的行与某 UPDATE_ROWS_EVENT 的 after 部分匹配</b><br>
     *       说明删除的是本 batch 中通过 UPDATE 原地写入的数据，将 delete 拆成两份：
     *       与 UPDATE after 相同的部分（split-delete）紧跟在该 UPDATE 后，
     *       不同部分继续向前（情况①）。同时对 UPDATE 的 TABLE_MAP 打上 returning 标记，
     *       保证跨 DN 归并排序时该组事件能整体优先排出。</li>
     * </ol>
     *
     * <p>注意：以上三种情况可能在同一个 fix-delete event 中同时出现（batch replace 场景），
     * 代码通过 {@code shouldMoveDelete} 和 {@code deleteEventExist} 两个标志位跟踪处理状态。</p>
     *
     * @param ref fix-delete event 对应的 TxnItemRef（已设置 returningEvent=true）
     */
    private void addReturningEvent(TxnItemRef ref) {
        // fix-delete event 进入此函数前，其对应的 TABLE_MAP_EVENT 已经被 push 到 refList 末尾
        TxnItemRef deleteTableMapEventRef = entity.refList.getLast();
        boolean lastTableMaphasMoved = false;
        if (deleteTableMapEventRef.getEventType() != LogEvent.TABLE_MAP_EVENT) {
            // 正常情况下，fix-delete 前必定有一个 TABLE_MAP_EVENT。
            // 若最后一个元素不是 TABLE_MAP，说明该 TABLE_MAP 在处理之前的 fix-delete 时已被挪走，
            // 此时不能再执行"将 TABLE_MAP 和 delete 一起提前"的逻辑。
            lastTableMaphasMoved = true;
            logger.warn("last item in buffer type:{}, may table map has moved to front",
                deleteTableMapEventRef.getEventType());
        }

        // 由于 refList 是 LinkedList，随机访问代价高，必须通过 ListIterator 遍历
        ListIterator<TxnItemRef> iterator = entity.refList.listIterator(0);

        // startIdx：delete 最终应该插入的位置（情况①/③ 后剩余 diff 部分放到此处）
        // -1 表示尚未找到，最终若仍为 -1 则放到 refList 最前面（index 0）
        int startIdx = -1;

        // shouldMoveDelete：最终是否还需要将 delete 提前到 startIdx 位置
        // 情况②/③ 消除后置为 false
        boolean shouldMoveDelete = true;

        // deleteEventExist：delete event（入参 ref）当前是否还"存在"
        // 被完全消除后置为 false，以便后续跳过无效的 TABLE_MAP 处理
        boolean deleteEventExist = true;

        int currentSqlId = ref.getLogicSqlId();

        // 构建解码器，用于懒解析 TABLE_MAP 和 ROWS_LOG_EVENT
        // DecodeMode.PART_RETURNING：在处理ROWS_LOG_EVENT时保留table meta
        LogDecoder logDecoder = new LogDecoder(0, LogEvent.ENUM_END_EVENT, DecodeMode.PART_RETURNING);
        // returning 场景下解码的是内存中的合成 raw payload，position 固定从 4 开始，
        // 不存在真实 binlog 文件的 position 回退问题，禁用相关 warn 和修正逻辑，
        // 避免每次 decode 都触发 logger.warn + String.format（火焰图显示占约 29% CPU）。
        logDecoder.setNeedFixBigBinlogFileLogPos(false);
        // 同理，returning 场景不会遇到需要修正的 ROTATE_EVENT，禁用避免无谓判断
        logDecoder.setNeedFixRotate(false);
        LogContext logContext = new LogContext();
        // binlog 文件名仅用于 LogPosition 初始化，实际不使用，虚拟即可
        logContext.setLogPosition(new LogPosition("binlog.000001", 4));
        logContext.setFormatDescription(new FormatDescriptionLogEvent(4, LogEvent.BINLOG_CHECKSUM_ALG_CRC32));
        logContext.setServerCharactorSet(new ServerCharactorSet());

        // 解析 fix-delete 对应的 TABLE_MAP，将其列信息注入 logContext，
        // 以便后续对 ROWS_LOG_EVENT 进行正确的 schema 解析
        try {
            byte[] rawPayload = deleteTableMapEventRef.getRawPayload();
            logDecoder.decode(new LogBuffer(rawPayload, 0, rawPayload.length), logContext);
        } catch (Exception e) {
            logger.error("decode lastTableMapEvent... , rawPayload is {}, isPersisted:{}, eventData:{}",
                deleteTableMapEventRef.getRawPayload(), deleteTableMapEventRef.isPersisted(),
                deleteTableMapEventRef.getEventData());
            throw new RuntimeException(e);
        }

        // lastTableMapEventRef：遍历过程中最近一次遇到的 TABLE_MAP_EVENT，
        // 用于情况③：对其打 returning 标记，以便归并排序时整体优先排出
        TxnItemRef lastTableMapEventRef = null;

        while (iterator.hasNext()) {
            TxnItemRef item = iterator.next();
            int sqlId = item.getLogicSqlId();
            int eventType = item.getEventType();

            // 只处理与 fix-delete 同一个逻辑 SQL id 的事件
            if (sqlId < currentSqlId) {
                continue;
            }
            if (sqlId > currentSqlId) {
                // 逻辑 SQL id 超出范围，不会再找到匹配项（理论上不会进入此分支）
                break;
            }

            // 在同一逻辑 SQL 内，找到物理 SQL id 比 fix-delete 小的最后一个 event
            // 即找到"恰好比 delete 小的 trace 的最后位置"，作为情况①/③ 的插入点
            if (startIdx == -1 && item.getTraceId().compareTo(ref.getTraceId()) > 0) {
                // iterator.previousIndex() 返回最后调用 next() 返回的元素的下标
                startIdx = iterator.previousIndex();
            }

            if (eventType == LogEvent.TABLE_MAP_EVENT) {
                // 解析 TABLE_MAP_EVENT，将其列信息持续更新到 logContext，
                // 以便正确解析同逻辑 SQL 中多张表的 ROWS_LOG_EVENT
                try {
                    byte[] rawPayload = item.getRawPayload();
                    LogBuffer logBuffer = new LogBuffer(rawPayload, 0, rawPayload.length);
                    logDecoder.decode(logBuffer, logContext);
                    lastTableMapEventRef = item;
                } catch (Exception e) {
                    logger.info("fix returning order error with trace id:{} ", ref.getTraceId(), e);
                    throw new RuntimeException(e);
                }
            }

            // ---- 情况②：与 WRITE_ROWS_EVENT（INSERT）比较 ----
            if (eventType == LogEvent.WRITE_ROWS_EVENT || eventType == LogEvent.WRITE_ROWS_EVENT_V1) {
                RowsLogEventCompareCode compareCode =
                    RowsLogEventCompareUtil.compareEventAndRemoveEqualPart(ref, item, logDecoder, logContext);
                switch (compareCode) {
                case PARTLY_EQUAL:
                case TOTALLY_NOT_EQUAL:
                    // 部分相同或完全不同：INSERT 已被截短（PARTLY_EQUAL 情况），
                    // delete 剩余行继续向前匹配，不在此处处理
                    break;
                case TOTALLY_EQUAL:
                    // INSERT 与 delete 行完全相同，双方均消除
                    shouldMoveDelete = false;
                    deleteEventExist = false;
                    // fall through：INSERT event 同样需要从 refList 中移除
                case LEFT_CONTAINS:
                    // delete 包含 INSERT 的全部行，INSERT 消除，delete 截短后继续
                    try {
                        item.delete();
                    } catch (RocksDBException e) {
                        throw new RuntimeException(e);
                    }
                    iterator.remove();
                    /*
                    一律不删除 table map（避免影响其他 ROWS event 的解析）
                    try {
                        TxnItemRef tableMapItem = iterator.previous();
                        tableMapItem.delete();
                    } catch (RocksDBException e) {
                        throw new RuntimeException(e);
                    }
                    iterator.remove();
                     */
                    break;
                case RIGHT_CONTAINS:
                    // INSERT 完全包含 delete 的行，INSERT 被截短，delete 不再存在
                    shouldMoveDelete = false;
                    deleteEventExist = false;
                    break;
                }
                if (!deleteEventExist) {
                    break;
                }
            }

            // ---- 情况③：与 UPDATE_ROWS_EVENT 比较 ----
            if (eventType == LogEvent.UPDATE_ROWS_EVENT || eventType == LogEvent.UPDATE_ROWS_EVENT_V1) {
                // 比较 delete 与该 UPDATE 的 after 行，若有匹配则拆出 split-delete 插到 UPDATE 后
                Pair<TxnItemRef, TxnItemRef> pair =
                    RowsLogEventCompareUtil.splitDeleteEvent(ref, item, logDecoder, logContext);
                TxnItemRef equalPartDelete = pair.getValue();
                if (equalPartDelete != null) {
                    // UPDATE 不再是该 TABLE_MAP 对应的最后一个 DML event，
                    // 清除其 STMT_END_F 标志，否则下游解析器会误认为语句已结束
                    item.unsetEndFlags();
                    // 将 split-delete（与 UPDATE after 匹配的部分）紧跟在 UPDATE 后插入
                    iterator.add(equalPartDelete);
                    // 对 UPDATE 对应的 TABLE_MAP 打 returning 标记：
                    // 在跨 DN 归并排序时，该 TABLE_MAP 会被优先取出（compareTo 中 returning=true 排前），
                    // 保证 TABLE_MAP → UPDATE → split-delete 这一组事件整体先于其他 DN 的同 traceId 事件输出，
                    // 从而解决多 DN 间排序不确定导致的 UK 冲突
                    lastTableMapEventRef.setReturningEvent(true);
                }
                if (pair.getKey() == null) {
                    // delete 的所有行均已被 UPDATE 覆盖，不再需要继续向前移动
                    shouldMoveDelete = false;
                    break;
                }
                // pair.getKey() != null：delete 还有剩余行（diff 部分），继续处理
            }
        }

        if (shouldMoveDelete) {
            // 情况①：将 delete 及其 TABLE_MAP 提前到 startIdx 位置
            if (startIdx == -1) {
                // 在本逻辑 SQL 内没有找到比 delete 更小的 trace，放到 refList 最前面
                startIdx = 0;
            }
            entity.refList.add(startIdx, ref);
            if (!lastTableMaphasMoved) {
                // 将 fix-delete 对应的 TABLE_MAP（refList 末尾）也一并提前
                TxnItemRef lastItem = entity.refList.pollLast();
                entity.refList.add(startIdx, lastItem);
            }
        } else {
            try {
                // fix-delete 被完全消除（情况②/③ 全部匹配），不插入 refList，但需释放资源
                // 注意：即使 deleteEventExist=false，对应的 TABLE_MAP 也不删除，
                // 因为不确定后续是否还有其他 delete 会用到该 TABLE_MAP
                if (!deleteEventExist) {
                    ref.delete();
                }
            } catch (RocksDBException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private TxnItemRef makeRef(TxnBufferItem txnItem) {
        return new TxnItemRef(this, txnItem.getTraceId(), txnItem.getRowsQuery(),
            txnItem.getEventType(), txnItem.getPayload(), txnItem.getSchema(), txnItem.getTable(),
            txnItem.getHashKey(), txnItem.getPrimaryKey());
    }

    private TxnItemRef doAddBefore(TxnBufferItem txnItem) {
        entity.memSize += txnItem.size();
        entity.lastTraceId = txnItem.getTraceId();
        TxnItemRef ref = makeRef(txnItem);
        tryPersist(ref, txnItem.size());
        return ref;
    }

    /**
     * 1. 将另外一个TxnBuffer的TxnItem合并给当前的TxnBuffer
     *
     * <p>多 DN 场景下，同一个逻辑事务的 binlog 分布在多个 DN 的物理 binlog 文件中，
     * CDC extractor 会为每个 DN 创建独立的 TxnBuffer，最终需要将所有 TxnBuffer 归并为一个
     * 有序序列，供下游消费。本方法通过 {@link #mergeTwoSortList} 对两个 TxnBuffer 的
     * refList 做归并排序（按 TABLE_MAP_EVENT 的 traceId 为 key 排序）。</p>
     *
     * <p>replace returning 场景补充：当某个 fix-delete 与 UPDATE 行匹配时（情况③），
     * 对应的 TABLE_MAP_EVENT 会被打上 {@code returningEvent=true} 标记，
     * 归并排序时 {@link TxnItemRef#compareTo} 会让该 TABLE_MAP 优先排出，
     * 保证 TABLE_MAP → UPDATE → split-delete 这一组整体先于其他 DN 的同 traceId 事件输出
     * （详见 20250807 方案）。</p>
     */
    public void merge(TxnBuffer other) {
        if (!isCompleted()) {
            throw new PolardbxException("None completed txn buffer can't do merge.");
        }

        if (this.itemSize() == 0 || other.itemSize() == 0) {
            throw new PolardbxException("Buffer size should't be zero.");
        }

        if (entity.refList.getFirst().getEventType() != LogEvent.TABLE_MAP_EVENT) {
            throw new PolardbxException("The first event is not table_map_event, but is "
                + entity.refList.getFirst().getEventType() + ", and corresponding txn key is " + entity.txnKey);
        }

        if (other.entity.refList.getFirst().getEventType() != LogEvent.TABLE_MAP_EVENT) {
            throw new PolardbxException(
                "The first event is not table_map_event, but is " + other.entity.refList.getFirst().getEventType()
                    + ", and corresponding txn key is " + entity.txnKey);
        }
        this.entity.refList = mergeTwoSortList(entity.refList, other.entity.refList);
        this.entity.memSize += other.entity.memSize;
    }

    public void compressDuplicateTraceId() {
        if (!ClusterType.BINLOG_X.name().equals(clusterType)) {
            String lastTraceId = "";
            for (TxnItemRef ref : entity.refList) {
                if (ref.getEventType() == LogEvent.TABLE_MAP_EVENT) {
                    if (StringUtils.equals(lastTraceId, ref.getTraceId())) {
                        ref.clearRowsQuery();
                    } else {
                        lastTraceId = ref.getTraceId();
                    }
                }
            }
        }
    }

    public boolean isLargeTrans() {
        return entity.memSize >= Math.min(repository.getTxnItemPersistThreshold(), repository.getTxnPersistThreshold());
    }

    public void restore() {
        if (entity.hasPersistingData && !entity.restored) {
            byte[] beginKey = buildTxnItemRefKeyWithSubSequence(beginKeySubSequence);
            byte[] endKey = peekNextTxnItemRefKey().getRight();
            List<Pair<byte[], byte[]>> repoList = getRepoUnit().getRange(beginKey, endKey, entity.itemSizeBeforeMerge);
            if (repoList.size() != entity.itemSizeBeforeMerge) {
                throw new PolardbxException(
                    "list size from repository is not equal to sub sequence, [" + repoList.size() + ","
                        + entity.itemSizeBeforeMerge + "]");
            }

            int count = 0;
            for (TxnItemRef txnItemRef : entity.refList) {
                if (txnItemRef.getTxnBuffer() == this) {
                    try {
                        Pair<byte[], byte[]> pair = repoList.get(count);
                        txnItemRef.restore(pair.getKey(), pair.getValue());
                        count++;
                    } catch (Throwable t) {
                        printErrorForRestore(repoList, count);
                        throw t;
                    }
                }
            }

            if (count != repoList.size()) {
                throw new PolardbxException(
                    "txn item count in repository is not equal to which in memory, count in repository is " + repoList
                        .size() + ", count in memory is " + count);
            }

            entity.restored = true;
        }
    }

    private void printErrorForRestore(List<Pair<byte[], byte[]>> repoList, int count) {
        List<TxnItemRef> txnItemRefs = entity.refList.stream().filter(i -> i.getTxnBuffer() == TxnBuffer.this)
            .collect(Collectors.toList());
        List<String> repoKeyList = repoList.stream()
            .map(p -> new String(p.getKey())).collect(Collectors.toList());
        List<String> refKeyList = txnItemRefs.stream()
            .map(p -> new String(buildTxnItemRefKeyWithSubSequence(p.getSubKeySeq())))
            .collect(Collectors.toList());

        logger.error("meet fatal error when restore txn item, repository list size is {}, "
                + "ref list size is {}, ref list size for this txn buffer is {}, current count is {},.",
            repoList.size(), entity.refList.size(), txnItemRefs.size(), count);
        logger.error("key list for repository list is : " + JSONObject.toJSONString(repoKeyList, true));
        logger.error("key list for txn item ref list is :" + JSONObject.toJSONString(refKeyList, true));
    }

    /**
     * 包内访问，for test
     */
    boolean seek(TxnItemRef itemRef) {
        int index = Collections.binarySearch(entity.refList, itemRef);
        if (index < 0) {
            return false;
        }

        LinkedList<TxnItemRef> list = new LinkedList<>();
        for (int i = 0; i < index; i++) {
            list.add(entity.refList.get(i));
        }

        entity.refList = list;
        entity.lastTraceId = list.get(list.size() - 1).getTraceId();
        return true;
    }

    Pair<Integer, byte[]> buildNewTxnItemRefKey() {
        int subSequence = nextSubSequence();
        byte[] key = buildTxnItemRefKeyWithSubSequence(subSequence);
        return Pair.of(subSequence, key);
    }

    Pair<Integer, byte[]> peekNextTxnItemRefKey() {
        int subSequence = entity.subSequenceGenerator.get() + 1;
        byte[] key = buildTxnItemRefKeyWithSubSequence(subSequence);
        return Pair.of(subSequence, key);
    }

    byte[] buildTxnItemRefKeyWithSubSequence(int subSequence) {
        return ByteUtil.bytes(StringUtils.leftPad(txnBufferId.toString(), 19, "0") +
            StringUtils.leftPad(subSequence + "", 10, "0"));
    }

    /**
     * 对来自两个 DN 分片的 TxnItemRef 列表做归并排序，输出全局有序的合并列表。
     *
     * <h2>排序策略</h2>
     * <p>排序以 <b>TABLE_MAP_EVENT 的 traceId</b> 为 key（而非每个 event 自身的 traceId），
     * 理由如下：</p>
     * <ol>
     *   <li>当 PolarDB-X 开启 trace 功能时，traceId 全局有序，直接按 traceId 排序等价于
     *       按 TABLE_MAP 排序。</li>
     *   <li>当 trace 功能关闭时，虚拟 traceId 只能保证单分片有序，直接按 traceId 排序
     *       会打乱 TABLE_MAP 与其后续 ROWS_LOG_EVENT 的整体性（即连续性）。
     *       以 TABLE_MAP 为 key 则可保证每组 DML 作为整体被有序输出。</li>
     * </ol>
     *
     * <h2>算法描述</h2>
     * <p>标准归并排序，同时处理以下约束：</p>
     * <ul>
     *   <li>只在两个链表均指向 TABLE_MAP_EVENT 时才进行比较和交叉；否则将非 TABLE_MAP event
     *       视为属于上一个 TABLE_MAP 的 "跟随者"，直接追加到输出列表。</li>
     *   <li>通过 {@link TxnItemRef#compareTo} 决定哪侧 TABLE_MAP 优先输出；
     *       对于打了 {@code returningEvent=true} 标记的 TABLE_MAP，其 compareTo 会返回更小值，
     *       从而优先排出，保证 split-delete 能在跨 DN 归并时整体先行（20250807 方案）。</li>
     *   <li>同一 traceId 的重复 TABLE_MAP（来自同分片的 batch SQL）只保留第一次出现的
     *       rowsQuery，后续重复出现的调用 {@link TxnItemRef#clearRowsQuery()} 清空以节省带宽。</li>
     * </ul>
     *
     * @param aList 来自 DN-A 的已排序 refList
     * @param bList 来自 DN-B 的已排序 refList
     * @return 归并后的全局有序列表，大小等于 aList.size() + bList.size()
     */
    private LinkedList<TxnItemRef> mergeTwoSortList(LinkedList<TxnItemRef> aList, LinkedList<TxnItemRef> bList) {
        String lastTraceId = "";
        int aSize = aList.size();
        int bSize = bList.size();
        LinkedList<TxnItemRef> mergeList = new LinkedList<>();
        Iterator<TxnItemRef> ai = aList.iterator();
        Iterator<TxnItemRef> bi = bList.iterator();
        TxnItemRef aItem = null;
        TxnItemRef bItem = null;

        while ((aItem != null || ai.hasNext()) && (bItem != null || bi.hasNext())) {
            if (aItem == null) {
                aItem = ai.next();
            }
            if (bItem == null) {
                bItem = bi.next();
            }

            if (aItem.getEventType() == LogEvent.TABLE_MAP_EVENT
                && bItem.getEventType() == LogEvent.TABLE_MAP_EVENT) {
                // 两侧均为 TABLE_MAP：比较 traceId 决定哪侧优先输出
                if (aItem.compareTo(bItem) > 0) {
                    // b 侧 TABLE_MAP 优先
                    if (bItem.getTraceId().equals(lastTraceId)) {
                        // 同 traceId 的重复 TABLE_MAP，清空 rowsQuery 避免重复携带
                        tryClearRowsQuery(bItem);
                    } else {
                        lastTraceId = bItem.getTraceId();
                    }
                    mergeList.add(bItem);
                    bItem = null;
                } else {
                    // a 侧 TABLE_MAP 优先（含 traceId 相等的情况，此时 returning 标记决定胜负）
                    if (aItem.getTraceId().equals(lastTraceId)) {
                        tryClearRowsQuery(aItem);
                    } else {
                        lastTraceId = aItem.getTraceId();
                    }
                    mergeList.add(aItem);
                    aItem = null;
                }
            } else {
                // 至少有一侧不是 TABLE_MAP，说明是 TABLE_MAP 的跟随者（ROWS event）
                // 跟随者不参与跨链表比较，直接追加到输出列表
                if (aItem.getEventType() != LogEvent.TABLE_MAP_EVENT) {
                    mergeList.add(aItem);
                    aItem = null;
                } else if (bItem.getEventType() != LogEvent.TABLE_MAP_EVENT) {
                    mergeList.add(bItem);
                    bItem = null;
                } else {
                    throw new PolardbxException("invalid merge status");
                }
            }
        }

        // b 列表已全部排入，追加 a 的剩余元素
        if (aItem != null || ai.hasNext()) {
            if (aItem != null) {
                lastTraceId = processRowsQuery(aItem, lastTraceId);
                mergeList.add(aItem);
            }
            while (ai.hasNext()) {
                TxnItemRef ref = ai.next();
                lastTraceId = processRowsQuery(ref, lastTraceId);
                mergeList.add(ref);
            }
        }

        // a 列表已全部排入，追加 b 的剩余元素
        if (bItem != null || bi.hasNext()) {
            if (bItem != null) {
                lastTraceId = processRowsQuery(bItem, lastTraceId);
                mergeList.add(bItem);
            }
            while (bi.hasNext()) {
                TxnItemRef ref = bi.next();
                lastTraceId = processRowsQuery(ref, lastTraceId);
                mergeList.add(ref);
            }
        }

        if (mergeList.size() != (aSize + bSize)) {
            throw new PolardbxException(
                "merge list size is incorrect : " + mergeList.size() + ", input first list size is "
                    + aSize + ", input second list size is " + bSize);
        }
        return mergeList;
    }

    private void tryClearRowsQuery(TxnItemRef txnItemRef) {
        if (!ClusterType.BINLOG_X.name().equals(clusterType)) {
            txnItemRef.clearRowsQuery();
        }
    }

    private String processRowsQuery(TxnItemRef ref, String lastTraceId) {
        if (ref.getEventType() == LogEvent.TABLE_MAP_EVENT) {
            if (ref.getTraceId().equals(lastTraceId)) {
                tryClearRowsQuery(ref);
            } else {
                return ref.getTraceId();
            }
        }
        return lastTraceId;
    }

    private long nextSequence() {
        long sequence = sequenceGenerator.incrementAndGet();
        if (sequence == Long.MAX_VALUE) {
            throw new PolardbxException("sequence exceed max value.");
        }
        return sequence;
    }

    private int nextSubSequence() {
        int sequence = entity.subSequenceGenerator.incrementAndGet();
        if (sequence == Integer.MAX_VALUE) {
            throw new PolardbxException("sub sequence exceed max value.");
        }
        return sequence;
    }

    private void tryPersist(TxnItemRef ref, int payloadSize) {
        if (repository == null) {
            return;
        }

        if (!repository.isPersistOn()) {
            return;
        }

        if (repository.isForcePersist()) {
            if (!entity.shouldPersist) {
                persistPreviousItems();
                entity.shouldPersist = true;
                CURRENT_TXN_BUFFER_PERSISTED_COUNT.incrementAndGet();
            }
        } else if (!entity.shouldPersist) {
            if (payloadSize >= repository.getTxnItemPersistThreshold() && repository.isReachPersistThreshold(true)) {
                // 单个Event大小如果超过了指定阈值，立刻进行内存使用率的校验，如果超过阈值，则触发落盘
                entity.shouldPersist = true;
                logger.info("Txn Item size is greater than txnItemPersistThreshold,"
                        + " txnKey is {},txnItemSize is {},txnItemPersistThreshold is {}.",
                    entity.txnKey, entity.memSize, repository.getTxnItemPersistThreshold());

            } else if (entity.memSize >= repository.getTxnPersistThreshold() && repository
                .isReachPersistThreshold(true)) {
                // 单个事务大小如果超过了指定阈值，立刻进行内存使用率的校验，如果超过阈值，则触发落盘
                entity.shouldPersist = true;
                logger.info("Txn Buffer size is greater than txnPersistThreshold,"
                        + " txnKey is {},txnBuffSize is {},txnPersisThreshold is {}.",
                    entity.txnKey, entity.memSize, repository.getTxnPersistThreshold());

            } else {
                entity.shouldPersist = repository.isReachPersistThreshold(false);
                if (entity.shouldPersist) {
                    logger
                        .info("Persisting mode is open for txn buffer : " + entity.txnKey + ",caused by memory ratio.");
                }
            }

            if (entity.shouldPersist) {
                persistPreviousItems();
                CURRENT_TXN_BUFFER_PERSISTED_COUNT.incrementAndGet();
            }
        }

        if (entity.shouldPersist) {
            try {
                persistOneItem(ref);
            } catch (RocksDBException e) {
                throw new PolardbxException("txn item persist error.", e);
            }
        }
    }

    private void persistPreviousItems() {
        //将历史item也进行持久化
        entity.refList.forEach(r -> {
            try {
                persistOneItem(r);
            } catch (RocksDBException e) {
                throw new PolardbxException("txn item persist error.", e);
            }
        });

    }

    private void persistOneItem(TxnItemRef ref) throws RocksDBException {
        entity.hasPersistingData = true;
        ref.persist();
    }

    public TxnKey getTxnKey() {
        return entity.txnKey;
    }

    public Iterator<TxnItemRef> iterator() {
        return entity.refList.iterator();
    }

    public Iterator<TxnItemRef> parallelRestoreIterator() {
        if (entity.iterator == null) {
            boolean enableParallelRestore = DynamicApplicationConfig.getBoolean(STORAGE_PARALLEL_RESTORE_ENABLE);
            if (enableParallelRestore) {
                this.entity.iterator = new ParallelRestoreIterator(entity.refList);
            } else {
                this.entity.iterator = iterator();
            }
        }
        return entity.iterator;
    }

    public IteratorBuffer iteratorWrapper() {
        return new IteratorBuffer() {

            private final ListIterator<TxnItemRef> llIt = entity.refList.listIterator();
            private TxnItemRef curRef;

            @Override
            public boolean hasNext() {
                return llIt.hasNext();
            }

            @Override
            public TxnItemRef next() {
                curRef = llIt.next();
                return curRef;
            }

            @SneakyThrows
            @Override
            public void remove() {
                try {
                    curRef.delete();
                    llIt.remove();
                } catch (RocksDBException e) {
                    throw new PolardbxException("remove txn item ref failed!", e);
                }

            }

            @Override
            public void appendAfter(TxnBufferItem txnItem) {
                TxnItemRef ref = doAddBefore(txnItem);
                llIt.add(ref);
            }

        };
    }

    @SneakyThrows
    public void persistEntity() {
        if (entity != null && !entity.shouldPersist) {
            throw new PolardbxException("can`t serialize Entity for TxnBuffer which not persisted! " + entity.txnKey);
        }
        if (!entityPersisted) {
            entityPersistKey = entitySequenceGenerator.incrementAndGet();
            getRepoUnit().put(buildEntityKey(), entity.serialize());
            entity = null;
            entityPersisted = true;
        }
    }

    @SneakyThrows
    public void restoreEntity() {
        if (entityPersisted) {
            byte[] key = buildEntityKey();
            byte[] value = getRepoUnit().get(key);
            entity = TxnBufferEntity.deserialize(value);
            entity.refList.forEach(i -> i.setTxnBuffer(this));
            getRepoUnit().delete(key);
            entityPersisted = false;
        }
    }

    @SneakyThrows
    public void deleteEntity() {
        if (entityPersisted) {
            getRepoUnit().delete(buildEntityKey());
        }
    }

    private byte[] buildEntityKey() {
        return ByteUtil.bytes(entityKeyPrefix +
            StringUtils.leftPad(String.valueOf(entityPersistKey), 19, "0"));
    }

    public TxnItemRef getItemRef(int index) {
        return entity.refList.get(index);
    }

    public boolean isCompleted() {
        return entity.completed.get();
    }

    public int itemSize() {
        return entity.refList == null ? 0 : entity.refList.size();
    }

    public long memSize() {
        return entity.memSize;
    }

    public RepoUnit getRepoUnit() {
        return repository.selectUnit(txnBufferId);
    }

    private static class ParallelRestoreIterator implements Iterator<TxnItemRef> {
        private static final ThreadPoolExecutor EXECUTORS;

        static {
            int parallelism = DynamicApplicationConfig.getInt(STORAGE_PARALLEL_RESTORE_PARALLELISM);
            EXECUTORS = new ThreadPoolExecutor(parallelism, parallelism, 30L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder().setNameFormat("event-data-restore-thread-%d").build(),
                new ThreadPoolExecutor.CallerRunsPolicy());
            EXECUTORS.allowCoreThreadTimeOut(true);
        }

        private final List<TxnItemRef> txnItemRefList;
        private final Iterator<TxnItemRef> iterator;
        private final LinkedList<TxnItemRef> batchData;
        private final int batchSize;
        private final int maxEventSize;
        private int index;

        ParallelRestoreIterator(List<TxnItemRef> txnItemRefList) {
            this.txnItemRefList = txnItemRefList;
            this.iterator = txnItemRefList.iterator();
            this.batchData = new LinkedList<>();
            this.batchSize = DynamicApplicationConfig.getInt(STORAGE_PARALLEL_RESTORE_BATCH_SIZE);
            this.maxEventSize = DynamicApplicationConfig.getInt(STORAGE_PARALLEL_RESTORE_MAX_EVENT_SIZE);
            this.index = 0;
        }

        @Override
        public boolean hasNext() {
            return index < txnItemRefList.size();
        }

        @Override
        public TxnItemRef next() {
            if (batchData.isEmpty() && iterator.hasNext()) {
                while (iterator.hasNext()) {
                    batchData.add(iterator.next());
                    if (batchData.size() == batchSize) {
                        break;
                    }
                }

                List<Future<?>> futures = new LinkedList<>();
                for (TxnItemRef ref : batchData) {
                    if (!ref.isPersisted()) {
                        continue;
                    }
                    futures.add(EXECUTORS.submit(() -> {
                        try {
                            byte[] key = ref.getTxnBuffer().buildTxnItemRefKeyWithSubSequence(ref.getSubKeySeq());
                            byte[] value = ref.getTxnBuffer().getRepoUnit().get(key);
                            if (value.length < maxEventSize) {
                                ref.restore(key, value);
                            }
                        } catch (Throwable e) {
                            throw new PolardbxException("restore error for txn item ref", e);
                        }
                    }));
                }
                futures.forEach(f -> {
                    try {
                        f.get();
                    } catch (Throwable t) {
                        throw new PolardbxException("wait restore error", t);
                    }
                });
            }

            index++;
            return batchData.removeFirst();
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException("remove is unsupported");
        }

        @Override
        public void forEachRemaining(Consumer<? super TxnItemRef> action) {
            throw new UnsupportedOperationException("forEachRemaining is unsupported");
        }
    }

    //只有在key是相邻状态时，才能发挥Iterator的优势，否则性能反而会更慢，暂时放在这里
    private static class RestoreContext {
        private final TxnBuffer txnBuffer;
        private Iterator<TxnItemRef> refIterator;
        private RocksIterator rocksIterator;
        private int restoreCursor;

        RestoreContext(TxnBuffer txnBuffer) {
            this.txnBuffer = txnBuffer;
            this.restoreCursor = 1;
        }

        void next(TxnItemRef ref) {
            try {
                if (rocksIterator == null) {
                    byte[] beginKey = txnBuffer.buildTxnItemRefKeyWithSubSequence(beginKeySubSequence);
                    byte[] endKey = txnBuffer.peekNextTxnItemRefKey().getRight();
                    rocksIterator = txnBuffer.getRepoUnit().getIterator(beginKey, endKey);
                    rocksIterator.seek(beginKey);
                } else {
                    rocksIterator.next();
                }

                //get data from rocksdb
                if (!rocksIterator.isValid()) {
                    throw new PolardbxException("rocks iterator has no data for subKeySeq " + ref.getSubKeySeq());
                }
                Pair<byte[], byte[]> pair = Pair.of(rocksIterator.key(), rocksIterator.value());

                //do restore
                ref.restore(pair.getLeft(), pair.getRight());
            } catch (Throwable t) {
                throw new PolardbxException("next restore failed ", t);
            }
        }

        void tryRestoreNextBatch(int subKeySeq) {
            if (refIterator == null) {
                this.refIterator = txnBuffer.iterator();
            }

            if (subKeySeq < restoreCursor) {
                return;
            }
            if (subKeySeq > restoreCursor) {
                throw new PolardbxException("invalid restore status, input subKeySeq is " + subKeySeq +
                    " , restoreCursor is" + restoreCursor);
            }

            Pair<Integer, byte[]> pair = txnBuffer.peekNextTxnItemRefKey();
            if (pair.getLeft() == restoreCursor) {
                return;
            }

            byte[] beginKey = txnBuffer.buildTxnItemRefKeyWithSubSequence(restoreCursor);
            int end = restoreCursor + 200;
            end = Math.min(end, pair.getLeft());
            byte[] endKey = txnBuffer.buildTxnItemRefKeyWithSubSequence(end);
            int count = end - restoreCursor;
            List<Pair<byte[], byte[]>> repoList = txnBuffer.getRepoUnit().getRange(beginKey, endKey, count);
            repoList.forEach(p -> {
                TxnItemRef ref = null;
                while (refIterator.hasNext()) {
                    TxnItemRef temp = refIterator.next();
                    if (temp.getTxnBuffer() == txnBuffer) {
                        ref = temp;
                        break;
                    }
                }
                if (ref == null) {
                    throw new PolardbxException("can`t find txn item ref for key " + new String(p.getLeft()));
                }
                ref.restore(p.getLeft(), p.getRight());
            });

            restoreCursor = end;
        }

        void close() {
            if (rocksIterator != null) {
                rocksIterator.close();
            }
        }
    }
}
