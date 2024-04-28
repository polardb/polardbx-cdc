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
package com.aliyun.polardbx.binlog.client.metrics;

import java.util.concurrent.atomic.AtomicLong;

public class CdcClientMetricsManager {

    private static CdcClientMetricsManager instance = new CdcClientMetricsManager();
    private AtomicLong rt = new AtomicLong(0);
    private AtomicLong receiveEvent = new AtomicLong(0);

    private CdcClientMetricsManager() {
    }

    public static final CdcClientMetricsManager getInstance() {
        return instance;
    }

    public void recordRt(long rt) {
        this.rt.set(rt);
    }

    public void addEvent(int count) {
        this.receiveEvent.addAndGet(count);
    }

    public void start() {

    }

    public void stop() {

    }
}
