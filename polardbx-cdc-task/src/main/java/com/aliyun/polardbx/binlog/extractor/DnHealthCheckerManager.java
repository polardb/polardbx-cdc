/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.RandomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class DnHealthCheckerManager {

    private static final Logger logger = LoggerFactory.getLogger(DnHealthCheckerManager.class);

    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "dn-helper-checker");
            t.setDaemon(true);
            return t;
        });
    private final ConcurrentHashMap<String, DnHealthChecker> taskCheckerMap = new ConcurrentHashMap<>();
    private final AtomicBoolean startedSchedule = new AtomicBoolean(false);

    @Getter
    @Setter
    private long lastInjectTime = System.currentTimeMillis();

    public void registerTask(DnHealthChecker checker) {
        DnHealthChecker oldChecker = taskCheckerMap.put(checker.getStorageInstId(), checker);
        if (oldChecker != null) {
            logger.warn("duplicate start check dn health for {}", oldChecker.getStorageInstId());
        }
    }

    public void unregisterTask(DnHealthChecker checker) {
        taskCheckerMap.remove(checker.getStorageInstId());
    }

    public void start() {
        if (!DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_SWITCH)) {
            return;
        }
        int interval = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_INTERVAL_SEC);
        if (startedSchedule.compareAndSet(false, true)) {
            scheduledExecutorService.scheduleAtFixedRate(() -> {
                try {
                    scheduleCheck();
                } catch (Throwable ignored) {
                }
            }, interval, interval, java.util.concurrent.TimeUnit.SECONDS);
        }
        logger.info("start checker schedule");
    }

    public boolean injectError() {
        long interval = TimeUnit.MINUTES.toMillis(
            DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_ERROR_INJECT_INTERVAL_MIN));
        long now = System.currentTimeMillis();
        boolean ret = RandomUtils.nextBoolean() && now - getLastInjectTime() > interval;
        setLastInjectTime(now);
        return ret;
    }

    public void scheduleCheck() {
        try {
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_ERROR_INJECT_IN_LAB) &&
                DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV)) {
                if (injectError()) {
                    throw new IOException("error inject for dn health check");
                }
            }
            for (DnHealthChecker checker : taskCheckerMap.values()) {
                checker.check();
            }
        } catch (Throwable t) {
            taskCheckerMap.clear();
            logger.error("check dn health failed, will restart task engine!", t);
            Runtime.getRuntime().halt(1);
        }
    }

    public void stop() {
        if (!DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_SWITCH)) {
            return;
        }
        taskCheckerMap.clear();
        scheduledExecutorService.shutdownNow();
        startedSchedule.set(false);
        logger.info("stop checker schedule");
    }

}
