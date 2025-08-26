/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class MultiPartStreamMetrics {
    @Getter
    private final List<PartStreamMetrics> partMetrics = new ArrayList<>();
    @Getter
    private final String fileName;
    private final String url;
    @Getter
    private final String storageInstanceId;
    @Getter
    private long startTime;
    private final AtomicInteger finishCounter;

    public MultiPartStreamMetrics(String fileName, String url, String storageInstanceId, AtomicInteger finishCounter) {
        this.fileName = fileName;
        this.url = url;
        this.storageInstanceId = storageInstanceId;
        this.finishCounter = finishCounter;
    }

    public void addPartMetrics(PartStreamMetrics partStreamMetrics) {
        partMetrics.add(partStreamMetrics);
    }

    public void startMetrics() {
        this.startTime = System.currentTimeMillis();
        SearchMetricsManager.getInstance().put(storageInstanceId, this);
    }

    public void finishMetrics() {
        SearchMetricsManager.getInstance().remove(storageInstanceId);
    }

    public boolean isFinish() {
        return finishCounter.get() == partMetrics.size();
    }

    public long getFinishedTime() {
        long finishedTime = 0;
        for (PartStreamMetrics partStreamMetrics : partMetrics) {
            finishedTime = Math.max(finishedTime, partStreamMetrics.getFinishTimestamp());
        }
        return finishedTime;
    }
}
