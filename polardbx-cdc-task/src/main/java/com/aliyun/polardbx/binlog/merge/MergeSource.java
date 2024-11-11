/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.error.TimeoutException;
import com.aliyun.polardbx.binlog.extractor.Extractor;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Created by ziyang.lb
 **/
public class MergeSource {

    private static final Logger logger = LoggerFactory.getLogger(MergeSource.class);

    private final String sourceId;
    private final ArrayBlockingQueue<MergeItem> queue;
    private final Storage storage;

    private String startTSO;
    private Extractor extractor;
    private long passCount;
    private long pollCount;
    private volatile boolean running;

    public MergeSource(String sourceId, Storage storage) {
        this(sourceId, new ArrayBlockingQueue<>(1024), storage);
    }

    public MergeSource(String sourceId, ArrayBlockingQueue<MergeItem> queue, Storage storage) {
        this.sourceId = sourceId;
        this.passCount = 0L;
        this.queue = queue;
        this.storage = storage;
    }

    public void start() {
        if (running) {
            return;
        }
        this.extractor.start(startTSO);
        this.running = true;
        logger.info("Merge source {} started", sourceId);
    }

    public void stop() {
        if (!running) {
            return;
        }
        this.extractor.stop();
        this.running = false;
        logger.info("Merge source {} Stopped.", sourceId);
    }

    public void push(TxnToken txnToken) throws InterruptedException {
        push(txnToken, true, -1);
    }

    public void push(TxnToken txnToken, boolean hasBufferData) throws InterruptedException {
        push(txnToken, hasBufferData, -1);
    }

    /**
     * 1. TxnToken被成功接收后，对应的TxnBuffer会被标识为Complete状态 </br>
     * 2. 处于Complete状态的Buffer不能再有数据写入，也不能被revert，下游才可以安全使用 </br>
     * 3. Origin MergeSourceId是Final阶段进行事务合并时需要强依赖的属性，此处如果发现该字段为空，则说明此merge
     * source为origin source
     */
    public void push(TxnToken txnToken, boolean hasBufferData, long timeout) throws InterruptedException {
        if (StringUtils.isBlank(txnToken.getOriginMergeSourceId())) {
            txnToken = txnToken.toBuilder().setOriginMergeSourceId(this.sourceId).build();
        }

        if (hasBufferData) {
            TxnBuffer buffer = storage.fetch(new TxnKey(txnToken.getTxnId(), txnToken.getPartitionId()));
            buffer.markComplete();
        }

        MergeItem mergeItem = new MergeItem(sourceId, txnToken);
        if (timeout == -1) {
            this.queue.put(mergeItem);
        } else {
            if (!this.queue.offer(mergeItem, timeout, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("waiting up to the space failed");
            }
        }
    }

    public MergeItem poll() throws InterruptedException {
        MergeItem item = this.queue.poll(1, TimeUnit.MILLISECONDS);
        if (item != null) {
            passCount++;
        }
        pollCount++;
        return item;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MergeSource that = (MergeSource) o;
        return sourceId.equals(that.sourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceId);
    }

    public int tryPersist() {
        return PersistUtil.persist(queue, storage);
    }

    public void setStartTSO(String startTSO) {
        this.startTSO = startTSO;
    }

    public Extractor getExtractor() {
        return extractor;
    }

    public void setExtractor(Extractor extractor) {
        this.extractor = extractor;
    }

    public String getSourceId() {
        return sourceId;
    }

    public long getPassCount() {
        return passCount;
    }

    public long getQueuedSize() {
        return queue.size();
    }

    public long getPollCount() {
        return pollCount;
    }

    public Storage getStorage() {
        return storage;
    }
}
