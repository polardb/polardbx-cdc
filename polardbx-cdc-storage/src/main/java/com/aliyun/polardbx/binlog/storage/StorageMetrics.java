/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
    private AtomicLong cleanerQueuedSize = new AtomicLong(0L);

    private StorageMetrics() {
    }
}
