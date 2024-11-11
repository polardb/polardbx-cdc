/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
