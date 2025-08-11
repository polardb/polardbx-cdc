/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.metrics;

import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumClientType;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumProtocolType;
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
    /**
     * 分协议来说:
     * DUMP:从最后一个event中解析出来的时间戳
     * SYNC:由于不在服务端拆分event，因此设为-1
     */
    @Getter
    private long timestamp;
    @Getter
    private EnumClientType clientType;
    @Getter
    private long processId;
    @Getter
    private String traceId;
    @Getter
    private EnumProtocolType protocolType;

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

    public static void addDumpBytes(long bytes, DumpClientMetric metrics) {
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

    public static void startDump(EnumClientType clientType, EnumProtocolType protocolType, long processId,
                                 String traceId, DumpClientMetric metrics) {
        if (metrics == null) {
            return;
        }
        metrics.dumpStartTimestamp = System.currentTimeMillis();
        metrics.metricsManager.addClientMetric(metrics);
        metrics.clientType = clientType;
        metrics.processId = processId;
        metrics.traceId = traceId;
        metrics.protocolType = protocolType;
    }

    public static void stopDump(DumpClientMetric metrics) {
        if (metrics == null) {
            return;
        }
        metrics.metricsManager.removeClientMetric(metrics);
    }

    public static void recordPosition(String fileName, long position, long timestamp, DumpClientMetric metrics) {
        if (metrics == null) {
            return;
        }
        metrics.fileName = fileName;
        metrics.position = position;
        metrics.timestamp = timestamp;
    }

}
