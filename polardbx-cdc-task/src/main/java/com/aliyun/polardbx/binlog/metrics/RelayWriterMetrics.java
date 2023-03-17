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
