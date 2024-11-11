/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.metrics;

import com.aliyun.polardbx.binlog.dumper.CdcServer;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumClientType;
import lombok.Getter;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DumpClientMetric {
    @Getter
    private String remoteIp;
    @Getter
    private int remotePort;
    @Getter
    private String fileName;
    @Getter
    private long position;
    @Getter
    private long timestamp;
    @Getter
    private EnumClientType clientType;

    private long lastAvgTimestamp = System.currentTimeMillis();

    private final MetricsManager metricsManager;

    public DumpClientMetric(String remoteIp, int remotePort, MetricsManager metricsManager) {
        this.remoteIp = remoteIp;
        this.remotePort = remotePort;
        this.metricsManager = metricsManager;
    }

    public String getDestination() {
        return remoteIp + ":" + remotePort;
    }

    private final AtomicLong dumpBytes = new AtomicLong(0);

    @Getter
    private long dumpStartTimestamp;

    @Getter
    private long lastSyncTimestamp;

    public static DumpClientMetric get() {
        return CdcServer.KEY_CLIENT_METRICS.get();
    }

    public static void addDumpBytes(long bytes) {
        DumpClientMetric metrics = get();
        if (metrics == null) {
            return;
        }
        metrics.dumpBytes.addAndGet(bytes);
        metrics.lastSyncTimestamp = System.currentTimeMillis();
    }

    public long getDumpBps() {
        long now = System.currentTimeMillis();
        long diff = Math.max(TimeUnit.MILLISECONDS.toSeconds(now - lastAvgTimestamp), 1);
        lastAvgTimestamp = now;
        return dumpBytes.getAndSet(0) / diff;
    }

    public static void startDump(EnumClientType clientType) {
        DumpClientMetric metrics = get();
        if (metrics == null) {
            return;
        }
        metrics.dumpStartTimestamp = System.currentTimeMillis();
        metrics.metricsManager.addClientMetric(metrics);
        metrics.clientType = clientType;
    }

    public static void stopDump() {
        DumpClientMetric metrics = get();
        if (metrics == null) {
            return;
        }
        metrics.metricsManager.removeClientMetric(metrics);
    }

    public static void recordPosition(String fileName, long position, long timestamp) {
        DumpClientMetric metrics = get();
        if (metrics == null) {
            return;
        }
        metrics.fileName = fileName;
        metrics.position = position;
        metrics.timestamp = timestamp;
    }

}
