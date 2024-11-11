/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.storage.MemoryLeakDetector;
import com.aliyun.polardbx.binlog.storage.memory.WatchObject;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_EXTRACT_WATCH_MEMORY_LEAK_ENABLED;

/**
 * created by ziyang.lb
 */
public class TransactionMemoryLeakDetectorManager {
    private static final TransactionMemoryLeakDetectorManager INSTANCE = new TransactionMemoryLeakDetectorManager();
    private final MemoryLeakDetector detector = new MemoryLeakDetector();
    private final boolean watchSwitch = DynamicApplicationConfig.getBoolean(TASK_EXTRACT_WATCH_MEMORY_LEAK_ENABLED);

    private TransactionMemoryLeakDetectorManager() {
        if (watchSwitch) {
            detector.start();
        }
    }

    public static TransactionMemoryLeakDetectorManager getInstance() {
        return INSTANCE;
    }

    public void watch(Transaction transaction) {
        if (watchSwitch) {
            detector.watch(transaction, new WatchObject());
        }
    }

    public void unWatch(Transaction transaction) {
        if (watchSwitch) {
            detector.unWatch(transaction);
        }
    }
}
