/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.lang3.StringUtils;
import org.rocksdb.RocksDBException;
import org.rocksdb.util.ByteUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRACEID_DISORDER_IGNORE;

/**
 * Created by ziyang.lb
 **/
public class TxnBuffer {

    private static final Logger logger = LoggerFactory.getLogger(TxnBuffer.class);
    private static final Logger traceIdLogger = LoggerFactory.getLogger("traceIdDisorderLogger");
    private static final AtomicLong sequenceGenerator = new AtomicLong(0L);

    private final TxnKey txnKey;
    private final AtomicBoolean started;
    private final AtomicBoolean completed;
    private final Repository repository;
    private final AtomicBoolean shouldPersist;
    private final AtomicBoolean hasPersistingData;
    private final Long txnBufferId;
    private final AtomicLong persistedItemCount;

    private List<TxnItemRef> refList;
    private String lastTraceId;
    private long lastPersistCheckTime;
    private long lastPersistCheckMemSize;
    private long memSize;
    private byte[] beginKey;

    TxnBuffer(TxnKey txnKey, Repository repository) {
        this.txnKey = txnKey;
        this.refList = new ArrayList<>();
        this.started = new AtomicBoolean(false);
        this.completed = new AtomicBoolean(false);
        this.repository = repository;
        this.shouldPersist = new AtomicBoolean(false);
        this.hasPersistingData = new AtomicBoolean(false);
        this.txnBufferId = nextSequence();
        this.persistedItemCount = new AtomicLong(0L);
        StorageMemoryLeakDectectorManager.getInstance().watch(this);
    }

    /**
     * 将TxnBuffer标记为启动状态，处于启动状态的buffer才可以append数据
     */
    public boolean markStart() {
        return started.compareAndSet(false, true);
    }

    /**
     * 将TxnBuffer标记为完成状态, 处于完成状态之后，不能再append数据
     */
    public void markComplete() {
        if (!completed.compareAndSet(false, true)) {
            throw new PolardbxException("txn buffer has already completed, can't mark complete again.");
        }
    }

