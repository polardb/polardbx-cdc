/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.task.ICursorProvider;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Created by ziyang.lb
 **/
public class LogFileManagerCollection {
    private final Map<String, LogFileManager> nestedLogFileManagers;

    public LogFileManagerCollection() {
        this.nestedLogFileManagers = new HashMap<>();
    }

    public void add(String key, LogFileManager value) {
        this.nestedLogFileManagers.put(key, value);
    }

    public LogFileManager get(String key) {
        return this.nestedLogFileManagers.get(key);
    }

    /**
     * 多线程启动各个流的LogFileManager，加快恢复速度
     */
    public void start() {
        ExecutorService executorService = Executors.newCachedThreadPool();
        List<Future<?>> futureList = new ArrayList<>();
        nestedLogFileManagers.forEach(
            (streamName, logFileManager) -> {
                futureList.add(executorService.submit(logFileManager::start));
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

    public void stop() {
        nestedLogFileManagers.forEach((key, value) -> value.stop());
    }

    public Map<String, ICursorProvider> getCursorProviders() {
        return new HashMap<>(nestedLogFileManagers);
    }

    public Set<String> streamSet() {
        return nestedLogFileManagers.keySet();
    }
}
