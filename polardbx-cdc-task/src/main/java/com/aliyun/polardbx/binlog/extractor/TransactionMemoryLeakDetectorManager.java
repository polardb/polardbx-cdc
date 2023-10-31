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
