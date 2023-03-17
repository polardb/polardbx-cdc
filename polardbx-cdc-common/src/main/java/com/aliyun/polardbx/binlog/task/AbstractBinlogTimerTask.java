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
public abstract class AbstractBinlogTimerTask {
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

    public void start() {
        new Thread(() -> run(), "Thread-TimerTask-" + name).start();
    }

    public abstract void exec();

    @SneakyThrows
    public void stop() {
        timer.shutdownNow();
        timer.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        log.info("TimerTask-" + name + " stopped.");
    }
}
