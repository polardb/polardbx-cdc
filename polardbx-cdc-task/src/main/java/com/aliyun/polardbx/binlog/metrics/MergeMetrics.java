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
 * Created by ziyang.lb
 **/
public class MergeMetrics {

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
     * Merger阶段的延迟时间(ms)
     */
    private long delayTimeOnMerge;
    /**
     * Collect阶段的延迟时间(ms)
     */
    private long delayTimeOnCollect;
    /**
     * Collect阶段，RingBuffer队列中正在排队的TxnToken数量
     */
    private long collectQueuedSize;

    public MergeMetrics snapshot() {
        MergeMetrics snapshot = new MergeMetrics();
        snapshot.totalMergePassCount = this.totalMergePassCount;
        snapshot.totalMergePass1PCCount = this.totalMergePass1PCCount;
        snapshot.totalMergePass2PCCount = this.totalMergePass2PCCount;
        snapshot.totalPushToCollectorBlockTime = this.totalPushToCollectorBlockTime;
        snapshot.delayTimeOnMerge = this.delayTimeOnMerge;
        snapshot.delayTimeOnCollect = this.delayTimeOnCollect;
        snapshot.collectQueuedSize = this.collectQueuedSize;
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

    public void incrementMergePollEmptyCount() {
        totalMergePollEmptyCount++;
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

    public long getCollectQueuedSize() {
        return collectQueuedSize;
    }

    public void setCollectQueuedSize(long collectQueuedSize) {
        this.collectQueuedSize = collectQueuedSize;
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
}
