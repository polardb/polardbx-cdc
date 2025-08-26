/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.lock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author zm
 * 负责管理本地文件读写锁，当文件正在被dump时，不应被删除。
 */
public class LogFileLockManagerCollection {
    private final Map<String, LogFileLockManager> logFileLockManagerMap;

    public LogFileLockManagerCollection() {
        logFileLockManagerMap = new HashMap<>();
    }

    public void add(String key, LogFileLockManager value) {
        logFileLockManagerMap.put(key, value);
    }

    public LogFileLockManager get(String key) {
        return logFileLockManagerMap.get(key);
    }

    /**
     * 多线程启动各个流的LogFileLockManager，加快恢复速度
     */
    public void start() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        List<Future<?>> futureList = new ArrayList<>();
        logFileLockManagerMap.forEach(
            (streamName, logFileLockManager) -> {
                futureList.add(executorService.submit(logFileLockManager::init));
            });

        futureList.forEach(f -> {
            try {
                f.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        });

        executorService.shutdownNow();
    }
}
