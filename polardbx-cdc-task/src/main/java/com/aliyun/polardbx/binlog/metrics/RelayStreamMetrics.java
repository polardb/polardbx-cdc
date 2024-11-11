/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.metrics;

import lombok.Data;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * created by ziyang.lb
 **/
@Data
public class RelayStreamMetrics {
    private static final Map<Integer, RelayStreamMetrics> METRICS_MAP = new ConcurrentHashMap<>();

    public static void register(Integer key, RelayStreamMetrics metrics) {
        METRICS_MAP.put(key, metrics);
    }

    public static Map<Integer, RelayStreamMetrics> getMetricsMap() {
        return METRICS_MAP;
    }

    public RelayStreamMetrics(int streamSeq) {
        this.streamSeq = streamSeq;
    }

    private final int streamSeq;
    private AtomicLong writeEventCount = new AtomicLong(0);
    private AtomicLong readEventCount = new AtomicLong(0);
    private AtomicLong writeByteSize = new AtomicLong(0);
    private AtomicLong readByteSize = new AtomicLong(0);
    private AtomicLong writeDelay = new AtomicLong(0);
    private AtomicLong readDelay = new AtomicLong(0);
    private AtomicLong minRelayTimestamp = new AtomicLong(0);
    private AtomicLong maxRelayTimestamp = new AtomicLong(0);
    private AtomicLong writeBps = new AtomicLong(0);
    private AtomicLong readBps = new AtomicLong(0);
    private AtomicLong writeEps = new AtomicLong(0);
    private AtomicLong readEps = new AtomicLong(0);
    private AtomicLong fileCount = new AtomicLong(0);
}
