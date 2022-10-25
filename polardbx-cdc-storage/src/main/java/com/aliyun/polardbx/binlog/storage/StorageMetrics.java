/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.storage;

import lombok.Data;

import java.util.concurrent.atomic.AtomicLong;

/**
 * created by ziyang.lb
 **/
@Data
public class StorageMetrics {
    private static final StorageMetrics STORAGE_METRICS = new StorageMetrics();

    public static StorageMetrics get() {
        return STORAGE_METRICS;
    }

    private AtomicLong txnCreateCount = new AtomicLong(0L);
    private AtomicLong txnCreateCostTime = new AtomicLong(0L);

    private StorageMetrics() {
    }
}
