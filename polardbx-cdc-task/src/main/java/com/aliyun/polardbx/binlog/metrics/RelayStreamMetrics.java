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
