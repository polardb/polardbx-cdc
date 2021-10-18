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

package com.aliyun.polardbx.binlog.metrics;

import com.aliyun.polardbx.binlog.merge.MergeSource;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by ziyang.lb
 **/
public class MergeMetrics {

    private Map<String, MergeSource> mergeSources = new HashMap<>();

    /**
     * 从Task启动开始计算，通过LogEventMerger的TxnToken总量
     */
    private long totalMergePassCount;
    /**
     * 从Task启动开始计算，LogEventMerger空轮询的次数，即每次没有Poll到数据
     */
    private long totalMergePollEmptyCount;
    /**
     * 从Task启动开始计算，通过LogEventMerger的1PC类型的TxnToken总量
     */
    private long totalMergePass1PCCount;
    /**
     * 从Task启动开始计算，通过LogEventMerger的2PC类型的TxnToken总量
     */
    private long totalMergePass2PCCount;
    /**
     * 从Task启动开始计算，Merger向Collector发送数据时，总的阻塞时间(纳秒)
     */
    private long totalPushToCollectorBlockTime;
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
     * Merger阶段的延迟时间(ms)
     */
    private long delayTimeOnMerge;
    /**
     * Collect阶段的延迟时间(ms)
     */
    private long delayTimeOnCollect;
    /**
     * Transmit阶段的延迟时间(ms)
     */
    private long delayTimeOnTransmit;
    /**
     * Collect阶段，RingBuffer队列中正在排队的TxnToken数量
     */
    private long ringBufferQueuedSize;
    /**
     * Transmit阶段，队列中正在排队的TxnToken数量
     */
    private long transmitQueuedSize;
    /**
     * Storage清理队列的大小
     */
    private long storageCleanerQueuedSize;

    public MergeMetrics snapshot() {
        MergeMetrics snapshot = new MergeMetrics();
        snapshot.totalMergePassCount = this.totalMergePassCount;
        snapshot.totalMergePass1PCCount = this.totalMergePass1PCCount;
        snapshot.totalMergePass2PCCount = this.totalMergePass2PCCount;
        snapshot.totalPushToCollectorBlockTime = this.totalPushToCollectorBlockTime;
        snapshot.totalTransmitCount = this.totalTransmitCount;
        snapshot.totalSingleTransmitCount = this.totalSingleTransmitCount;
        snapshot.totalChunkTransmitCount = this.totalChunkTransmitCount;
        snapshot.delayTimeOnMerge = this.delayTimeOnMerge;
        snapshot.delayTimeOnCollect = this.delayTimeOnCollect;
        snapshot.delayTimeOnTransmit = this.delayTimeOnTransmit;
        snapshot.ringBufferQueuedSize = this.ringBufferQueuedSize;
        snapshot.transmitQueuedSize = this.transmitQueuedSize;
        snapshot.mergeSources = this.mergeSources;
        snapshot.storageCleanerQueuedSize = this.storageCleanerQueuedSize;
        return snapshot;
    }

    public void incrementMergePass1PCCount() {
        totalMergePassCount++;
        totalMergePass1PCCount++;
    }

    public void incrementMergePass2PCCount() {
        totalMergePassCount++;
        totalMergePass2PCCount++;
    }

    public void incrementSingleTransmitCount() {
        totalSingleTransmitCount++;
        totalTransmitCount++;
    }

    public void incrementMergePollEmptyCount() {
        totalMergePollEmptyCount++;
    }

    public void addChunkTransmitCount(int count) {
        totalChunkTransmitCount += count;
        totalTransmitCount += count;
    }

    public void addMergeSources(Map<String, MergeSource> mergeSources) {
        this.mergeSources.putAll(mergeSources);
    }

    // ---------------------------------单 例----------------------------------
    private static final MergeMetrics MERGE_METRICS;

    static {
        MERGE_METRICS = new MergeMetrics();
    }

    private MergeMetrics() {
    }

    public static MergeMetrics get() {
        return MERGE_METRICS;
    }

    // ---------------------------------get&set---------------------------------

    public long getTotalTransmitCount() {
        return totalTransmitCount;
    }

    public long getTotalSingleTransmitCount() {
        return totalSingleTransmitCount;
    }

    public long getTotalChunkTransmitCount() {
        return totalChunkTransmitCount;
    }

    public long getDelayTimeOnMerge() {
        return delayTimeOnMerge;
    }

    public void setDelayTimeOnMerge(long delayTimeOnMerge) {
        this.delayTimeOnMerge = delayTimeOnMerge;
    }

    public long getDelayTimeOnCollect() {
        return delayTimeOnCollect;
    }

    public void setDelayTimeOnCollect(long delayTimeOnCollect) {
        this.delayTimeOnCollect = delayTimeOnCollect;
    }

    public long getDelayTimeOnTransmit() {
        return delayTimeOnTransmit;
    }

    public void setDelayTimeOnTransmit(long delayTimeOnTransmit) {
        this.delayTimeOnTransmit = delayTimeOnTransmit;
    }

    public long getRingBufferQueuedSize() {
        return ringBufferQueuedSize;
    }

    public void setRingBufferQueuedSize(long ringBufferQueuedSize) {
        this.ringBufferQueuedSize = ringBufferQueuedSize;
    }

    public long getTransmitQueuedSize() {
        return transmitQueuedSize;
    }

    public void setTransmitQueuedSize(long transmitQueuedSize) {
        this.transmitQueuedSize = transmitQueuedSize;
    }

    public long getTotalMergePassCount() {
        return totalMergePassCount;
    }

    public long getTotalMergePollEmptyCount() {
        return totalMergePollEmptyCount;
    }

    public long getTotalPushToCollectorBlockTime() {
        return totalPushToCollectorBlockTime;
    }

    public void setTotalPushToCollectorBlockTime(long totalPushToCollectorBlockTime) {
        this.totalPushToCollectorBlockTime = totalPushToCollectorBlockTime;
    }

    public long getTotalMergePass1PCCount() {
        return totalMergePass1PCCount;
    }

    public long getTotalMergePass2PCCount() {
        return totalMergePass2PCCount;
    }

    public long getStorageCleanerQueuedSize() {
        return storageCleanerQueuedSize;
    }

    public void setStorageCleanerQueuedSize(long storageCleanerQueuedSize) {
        this.storageCleanerQueuedSize = storageCleanerQueuedSize;
    }

    public HashMap<String, Long> getMergeSourceQueuedSize() {
        HashMap<String, Long> result = new HashMap<>();
        mergeSources.forEach((k, v) -> result.put(k, v.getQueuedSize()));
        return result;
    }

}
