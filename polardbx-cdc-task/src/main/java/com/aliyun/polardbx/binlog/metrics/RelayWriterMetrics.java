/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.metrics;

import lombok.Data;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * created by ziyang.lb
 **/
@Data
public class RelayWriterMetrics {
    private static final Map<String, RelayWriterMetrics> METRICS_MAP = new ConcurrentHashMap<>();

    public static void register(String key, RelayWriterMetrics metrics) {
        METRICS_MAP.put(key, metrics);
    }

    public static Map<String, RelayWriterMetrics> getMetricsMap() {
        return METRICS_MAP;
    }

    private String threadId;
    private long queuedSize;
    private long putCount;
    private long takeCount;
    private Set<Integer> streams = new HashSet<>();
}
