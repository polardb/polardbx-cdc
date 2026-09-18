/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.rpl.common.NamedThreadFactory;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.TaskStatus;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HeartbeatManager {
    @Getter
    private long lastEventTimestamp;
    private long taskId;
    @Getter
    @Setter
    private ScheduledExecutorService executorService;

    private static final HeartbeatManager INSTANCE = new HeartbeatManager();

    public static HeartbeatManager getInstance() {
        return INSTANCE;
    }

    private HeartbeatManager() {
    }

    public void init(long taskId) {
        this.lastEventTimestamp = System.currentTimeMillis();
        this.taskId = taskId;
        this.executorService = new ScheduledThreadPoolExecutor(1,
            new NamedThreadFactory("HeartbeatManager"));
    }

    public void heartbeat() {
        lastEventTimestamp = System.currentTimeMillis();
    }

    public void start() {
        executorService.scheduleAtFixedRate(this::flushHeartbeat, 0, 5, TimeUnit.SECONDS);
    }

    public void flushHeartbeat() {
        int maxRetries = 3;
        Throwable lastException = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // get the latest status before update
                RplTask task = DbTaskMetaManager.getTask(taskId);
                if (task == null) {
                    log.error("task has been deleted from db, taskId={}, process will exit", taskId);
                    Runtime.getRuntime().halt(1);
                    return;
                }
                if (lastEventTimestamp > 0) {
                    Date gmtHeartBeat = new Date(lastEventTimestamp);
                    TaskStatus status = TaskStatus.valueOf(task.getStatus());
                    if (status == TaskStatus.RUNNING) {
                        DbTaskMetaManager.updateTask(taskId, null, null, null, null, gmtHeartBeat);
                    } else {
                        log.error("task is not in running status ({}), taskId={}, process will exit",
                            status, taskId);
                        Runtime.getRuntime().halt(1);
                        return;
                    }
                }
                return; // success
            } catch (Throwable e) {
                lastException = e;
                log.warn("flushHeartbeat failed, attempt={}/{}, taskId={}", attempt, maxRetries, taskId, e);
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(1000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
        // Transient errors: only log, keep scheduled task alive for next cycle retry
        log.error("flushHeartbeat failed after {} retries, taskId={}, will retry next cycle",
            maxRetries, taskId, lastException);
    }
}