    /**
     * 关闭TxnBuffer，如果有持续化数据，删除数据
     */
    void close() {
        StorageMemoryLeakDectectorManager.getInstance().unwatch(this);
        if (hasPersistingData.get()) {
            if (repository.getDeleteMode() == DeleteMode.RANGE) {
                try {
                    repository.delRange(beginKey, buildTxnItemRefKey());
                } catch (RocksDBException e) {
                    throw new PolardbxException("delete rang failed", e);
                }
            } else if (repository.getDeleteMode() == DeleteMode.SINGLE) {
                refList.forEach(r -> {
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
        if (!started.get()) {
            throw new PolardbxException("can't push item to not started txn buffer.");
        }

        if (isCompleted()) {
            throw new PolardbxException("can't push item to completed txn buffer.");
        }

        //traceId是允许重复的，但不能回跳，所以此处只对乱序的情况进行校验
        if (StringUtils.isNotBlank(lastTraceId) && txnItem.getTraceId().compareTo(lastTraceId) < 0) {
            boolean ignoreDisorderedTraceId = DynamicApplicationConfig.getBoolean(TASK_TRACEID_DISORDER_IGNORE);
            if (!ignoreDisorderedTraceId) {
                throw new PolardbxException("detected disorderly traceId，current traceId is " + txnItem.getTraceId()
                    + ",last traceId is " + lastTraceId);
            } else {
                traceIdLogger.warn("detected disorderly traceId，current traceId is " + txnItem.getTraceId()
                    + " , last traceId is " + lastTraceId + " , origin traceId is " + txnItem.getOriginTraceId()
                    + " , binlog file name is " + txnItem.getBinlogFile() + " , binlog position is " + txnItem
                    .getBinlogPosition());
            }
        }

        doAdd(txnItem);
    }

    private void doAdd(TxnBufferItem txnItem) {
        //add to list && try persist
        TxnItemRef ref = new TxnItemRef(this, txnItem.getTraceId(), txnItem.getRowsQuery(),
            txnItem.getEventType(), txnItem.getPayload(), txnItem.getByteStringPayload(), txnItem.getSchema(),
            txnItem.getTable(), repository);

        refList.add(ref);
        memSize += txnItem.size();
        tryPersist(ref);
        lastTraceId = txnItem.getTraceId();

        if (logger.isDebugEnabled()) {
            logger.debug("accept an item for txn buffer " + txnKey);
        }
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

        if (refList.get(0).getEventType() != LogEvent.TABLE_MAP_EVENT) {
            throw new PolardbxException("The first event is not table_map_event, but is "
                + refList.get(0).getEventType() + ", and corresponding txn key is " + txnKey);
        }

        if (other.refList.get(0).getEventType() != LogEvent.TABLE_MAP_EVENT) {
            throw new PolardbxException(
                "The first event is not table_map_event, but is " + other.refList.get(0).getEventType()
                    + ", and corresponding txn key is " + txnKey);
        }

        this.refList = mergeTwoSortList(refList, other.refList);
        this.memSize += other.memSize;
    }

    /**
     * 包内访问，for test
     */
    boolean seek(TxnItemRef itemRef) {
        int index = Collections.binarySearch(refList, itemRef);
        if (index < 0) {
            return false;
        }

        ArrayList<TxnItemRef> list = new ArrayList<>(index + 1);
        for (int i = 0; i < index; i++) {
            list.add(refList.get(i));
        }

        refList = list;
        lastTraceId = list.get(list.size() - 1).getTraceId();
        return true;
    }

    byte[] buildTxnItemRefKey() {
        byte[] key = ByteUtil.bytes((txnKey.getTxnId() +
            StringUtils.leftPad(txnBufferId.toString(), 19, "0") +
            StringUtils.leftPad(nextSequence() + "", 19, "0")));

        if (beginKey == null) {
            beginKey = key;
        }
        return key;
    }

    /**
     * 1. 当polarx开启了trace功能时，extractor会直接使用其提供的traceId，此时traceId是全局有序的 </br>
     * 2. 当polarx关闭了trace功能时，extractor会生成虚拟的traceId，此时traceId只能保证单分片有序 </br>
     * 3. 在只能保证单分片有序的情况下，直接按traceId进行排序会打乱Table_map和Write_rows事件的整体性(或称为连续性) </br>
     * 4. 所以，不管是否全局有序，都按Table_Map_Event进行merge sort </br>
     * 5. traceId全局有序场景下，按此排序算法输出的【所有item】仍然是全局有序的 </br>
     * 6. traceId单分片有序场景下，按此算法输出的【所有TABLE_MAP_EVENT】是全局有序的
     */
    private List<TxnItemRef> mergeTwoSortList(List<TxnItemRef> aList, List<TxnItemRef> bList) {
        int aLength = aList.size(), bLength = bList.size();
        List<TxnItemRef> mergeList = new ArrayList<>();
        String lastTraceId = "";
        int i = 0, j = 0;
        while (aLength > i && bLength > j) {
            if (aList.get(i).getEventType() == LogEvent.TABLE_MAP_EVENT
                && bList.get(j).getEventType() == LogEvent.TABLE_MAP_EVENT) {
                if (aList.get(i).compareTo(bList.get(j)) > 0) {
                    if (bList.get(j).getTraceId().equals(lastTraceId)) {
                        bList.get(j).setRowsQuery("");
                    } else {
                        lastTraceId = bList.get(j).getTraceId();
                    }
                    mergeList.add(i + j, bList.get(j));
                    j++;
                } else {
                    if (aList.get(i).getTraceId().equals(lastTraceId)) {
                        aList.get(i).setRowsQuery("");
                    } else {
                        lastTraceId = aList.get(i).getTraceId();
                    }
                    mergeList.add(i + j, aList.get(i));
                    i++;
                }
            } else {
                if (aList.get(i).getEventType() != LogEvent.TABLE_MAP_EVENT) {
                    mergeList.add(i + j, aList.get(i));
                    i++;
                } else if (bList.get(j).getEventType() != LogEvent.TABLE_MAP_EVENT) {
                    mergeList.add(i + j, bList.get(j));
                    j++;
                } else {
                    throw new PolardbxException("invalid merge status");
                }
            }
        }
        // blist元素已排好序， alist还有剩余元素
        while (aLength > i) {
            mergeList.add(i + j, aList.get(i));
            i++;
        }
        // alist元素已排好序， blist还有剩余元素
        while (bLength > j) {
            mergeList.add(i + j, bList.get(j));
            j++;
        }

        assert mergeList.size() == aList.size() + bList.size();
        return mergeList;
    }

    private long nextSequence() {
        long sequence = sequenceGenerator.incrementAndGet();
        if (sequence == Long.MAX_VALUE) {
            throw new PolardbxException("sequence exceed max value.");
        }
        return sequence;
    }

    private void tryPersist(TxnItemRef ref) {
        if (!repository.isPersistOn()) {
            return;
        }

        if (repository.isForcePersist()) {
            shouldPersist.set(true);
        } else {
            boolean origin = shouldPersist.get();
            String switchToDiskReason = "";

            if (ref.getPayloadSize() >= repository.getTxnItemPersistThreshold()) {
                // 单个Event大小如果超过了指定阈值，则强制走KV存储
                shouldPersist.set(true);
                switchToDiskReason = "单个Event大小超过了指定阈值";
                if (!origin && shouldPersist.get()) {
                    logger.info("Txn Item size is greater than txnItemPersisThreshold,"
                            + " txnKey is {},txnItemSize is {},txnItemPersisThreshold is {}.",
                        txnKey, memSize, repository.getTxnItemPersistThreshold());
                }
            } else if (memSize >= repository.getTxnPersistThreshold()) {
                // 单个事务大小如果超过了指定阈值，则强制走KV存储
                shouldPersist.set(true);
                switchToDiskReason = "单个Transaction大小超过了指定阈值";
                if (!origin && shouldPersist.get()) {
                    logger.info("Txn Buffer size is greater than txnPersisThreshold,"
                            + " txnKey is {},txnBuffSize is {},txnPersisThreshold is {}.",
                        txnKey, memSize, repository.getTxnPersistThreshold());
                }
            } else if (System.currentTimeMillis() - lastPersistCheckTime >= 20
                || (memSize - lastPersistCheckMemSize) > 5 * 1024 * 1024) {
                // 性能考虑，每20ms检测一次,如果没达到20ms，内存增长量超过了2M，则也强制检测一次
                shouldPersist.set(repository.isReachPersistThreshold());
                switchToDiskReason = shouldPersist.get() ? "内存使用率超过了阈值" : "";
                lastPersistCheckTime = System.currentTimeMillis();
                lastPersistCheckMemSize = memSize;
            }

            if (!origin && shouldPersist.get()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Persisting mode is open for txn buffer : " + txnKey);
                }
            }

            if (origin && !shouldPersist.get()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Persisting mode is shutdown for txn buffer : " + txnKey);
                }
            }
        }

        if (shouldPersist.get()) {
            try {
                hasPersistingData.set(true);
                ref.persist();
                persistedItemCount.incrementAndGet();
            } catch (RocksDBException e) {
                throw new PolardbxException("txn item persist error.");
            }
        }
    }

    public TxnKey getTxnKey() {
        return txnKey;
    }

    public Iterator<TxnItemRef> iterator() {
        return refList.iterator();
    }

    public TxnItemRef getItemRef(int index) {
        return refList.get(index);
    }

    public boolean isCompleted() {
        return completed.get();
    }

    public int itemSize() {
        return refList == null ? 0 : refList.size();
    }

    public long memSize() {
        return memSize;
    }
}
