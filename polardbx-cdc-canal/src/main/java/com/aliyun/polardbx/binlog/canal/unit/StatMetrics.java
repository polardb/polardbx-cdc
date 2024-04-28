/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.canal.unit;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.proc.ProcSnapshot;
import com.aliyun.polardbx.binlog.proc.ProcUtils;
import lombok.Data;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class StatMetrics {

    private static final Logger logger = LoggerFactory.getLogger(StatMetrics.class);
    private static final StatMetrics INSTANCE = new StatMetrics();
    private StatisticCounter outMessageCount = new StatisticCounter();
    private StatisticCounter applyCount = new StatisticCounter();
    private StatisticCounter inMessageCount = new StatisticCounter();
    private StatisticCounter outBytesCount = new StatisticCounter();
    private StatisticCounter inBytesCount = new StatisticCounter();
    private StatisticCounter insertMessageCount = new StatisticCounter();
    private StatisticCounter updateMessageCount = new StatisticCounter();
    private StatisticCounter deleteMessageCount = new StatisticCounter();
    private AtomicLong receiveDelay = new AtomicLong();
    private AtomicLong processDelay = new AtomicLong();
    private StatisticCounter mergeBatchSize = new StatisticCounter();
    private StatisticCounter rt = new StatisticCounter();
    private AtomicLong skipCounter = new AtomicLong();
    private AtomicLong skipExceptionCounter = new AtomicLong();
    private AtomicLong persistentMessageCounter = new AtomicLong();
    private AtomicLong totalInCache = new AtomicLong();
    private AtomicLong periodCommitCount = new AtomicLong();

    public static StatMetrics getInstance() {
        return INSTANCE;
    }

    public void doStatOut(List<DBMSEvent> events) {
        DBMSEvent lastEvent = null;
        long deleteCount = 0;
        long updateCount = 0;
        long insertCount = 0;
        long outBytes = 0;
        for (DBMSEvent event : events) {
            switch (event.getAction()) {
            case DELETE:
                deleteCount += ((DefaultRowChange) event).getRowSize();
                break;
            case UPDATE:
                updateCount += ((DefaultRowChange) event).getRowSize();
                break;
            case INSERT:
                insertCount += ((DefaultRowChange) event).getRowSize();
                break;
            default:
                break;
            }
            outBytes += event.getEventSize();
            lastEvent = event;
        }

        doStatOut(insertCount, updateCount, deleteCount, outBytes, lastEvent);

    }

    public void doStatOut(long insertCount, long updateCount, long deleteCount, long byteSize, DBMSEvent lastEvent) {
        addOutBytes(byteSize);
        deleteMessageCount.add(deleteCount);
        updateMessageCount.add(updateCount);
        insertMessageCount.add(insertCount);
        addOutMessageCount(deleteCount + updateCount + insertCount);
        if (lastEvent != null) {
            doStatOutDelay(lastEvent);
        }
    }

    private void doStatOutDelay(DBMSEvent event) {
        long now = System.currentTimeMillis();
        long extractTimestamp = event.getExtractTimeStamp();
        setProcessDelay(now - extractTimestamp);
    }

    public void setTotalInCache(long totalInCache) {
        this.totalInCache.set(totalInCache);
    }

    public void addOutMessageCount(long count) {
        outMessageCount.add(count);
    }

    public void addMergeBatchSize(long count) {
        mergeBatchSize.add(count);
    }

    public void addRt(long count) {
        rt.add(count);
    }

    public void addApplyCount(long count) {
        applyCount.add(count);
    }

    public void addSkipCount(long count) {
        skipCounter.addAndGet(count);
    }

    public void addSkipExceptionCount(long count) {
        skipExceptionCounter.addAndGet(count);
    }

    public void addInMessageCount(long count) {
        inMessageCount.add(count);
    }

    public void addInBytes(long count) {
        inBytesCount.add(count);
    }

    public void addOutBytes(long count) {
        outBytesCount.add(count);
    }

    public void setReceiveDelay(long delay) {
        receiveDelay.set(delay);
    }

    public void setProcessDelay(long delay) {
        processDelay.set(delay);
    }

    public void addPersistEventCount(long addNum) {
        persistentMessageCounter.addAndGet(addNum);
    }

    public void deletePersistEventCount(long delNum) {
        persistentMessageCounter.addAndGet(-delNum);
    }

    public long getPeriodCommitCount() {
        return periodCommitCount.getAndSet(0);
    }

    public void addCommitCount(long addCount) {
        periodCommitCount.addAndGet(addCount);
    }

    public double getCpuRatio() {
        ProcSnapshot snapshot = ProcUtils.buildProcSnapshot();
        if (snapshot != null) {
            return snapshot.getCpuPercent();
        } else {
            return -1L;
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE).toString();
    }
}
