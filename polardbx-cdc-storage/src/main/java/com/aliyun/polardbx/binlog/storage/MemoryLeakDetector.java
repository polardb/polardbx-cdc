/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.storage.memory.WatchObject;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * created by ziyang.lb
 */
@Slf4j
public class MemoryLeakDetector implements Runnable {
    private static final long WARNING_INTERVAL = TimeUnit.MINUTES.toMillis(2);
    private static final ScheduledExecutorService SCHEDULE = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });

    private final ConcurrentHashMap<Object, WatchObject> watchBuffer = new ConcurrentHashMap<>();

    public void start() {
        SCHEDULE.scheduleAtFixedRate(this, 10, 10, TimeUnit.SECONDS);
    }

    public void watch(Object key, WatchObject object) {
        watchBuffer.put(key, object);
    }

    public void unWatch(Object key) {
        watchBuffer.remove(key);
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        Map<String, Integer> traceCounterMap = Maps.newHashMap();
        for (WatchObject watchObject : watchBuffer.values()) {
            if (warning(now, watchObject)) {
                Integer counter = traceCounterMap.get(watchObject.trace());
                if (counter == null) {
                    counter = 0;
                }
                traceCounterMap.put(watchObject.trace(), counter + 1);
            }
        }
        if (!CollectionUtils.isEmpty(traceCounterMap)) {
            for (Map.Entry<String, Integer> counterEntry : traceCounterMap.entrySet()) {
                log.error("find memory leak with transaction object , "
                    + "count : " + counterEntry.getValue() + ", trace @ " + counterEntry.getKey());
            }
        }
    }

    private boolean warning(long now, WatchObject watchObject) {
        return now - watchObject.createTime() > WARNING_INTERVAL;
    }

}
