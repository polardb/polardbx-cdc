/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.task;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by ziyang.lb
 */
@Slf4j
public abstract class AbstractBinlogTimerTask implements IScheduleJob {
    protected String clusterId;
    protected String clusterType;
    protected String name;
    protected long interval;
    private ScheduledExecutorService timer;

    private AbstractBinlogTimerTask() {
    }

    public AbstractBinlogTimerTask(String clusterId, String clusterType, String name, long interval) {
        this.clusterId = clusterId;
        this.name = name;
        this.interval = interval;
        this.clusterType = clusterType;
        this.timer = Executors.newSingleThreadScheduledExecutor((r) -> new Thread(r, "TimerTask-" + name));
    }

    public void run() {
        timer.scheduleWithFixedDelay(() -> {
            try {
                if (log.isDebugEnabled()) {
                    log.debug("exec {} {}", name, interval);
                }
                exec();
            } catch (Throwable e) {
                log.error("exec timer task fail {} {}", name, interval, e);
            }
        }, 0, interval, TimeUnit.MILLISECONDS);

        log.info("TimerTask-" + name + " started.");
    }

    @Override
    public void start() {
        new Thread(() -> run(), "Thread-TimerTask-" + name).start();
    }

    public abstract void exec();

    @Override
    @SneakyThrows
    public void stop() {
        timer.shutdownNow();
        timer.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        log.info("TimerTask-" + name + " stopped.");
    }
}
