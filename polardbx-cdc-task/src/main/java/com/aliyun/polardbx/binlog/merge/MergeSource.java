/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.merge;

import com.aliyun.polardbx.binlog.error.TimeoutException;
import com.aliyun.polardbx.binlog.extractor.Extractor;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnKey;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.LinkedList;
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
    private MergeItem currentMergeItem;
    private MergeType mergeType;
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

        if (mergeType == MergeType.BATCH && txnToken.getType() != TxnType.FORMAT_DESC) {
            if (currentMergeItem == null) {
                currentMergeItem = new MergeItem(sourceId, this);
            }
            currentMergeItem.addTxnToken(txnToken);
            if (txnToken.getType() == TxnType.META_HEARTBEAT) {
                this.queue.put(currentMergeItem);
                currentMergeItem = null;
            }
        } else {
            MergeItem mergeItem = new MergeItem(sourceId, txnToken, this);
            if (timeout == -1) {
                this.queue.put(mergeItem);
            } else {
                if (!this.queue.offer(mergeItem, timeout, TimeUnit.MILLISECONDS)) {
                    throw new TimeoutException("waiting up to the space failed");
                }
            }
        }

    }

    public MergeItem poll() throws InterruptedException {
        MergeItem item = null;
        item = this.queue.poll(1, TimeUnit.MILLISECONDS);
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

    public Iterator<TxnToken> iterator() {
        LinkedList<TxnToken> list = new LinkedList<>();
        for (MergeItem mergeItem : queue) {
            list.addAll(mergeItem.getAllTxnTokens());
        }
        return list.iterator();
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

    public void setMergeType(MergeType mergeType) {
        this.mergeType = mergeType;
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
}
