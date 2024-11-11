/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.metrics;

import com.aliyun.polardbx.binlog.backup.MetricsObserver;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.DELETE_ROWS_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.DELETE_ROWS_EVENT_V1;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.TABLE_MAP_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.UPDATE_ROWS_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.UPDATE_ROWS_EVENT_V1;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT_V1;

/**
 * Created by ziyang.lb
 **/
public class StreamMetrics implements MetricsObserver {
    private final String streamId;
    /**
     * 从dumper启动开始计算，截止到当前，已经收到的Event的总个数(ddl & dml)
     */
    private long totalRevEventCount;
    /**
     * 从dumper启动开始计算，截止到当前，已经收到的Event的字节数(ddl & dml)
     */
    private long totalRevEventBytes;
    /**
     * 从dumper启动开始计算，截止到当前，已经收到的dml event的总个数
     */
    private long totalWriteDmlEventCount;
    /**
     * 从dumper启动开启计算，截止到当前，已经收到的dml TabMap event的总个数，对应TABLE_MAP_EVENT
     */
    private long totalWriteDmlTabMapEventCount;
    /**
     * 从dumper启动开启计算，截止到当前，已经收到的dml insert event的总个数，对应WRITE_ROWS_EVENT
     */
    private long totalWriteDmlInsertEventCount;
    /**
     * 从dumper启动开启计算，截止到当前，已经收到的dml update event的总个数，对应WRITE_ROWS_EVENT
     */
    private long totalWriteDmlUpdateEventCount;
    /**
     * 从dumper启动开启计算，截止到当前，已经收到的dml delete event的总个数，对应WRITE_ROWS_EVENT
     */
    private long totalWriteDmlDeleteEventCount;
    /**
     * 从dumper启动开始计算，截止到当前，已经收到的ddl event的总个数
     */
    private long totalWriteDdlEventCount;
    /**
     * 从dumper启动开始计算，截止到当前，已经向binlog文件写入的事务的总个数(不包含ddl)
     */
    private long totalWriteTxnCount;
    /**
     * 从dumper启动开始计算，截止到当前，完成totalWriteTxnCount个事务写入的总耗时
     */
    private long totalWriteTxnTime;
    /**
     * 从dumper启动开始计算，截止到当前，已经向binlog文件写入的event事件的总个数
     * 和totalRevEventCount的主要差异是，此指标包含begin和commit事件
     */
    private long totalWriteEventCount;
    /**
     * 从dumper启动开始计算，截止到当前，已经向binlog文件写入的总字节数
     */
    private long totalWriteEventBytes;
    /**
     * 从dumper启动开始计算，BinlogFile执行flush write的总次数
     */
    private long totalWriteFlushCount;
    /**
     * 从dumper启动开始计算，binlogUploader上传的总字节数
     */
    private AtomicLong totalUploadBytes = new AtomicLong(0);
    /**
     * 从dumper启动开始计算，binlogDump发送的总字节数
     */
    private AtomicLong totalDumpBytes = new AtomicLong(0);
    /**
     * 当前最新的延迟时间(on commit)
     */
    private long latestDelayTimeOnCommit;
    /**
     * 最近一次收到数据的时间(单位：ms)
     */
    private long latestDataReceiveTime = System.currentTimeMillis();
    /**
     * 并行写入RingBuffer队列的大小
     */
    private long writeQueueSize;
    /**
     * TxnMessage接收队列的大小
     */
    private long receiveQueueSize;
    private long latestTsoTime;
    private String latestBinlogFile;
    private Supplier<Map<String, Integer>> kwaySourceQueueSizeSupplier = HashMap::new;

    private long beginTime;
    private static final LoadingCache<String, StreamMetrics> METRICS_MAP = CacheBuilder.newBuilder().build(
        new CacheLoader<String, StreamMetrics>() {
            @Override
            public StreamMetrics load(@NotNull String streamId) {
                return new StreamMetrics(streamId);
            }
        });

    public StreamMetrics(String streamId) {
        this.streamId = streamId;
    }

    public static Map<String, StreamMetrics> getMetricsMap() {
        return METRICS_MAP.asMap();
    }

    public static StreamMetrics getStreamMetrics(String streamId) {
        return METRICS_MAP.getUnchecked(streamId);
    }

    public StreamMetrics snapshot() {
        StreamMetrics result = new StreamMetrics(this.streamId);
        result.totalWriteDdlEventCount = this.totalWriteDdlEventCount;
        result.totalWriteDmlEventCount = this.totalWriteDmlEventCount;
        result.totalWriteDmlTabMapEventCount = this.totalWriteDmlTabMapEventCount;
        result.totalWriteDmlInsertEventCount = this.totalWriteDmlInsertEventCount;
        result.totalWriteDmlUpdateEventCount = this.totalWriteDmlUpdateEventCount;
        result.totalWriteDmlDeleteEventCount = this.totalWriteDmlDeleteEventCount;
        result.totalRevEventCount = this.totalRevEventCount;
        result.totalRevEventBytes = this.totalRevEventBytes;
        result.totalWriteEventBytes = this.totalWriteEventBytes;
        result.totalWriteEventCount = this.totalWriteEventCount;
        result.totalWriteTxnCount = this.totalWriteTxnCount;
        result.totalWriteTxnTime = this.totalWriteTxnTime;
        result.totalWriteFlushCount = this.totalWriteFlushCount;
        result.latestDelayTimeOnCommit = Math.max(
            this.latestDelayTimeOnCommit, (System.currentTimeMillis() - this.latestDataReceiveTime));
        result.latestDataReceiveTime = this.latestDataReceiveTime;
        result.writeQueueSize = this.writeQueueSize;
        result.receiveQueueSize = this.receiveQueueSize;
        result.totalUploadBytes = new AtomicLong(this.totalUploadBytes.get());
        result.totalDumpBytes = new AtomicLong(this.totalDumpBytes.get());
        result.latestBinlogFile = this.latestBinlogFile;
        result.latestTsoTime = this.latestTsoTime;
        result.kwaySourceQueueSizeSupplier = this.kwaySourceQueueSizeSupplier;
        return result;
    }

