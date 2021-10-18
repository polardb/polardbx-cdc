/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.task;

import lombok.extern.slf4j.Slf4j;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by ziyang.lb
 */
@Slf4j
public abstract class AbstractBinlogTimerTask {
    protected String cluster;
    protected String name;
    protected int interval;
    private Timer timer;

    private AbstractBinlogTimerTask() {
    }

    public AbstractBinlogTimerTask(String cluster, String name, int interval) {
        this.cluster = cluster;
        this.name = name;
        this.interval = interval;
        timer = new Timer("TimerTask-" + name);
    }

    public void run() {
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("exec {} {}", name, interval);
                    }
                    exec();
                } catch (Throwable e) {
                    log.error("exec timer task fail {} {}", name, interval, e);
                }
            }
        }, 0, interval);
    }

    public void start() {
        new Thread(() -> run(), "Thread-TimerTask-" + name).start();
    }

    public abstract void exec();

    public void stop() {
        timer.cancel();
    }

}
