/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.storage.memory.WatchObject;

public class StorageMemoryLeakDectectorManager {
    private static StorageMemoryLeakDectectorManager instance = new StorageMemoryLeakDectectorManager();
    private MemoryLeakDetector memoryLeakDectector = new MemoryLeakDetector();

    private boolean watchSwitch = false;

    public StorageMemoryLeakDectectorManager() {
        if (watchSwitch) {
            memoryLeakDectector.start();
        }
    }

    public static StorageMemoryLeakDectectorManager getInstance() {
        return instance;
    }

    public void watch(TxnBuffer buffer) {
        if (watchSwitch) {
            memoryLeakDectector.watch(buffer.getTxnKey(), new WatchObject());
        }
    }

    public void unwatch(TxnBuffer buffer) {
        if (watchSwitch) {
            memoryLeakDectector.unWatch(buffer.getTxnKey());
        }
    }
}