    // ---------------------------------setters---------------------------------

    public void markBegin() {
        beginTime = System.currentTimeMillis();
    }

    public void markEnd() {
        long endTime = System.currentTimeMillis();
        totalWriteTxnTime += (endTime - beginTime);
    }

    public void incrementTotalWriteTxnCount() {
        totalWriteTxnCount++;
    }

    public void incrementTotalWriteDmlEventCount(int eventType) {
        totalWriteDmlEventCount++;
        totalRevEventCount++;

        if (eventType == TABLE_MAP_EVENT) {
            totalWriteDmlTabMapEventCount++;
        } else if (eventType == WRITE_ROWS_EVENT || eventType == WRITE_ROWS_EVENT_V1) {
            totalWriteDmlInsertEventCount++;
        } else if (eventType == UPDATE_ROWS_EVENT || eventType == UPDATE_ROWS_EVENT_V1) {
            totalWriteDmlUpdateEventCount++;
        } else if (eventType == DELETE_ROWS_EVENT || eventType == DELETE_ROWS_EVENT_V1) {
            totalWriteDmlDeleteEventCount++;
        }
    }

    public void incrementTotalWriteDdlEventCount() {
        totalWriteDdlEventCount++;
        totalRevEventCount++;
    }

    public void incrementTotalWriteEventCount() {
        totalWriteEventCount++;
    }

    public void incrementTotalWriteBytes(long byteSize) {
        totalWriteEventBytes += byteSize;
    }

    public void incrementTotalRevBytes(long byteSize) {
        totalRevEventBytes += byteSize;
    }

    public void incrementTotalFlushWriteCount() {
        totalWriteFlushCount++;
    }

    public void incrementTotalUploadBytes(long byteSize) {
        totalUploadBytes.getAndAdd(byteSize);
    }

    public void incrementTotalDumpBytes(long byteSize) {
        totalDumpBytes.getAndAdd(byteSize);
    }

    public void setLatestDelayTimeOnCommit(long latestDelayTimeOnCommit) {
        this.latestDelayTimeOnCommit = latestDelayTimeOnCommit;
    }

    public void setLatestDataReceiveTime(long latestDataReceiveTime) {
        this.latestDataReceiveTime = latestDataReceiveTime;
    }

    public void setWriteQueueSize(long writeQueueSize) {
        this.writeQueueSize = writeQueueSize;
    }

    public void setReceiveQueueSize(long receiveQueueSize) {
        this.receiveQueueSize = receiveQueueSize;
    }

    public void setLatestTsoTime(long latestTsoTime) {
        this.latestTsoTime = latestTsoTime;
    }

    public void setLatestBinlogFile(String latestBinlogFile) {
        this.latestBinlogFile = latestBinlogFile;
    }

    public void setKwaySourceQueueSizeSupplier(
        Supplier<Map<String, Integer>> kwaySourceQueueSizeSupplier) {
        this.kwaySourceQueueSizeSupplier = kwaySourceQueueSizeSupplier;
    }

    // ---------------------------------getters---------------------------------

    public long getTotalRevEventCount() {
        return totalRevEventCount;
    }

    public long getTotalRevEventBytes() {
        return totalRevEventBytes;
    }

    public long getLatestDataReceiveTime() {
        return latestDataReceiveTime;
    }

    public long getTotalWriteTxnTime() {
        return totalWriteTxnTime;
    }

    public long getTotalWriteEventCount() {
        return totalWriteEventCount;
    }

    public long getTotalWriteDmlEventCount() {
        return totalWriteDmlEventCount;
    }

    public long getTotalWriteDmlTabMapEventCount() {
        return totalWriteDmlTabMapEventCount;
    }

    public long getTotalWriteDmlInsertEventCount() {
        return totalWriteDmlInsertEventCount;
    }

    public long getTotalWriteDmlUpdateEventCount() {
        return totalWriteDmlUpdateEventCount;
    }

    public long getTotalWriteDmlDeleteEventCount() {
        return totalWriteDmlDeleteEventCount;
    }

    public long getTotalWriteDdlEventCount() {
        return totalWriteDdlEventCount;
    }

    public long getTotalWriteEventBytes() {
        return totalWriteEventBytes;
    }

    public long getTotalWriteTxnCount() {
        return totalWriteTxnCount;
    }

    public long getLatestDelayTimeOnCommit() {
        return latestDelayTimeOnCommit;
    }

    public long getTotalWriteFlushCount() {
        return totalWriteFlushCount;
    }

    public long getWriteQueueSize() {
        return writeQueueSize;
    }

    public long getReceiveQueueSize() {
        return receiveQueueSize;
    }

    public String getStreamId() {
        return streamId;
    }

    public long getTotalUploadBytes() {
        return totalUploadBytes.get();
    }

    public long getTotalDumpBytes() {
        return totalDumpBytes.get();
    }

    public long getLatestTsoTime() {
        return latestTsoTime;
    }

    public String getLatestBinlogFile() {
        return latestBinlogFile;
    }

    @Override
    public void incrementUploadBytes(long byteSize) {
        incrementTotalUploadBytes(byteSize);
    }

    public Supplier<Map<String, Integer>> getKwaySourceQueueSizeSupplier() {
        return kwaySourceQueueSizeSupplier;
    }
}
