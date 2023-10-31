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
package com.aliyun.polardbx.binlog.dumper.metrics;

import com.aliyun.polardbx.binlog.dumper.CdcServer;
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

    public static void startDump() {
        DumpClientMetric metrics = get();
        if (metrics == null) {
            return;
        }
        metrics.dumpStartTimestamp = System.currentTimeMillis();
        metrics.metricsManager.addClientMetric(metrics);
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
