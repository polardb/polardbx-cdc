/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.task.IDumperStatisticProvider;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class LogFileManagerCollection {
    private final Map<String, LogFileManager> nestedLogFileManagers;

    public LogFileManagerCollection() {
        this.nestedLogFileManagers = new ConcurrentHashMap<>();
    }

    public void add(String key, LogFileManager value) {
        this.nestedLogFileManagers.put(key, value);
    }

    public LogFileManager get(String key) {
        return this.nestedLogFileManagers.get(key);
    }

    public boolean contains(String key) {
        return this.nestedLogFileManagers.containsKey(key);
    }

    public void start() {
        nestedLogFileManagers.forEach((key, value) -> value.start());
    }

    public void start(Map<String, LogFileManager> logFileManagerMap) {
        nestedLogFileManagers.putAll(logFileManagerMap);
        logFileManagerMap.forEach(
            (streamName, logFileManager) -> logFileManager.start());
    }

    public void stop(String streamName) {
        LogFileManager logFileManager = nestedLogFileManagers.get(streamName);
        if (logFileManager != null) {
            logFileManager.stop();
        }
    }

    @SneakyThrows
    public void clean(String streamName) {
        LogFileManager logFileManager = nestedLogFileManagers.get(streamName);
        if (logFileManager != null) {
            logFileManager.clean();
            nestedLogFileManagers.remove(streamName);
        }
    }

    public void refreshAndRestart(String streamName, ExecutionConfig executionConfig) {
        LogFileManager logFileManager = nestedLogFileManagers.get(streamName);
        logFileManager.refreshAndRestart(executionConfig);
    }

    public void refresh(String streamName, ExecutionConfig executionConfig) {
        LogFileManager logFileManager = nestedLogFileManagers.get(streamName);
        logFileManager.refresh(executionConfig);
    }

    public void stop() {
        nestedLogFileManagers.forEach((key, value) -> value.stop());
    }

    public Map<String, IDumperStatisticProvider> getCursorProviders() {
        return new HashMap<>(nestedLogFileManagers);
    }

    public Set<String> streamSet() {
        return nestedLogFileManagers.keySet();
    }
}
