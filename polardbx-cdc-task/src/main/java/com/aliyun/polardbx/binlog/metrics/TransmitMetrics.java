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
package com.aliyun.polardbx.binlog.metrics;

/**
 * created by ziyang.lb
 **/
public class TransmitMetrics {
    /**
     * Transmit阶段，队列中正在排队的TxnToken数量
     */
    private long transmitQueuedSize;
    /**
     * Transmit阶段，已经封装好Packet，准备网络发送的Packet数量
     */
    private long dumpingQueueSize;
    /**
     * 从Task启动开始计算，已经向下游发送的TxnToken总数
     */
    private long totalTransmitCount;
    /**
     * 从Task启动开始计算，以Single模式，向下游发送的TxnToken总数
     */
    private long totalSingleTransmitCount;
    /**
     * 从Task启动开始计算，以Chunk模式，向下游发送的TxnToken总数
     */
    private long totalChunkTransmitCount;
    /**
     * Transmit阶段的延迟时间(ms)
     */
    private long delayTimeOnTransmit;

    public TransmitMetrics snapshot() {
        TransmitMetrics snapshot = new TransmitMetrics();
        snapshot.transmitQueuedSize = this.transmitQueuedSize;
        snapshot.dumpingQueueSize = this.dumpingQueueSize;
        snapshot.totalTransmitCount = this.totalTransmitCount;
        snapshot.totalSingleTransmitCount = this.totalSingleTransmitCount;
        snapshot.totalChunkTransmitCount = this.totalChunkTransmitCount;
        snapshot.delayTimeOnTransmit = this.delayTimeOnTransmit;
        return snapshot;
    }

    // -------------------------------------------------- constructor --------------------------------------------------
    private static final TransmitMetrics TRANSMIT_METRICS;

    static {
        TRANSMIT_METRICS = new TransmitMetrics();
    }

    private TransmitMetrics() {
    }

    public static TransmitMetrics get() {
        return TRANSMIT_METRICS;
    }

    // ---------------------------------------------------- setters ----------------------------------------------------

    public void incrementSingleTransmitCount() {
        totalSingleTransmitCount++;
        totalTransmitCount++;
    }

    public void addChunkTransmitCount(int count) {
        totalChunkTransmitCount += count;
        totalTransmitCount += count;
    }

    public void setTransmitQueuedSize(long transmitQueuedSize) {
        this.transmitQueuedSize = transmitQueuedSize;
    }

    public void setDumpingQueueSize(long dumpingQueueSize) {
        this.dumpingQueueSize = dumpingQueueSize;
    }

    // ---------------------------------------------------- getters ----------------------------------------------------

    public long getTransmitQueuedSize() {
        return transmitQueuedSize;
    }

    public long getDumpingQueueSize() {
        return dumpingQueueSize;
    }

    public long getTotalTransmitCount() {
        return totalTransmitCount;
    }

    public long getTotalSingleTransmitCount() {
        return totalSingleTransmitCount;
    }

    public long getTotalChunkTransmitCount() {
        return totalChunkTransmitCount;
    }

    public long getDelayTimeOnTransmit() {
        return delayTimeOnTransmit;
    }

    public void setDelayTimeOnTransmit(long delayTimeOnTransmit) {
        this.delayTimeOnTransmit = delayTimeOnTransmit;
    }

}
