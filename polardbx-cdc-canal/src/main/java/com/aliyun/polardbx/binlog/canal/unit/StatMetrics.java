/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.unit;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
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
    private AtomicLong outMessageCount = new AtomicLong();
    private AtomicLong applyCount = new AtomicLong();
    private AtomicLong inMessageCount = new AtomicLong();
    private AtomicLong outBytesCount = new AtomicLong();
    private AtomicLong inBytesCount = new AtomicLong();
    private AtomicLong insertMessageCount = new AtomicLong();
    private AtomicLong updateMessageCount = new AtomicLong();
    private AtomicLong deleteMessageCount = new AtomicLong();
    private AtomicLong receiveDelay = new AtomicLong();
    private AtomicLong processDelay = new AtomicLong();
    private AtomicLong mergeBatchSize = new AtomicLong();
    private AtomicLong rt = new AtomicLong();
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
        deleteMessageCount.getAndAdd(deleteCount);
        updateMessageCount.getAndAdd(updateCount);
        insertMessageCount.getAndAdd(insertCount);
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
        outMessageCount.getAndAdd(count);
    }

    public void addMergeBatchSize(long count) {
        mergeBatchSize.getAndAdd(count);
    }

    public void addRt(long count) {
        rt.getAndAdd(count);
    }

    public void addApplyCount(long count) {
        applyCount.getAndAdd(count);
    }

    public void addSkipCount(long count) {
        skipCounter.addAndGet(count);
    }

    public void addSkipExceptionCount(long count) {
        skipExceptionCounter.addAndGet(count);
    }

    public void addInMessageCount(long count) {
        inMessageCount.getAndAdd(count);
    }

    public void addInBytes(long count) {
        inBytesCount.getAndAdd(count);
    }

    public void addOutBytes(long count) {
        outBytesCount.getAndAdd(count);
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

    public void addCommitCount(List<DBMSEvent> events) {
        long eventSize = 0;
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV)){
            eventSize = events.stream().filter(event -> (!(event instanceof DefaultQueryLog) ||
                (((DefaultQueryLog) event).getQuery().contains("# POLARX_TSO=")))).count();
        }else{
            eventSize = events.size();
        }
        addCommitCount(eventSize);
    }

    public void addCommitCount(long addCount) {
        periodCommitCount.addAndGet(addCount);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE).toString();
    }
}
