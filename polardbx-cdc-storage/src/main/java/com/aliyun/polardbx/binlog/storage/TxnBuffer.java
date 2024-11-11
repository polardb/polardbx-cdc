/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
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
    private static Repository repository;

    private long entityPersistKey;
    private boolean entityPersisted;
    private TxnBufferEntity entity;
    private Long txnBufferId;

    public TxnBuffer() {
    }

    TxnBuffer(TxnKey txnKey, Repository repository) {
        TxnBuffer.repository = repository;
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

        //traceId是允许重复的，但不能回跳，所以此处只对乱序的情况进行校验
        if (StringUtils.isNotBlank(entity.lastTraceId) && txnItem.getTraceId().compareTo(entity.lastTraceId) < 0) {
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

    private void doAdd(TxnBufferItem txnItem) {
        //add to list && try persist
        TxnItemRef ref = new TxnItemRef(this, txnItem.getTraceId(), txnItem.getRowsQuery(),
            txnItem.getEventType(), txnItem.getPayload(), txnItem.getSchema(), txnItem.getTable(),
            txnItem.getHashKey(), txnItem.getPrimaryKey());

        entity.memSize += txnItem.size();
        entity.lastTraceId = txnItem.getTraceId();
        tryPersist(ref, txnItem.size());
        entity.refList.add(ref);

        if (logger.isDebugEnabled()) {
            logger.debug("accept an item for txn buffer " + entity.txnKey);
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
     * 1. 当polarx开启了trace功能时，extractor会直接使用其提供的traceId，此时traceId是全局有序的 </br>
     * 2. 当polarx关闭了trace功能时，extractor会生成虚拟的traceId，此时traceId只能保证单分片有序 </br>
     * 3. 在只能保证单分片有序的情况下，直接按traceId进行排序会打乱Table_map和Write_rows事件的整体性(或称为连续性) </br>
     * 4. 所以，不管是否全局有序，都按Table_Map_Event进行merge sort </br>
     * 5. traceId全局有序场景下，按此排序算法输出的【所有item】仍然是全局有序的 </br>
     * 6. traceId单分片有序场景下，按此算法输出的【所有TABLE_MAP_EVENT】是全局有序的
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
                if (aItem.compareTo(bItem) > 0) {
                    if (bItem.getTraceId().equals(lastTraceId)) {
                        tryClearRowsQuery(bItem);
                    } else {
                        lastTraceId = bItem.getTraceId();
                    }
                    mergeList.add(bItem);
                    bItem = null;
                } else {
                    if (aItem.getTraceId().equals(lastTraceId)) {
                        tryClearRowsQuery(aItem);
                    } else {
                        lastTraceId = aItem.getTraceId();
                    }
                    mergeList.add(aItem);
                    aItem = null;
                }
            } else {
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

        // blist元素已排好序， alist还有剩余元素
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

        // alist元素已排好序， blist还有剩余元素
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

            private ListIterator<TxnItemRef> llIt = entity.refList.listIterator();
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
