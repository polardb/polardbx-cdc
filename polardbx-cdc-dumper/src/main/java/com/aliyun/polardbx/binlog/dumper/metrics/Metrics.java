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

package com.aliyun.polardbx.binlog.dumper.metrics;

/**
 * Created by ziyang.lb
 **/
public class Metrics {

    /**
     * 从dumper启动开始计算，截止到当前，已经收到的Event的总个数(ddl & dml)
     */
    private long totalRevEventCount;
    /**
     * 从dumper启动开始计算，截止到当前，已经收到的Event的总字节数(ddl & dml)
     */
    private long totalRevEventBytes;
    /**
     * 从dumper启动开始计算，截止到当前，已经收到的dml event的总个数
     */
    private long totalWriteDmlEventCount;
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
     * 从dumper启动开始计算，截止到当前，完成totalWriteEventCount个事件写入的总耗时
     */
    private long totalWriteEventTime;
    /**
     * 从dumper启动开始计算，截止到当前，已经向binlog文件写入的总字节数
     */
    private long totalWriteEventBytes;
    /**
     * 从dumper启动开始计算，BinlogFile执行flush write的总次数
     */
    private long totalFlushWriteCount;
    /**
     * 从dumper启动开始计算，BinlogFile由于写缓冲区已满，导致内存分配失败，并强制触发Flush的总次数
     */
    private long totalForceFlushWriteCount;
    /**
     * 当前最新的延迟时间(on receive)
     */
    private long latestDelayTimeOnReceive;
    /**
     * 当前最新的延迟时间(on commit)
     */
    private long latestDelayTimeOnCommit;
    /**
     * 最近一次收到数据的时间(单位：ms)
     */
    private long latestDataReceiveTime;

    private long beginTime;
    private long endTime;

    private static final Metrics METRICS;

    static {
        METRICS = new Metrics();
    }

    private Metrics() {
    }

    public static Metrics get() {
        return METRICS;
    }

    public Metrics snapshot() {
        Metrics result = new Metrics();
        result.totalWriteDdlEventCount = this.totalWriteDdlEventCount;
        result.totalWriteDmlEventCount = this.totalWriteDmlEventCount;
        result.totalRevEventCount = this.totalRevEventCount;
        result.totalRevEventBytes = this.totalRevEventBytes;
        result.totalWriteEventBytes = this.totalWriteEventBytes;
        result.totalWriteEventCount = this.totalWriteEventCount;
        result.totalWriteEventTime = this.totalWriteEventTime;
        result.totalWriteTxnCount = this.totalWriteTxnCount;
        result.totalWriteTxnTime = this.totalWriteTxnTime;
        result.totalFlushWriteCount = this.totalFlushWriteCount;
        result.totalForceFlushWriteCount = this.totalForceFlushWriteCount;
        result.latestDelayTimeOnReceive = this.latestDelayTimeOnReceive;
        result.latestDelayTimeOnCommit = this.latestDelayTimeOnCommit;
        result.latestDataReceiveTime = this.latestDataReceiveTime;
        return result;
    }

    // ---------------------------------setters---------------------------------

    public void markBegin() {
        beginTime = System.currentTimeMillis();
    }

    public void markEnd() {
        endTime = System.currentTimeMillis();
        totalWriteTxnTime += (endTime - beginTime);
    }

    public void incrementTotalWriteTxnCount() {
        totalWriteTxnCount++;
    }

    public void incrementTotalWriteDmlEventCount() {
        totalWriteDmlEventCount++;
        totalRevEventCount++;
    }

    public void incrementTotalWriteDdlEventCount() {
        totalWriteDdlEventCount++;
        totalRevEventCount++;
    }

    public void incrementTotalWriteEventCount() {
        totalWriteEventCount++;
    }

    public void incrementTotalWriteTime(long costTime) {
        totalWriteEventTime += costTime;
    }

    public void incrementTotalRevBytes(long byteSize) {
        totalRevEventBytes += byteSize;
    }

    public void incrementTotalWriteBytes(long byteSize) {
        totalWriteEventBytes += byteSize;
    }

    public void incrementTotalFlushWriteCount() {
        totalFlushWriteCount++;
    }

    public void incrementTotalForceFlushWriteCount() {
        totalForceFlushWriteCount++;
    }

    public void setLatestDelayTimeOnCommit(long latestDelayTimeOnCommit) {
        this.latestDelayTimeOnCommit = latestDelayTimeOnCommit;
    }

    public void setLatestDelayTimeOnReceive(long latestDelayTimeOnReceive) {
        this.latestDelayTimeOnReceive = latestDelayTimeOnReceive;
    }

    public void setLatestDataReceiveTime(long latestDataReceiveTime) {
        this.latestDataReceiveTime = latestDataReceiveTime;
    }

    // ---------------------------------getters---------------------------------

    public long getTotalRevEventBytes() {
        return totalRevEventBytes;
    }

    public long getTotalWriteTxnCount() {
        return totalWriteTxnCount;
    }

    public long getTotalRevEventCount() {
        return totalRevEventCount;
    }

    public long getTotalWriteDmlEventCount() {
        return totalWriteDmlEventCount;
    }

    public long getTotalWriteDdlEventCount() {
        return totalWriteDdlEventCount;
    }

    public long getTotalWriteTxnTime() {
        return totalWriteTxnTime;
    }

    public long getTotalWriteEventCount() {
        return totalWriteEventCount;
    }

    public long getTotalWriteEventTime() {
        return totalWriteEventTime;
    }

    public long getTotalWriteEventBytes() {
        return totalWriteEventBytes;
    }

    public long getLatestDelayTimeOnReceive() {
        return latestDelayTimeOnReceive;
    }

    public long getLatestDelayTimeOnCommit() {
        return latestDelayTimeOnCommit;
    }

    public long getTotalFlushWriteCount() {
        return totalFlushWriteCount;
    }

    public long getTotalForceFlushWriteCount() {
        return totalForceFlushWriteCount;
    }

    public long getLatestDataReceiveTime() {
        return latestDataReceiveTime;
    }
}
