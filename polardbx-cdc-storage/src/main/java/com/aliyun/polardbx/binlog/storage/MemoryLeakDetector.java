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
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.storage.memory.WatchObject;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MemoryLeakDetector implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(MemoryLeakDetector.class);
    private static final long WARNING_INTERVAL = TimeUnit.MINUTES.toMillis(2);
    private static final ScheduledExecutorService schedule = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    });
    private ConcurrentHashMap<Object, WatchObject> watchBuffer = new ConcurrentHashMap();

    public MemoryLeakDetector() {
    }

    public void start() {
        schedule.scheduleAtFixedRate(this, 10, 10, TimeUnit.SECONDS);
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
                logger.error("counter ： " + counterEntry.getValue() + ",  trace @ " + counterEntry.getKey());
            }
        }
    }

    private boolean warning(long now, WatchObject watchObject) {
        return now - watchObject.createTime() > WARNING_INTERVAL;
    }

}
