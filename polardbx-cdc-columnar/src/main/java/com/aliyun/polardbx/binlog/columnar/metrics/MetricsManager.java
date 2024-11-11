/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.columnar.metrics;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.MetaDbDataSource;
import com.aliyun.polardbx.binlog.jvm.JvmSnapshot;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.metrics.format.TableFormat;
import com.aliyun.polardbx.binlog.proc.ProcSnapshot;
import com.aliyun.polardbx.binlog.proc.ProcUtils;
import com.aliyun.polardbx.binlog.util.CommonMetricsHelper;
import com.aliyun.polardbx.binlog.util.MetricsReporter;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.aliyun.polardbx.binlog.ConfigKeys.PRINT_METRICS;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.util.CommonMetricsHelper.addJvmMetrics;
import static com.aliyun.polardbx.binlog.util.CommonMetricsHelper.addProcMetrics;

/**
 * @author wenki
 */
public class MetricsManager {
    private static final Logger METRICS_LOGGER = LoggerFactory.getLogger("METRICS");
    private static final long INTERVAL = TimeUnit.SECONDS.toMillis(5);
    private final AtomicLong snapshotSeq = new AtomicLong(0);

    private final ScheduledExecutorService scheduledExecutorService;
    private MetricsSnapshot lastSnapshot;
    private long startTime;
    private volatile boolean running;

    public MetricsManager() {
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "columnar-metrics-manager");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;

        startTime = System.currentTimeMillis();
        scheduledExecutorService.scheduleAtFixedRate(() -> {
            try {
                MetricsSnapshot snapshot = buildSnapshot();
                if (DynamicApplicationConfig.getBoolean(PRINT_METRICS)) {
                    print(snapshot);
                    sendMetrics(snapshot);
                }
                lastSnapshot = snapshot;
            } catch (Throwable e) {
                METRICS_LOGGER.error("metrics print error!", e);
            }
        }, INTERVAL, INTERVAL, TimeUnit.MILLISECONDS);
        METRICS_LOGGER.info("metrics manager started.");
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        scheduledExecutorService.shutdownNow();
        METRICS_LOGGER.info("metrics manager stopped.");
    }

    private MetricsSnapshot buildSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot(snapshotSeq.incrementAndGet());
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.jvmSnapshot = JvmUtils.buildJvmSnapshot();
        snapshot.procSnapshot = ProcUtils.buildProcSnapshot();

        snapshot.columnarMetrics = ColumnarMetrics.get().snapshot();

        return snapshot;
    }

    @SneakyThrows
    private void sendMetrics(MetricsSnapshot snapshot) {
        List<CommonMetrics> commonMetrics = Lists.newArrayList();
        ColumnarMetrics columnarMetrics = snapshot.columnarMetrics;
        Field[] metricsFields = ColumnarMetrics.class.getDeclaredFields();
        for (Field f : metricsFields) {
            CommonMetrics x1 = CommonMetricsHelper.getColumnar().get(f.getName());
            if (x1 != null) {
                f.setAccessible(true);
                if (columnarMetrics != null) {
                    Object o = f.get(columnarMetrics);
                    if (o != null) {
                        commonMetrics.add(x1.var(o));
                    }
                }
            }
        }

        JvmSnapshot jvmSnapshot = snapshot.jvmSnapshot;
        if (jvmSnapshot != null) {
            String prefix = "polardbx_columnar_";
            addJvmMetrics(commonMetrics, jvmSnapshot, prefix);
        }

        ProcSnapshot procSnapshot = snapshot.procSnapshot;
        if (procSnapshot != null) {
            addProcMetrics(commonMetrics, procSnapshot, "polardbx_columnar_");
        }

        if (!CollectionUtils.isEmpty(commonMetrics)) {
            MetricsReporter.report(commonMetrics);
        }
    }

    private void print(MetricsSnapshot snapshot) {

        StringBuilder sb = new StringBuilder();
        sb.append("\r\n");
        sb.append(
            "######################################################## columnar metrics begin #####################################################");
        sb.append("\r\n");

        contactColumnarMetrics(snapshot, sb);
        sb.append("\r\n");
        sb.append(
            "######################################################## columnar metrics end ########################################################");
        sb.append("\r\n");

        METRICS_LOGGER.info(sb.toString());
    }

    private void contactColumnarMetrics(MetricsSnapshot snapshot, StringBuilder sb) {
        ColumnarMetrics columnarMetrics = snapshot.columnarMetrics;
        TableFormat columnarFormat = new TableFormat("Columnar Metrics");
        columnarFormat.addColumn(
            "getIndexCount",
            "getPartitionCount",
            "getGroupCommitLatency",
            "getGroupCommitThroughput",
            "getGroupCommitRow",
            "getBinlogQueueLatency",
            "getOssWriteLatency",
            "getDataConsistencyLockGroupCommit",
            "getOrcCount",
            "getOrcSize",
            "getDelCount",
            "getDelSize",
            "getCsvCount",
            "getCsvSize",
            "getCompactionTaskCount",
            "getCompactionFileSize",
            "getDataConsistencyLockCompaction",
            "getCompactionReadOssBandWidth",
            "getCompactionWriteOssBandWidth",
            "getPkIdxWriteCount",
            "getPkIdxReadCount",
            "getPkIdxLogBytes",
            "getPkIdxSstReadCount",
            "getPkIdxSstMemGetCount",
            "getPkIdxSstMemMissCount",
            "getPkIdxLocalReadCount",
            "getPkIdxLocalReadBytes",
            "getPkIdxRemoteReadCount",
            "getPkIdxRemoteReadBytes",
            "getPkIdxSstWriteCount",
            "getPkIdxSstWriteBytes",
            "getPkIdxLogFixCount",
            "getBinlogNetThroughput",
            "getBinlogEventThroughput",
            "getBinlogEventInsert",
            "getBinlogEventDelete",
            "getBinlogEventUpdate",
            "getBinlogEventDDL",
            "getBinlogEventInsertRows",
            "getBinlogEventDeleteRows",
            "getBinlogEventUpdateRows",
            "getBinlogTrxCount",
            "getBinlogReadLatency");
        columnarFormat.addRow(
            columnarMetrics.getIndexCount(),
            columnarMetrics.getPartitionCount(),
            columnarMetrics.getGroupCommitLatency(),
            columnarMetrics.getGroupCommitThroughput(),
            columnarMetrics.getGroupCommitRow(),
            columnarMetrics.getBinlogQueueLatency(),
            columnarMetrics.getOssWriteLatency(),
            columnarMetrics.getDataConsistencyLockGroupCommit(),
            columnarMetrics.getOrcCount(),
            columnarMetrics.getOrcSize(),
            columnarMetrics.getDelCount(),
            columnarMetrics.getDelSize(),
            columnarMetrics.getCsvCount(),
            columnarMetrics.getCsvSize(),
            columnarMetrics.getCompactionTaskCount(),
            columnarMetrics.getCompactionFileSize(),
            columnarMetrics.getDataConsistencyLockCompaction(),
            columnarMetrics.getCompactionReadOssBandWidth(),
            columnarMetrics.getCompactionWriteOssBandWidth(),
            columnarMetrics.getPkIdxWriteCount(),
            columnarMetrics.getPkIdxReadCount(),
            columnarMetrics.getPkIdxLogBytes(),
            columnarMetrics.getPkIdxSstReadCount(),
            columnarMetrics.getPkIdxSstMemGetCount(),
            columnarMetrics.getPkIdxSstMemMissCount(),
            columnarMetrics.getPkIdxLocalReadCount(),
            columnarMetrics.getPkIdxLocalReadBytes(),
            columnarMetrics.getPkIdxRemoteReadCount(),
            columnarMetrics.getPkIdxRemoteReadBytes(),
            columnarMetrics.getPkIdxSstWriteCount(),
            columnarMetrics.getPkIdxSstWriteBytes(),
            columnarMetrics.getPkIdxLogFixCount(),
            columnarMetrics.getBinlogNetThroughput(),
            columnarMetrics.getBinlogEventThroughput(),
            columnarMetrics.getBinlogEventInsert(),
            columnarMetrics.getBinlogEventDelete(),
            columnarMetrics.getBinlogEventUpdate(),
            columnarMetrics.getBinlogEventDDL(),
            columnarMetrics.getBinlogEventInsertRows(),
            columnarMetrics.getBinlogEventDeleteRows(),
            columnarMetrics.getBinlogEventUpdateRows(),
            columnarMetrics.getBinlogTrxCount(),
            columnarMetrics.getBinlogReadLatency());
        sb.append(columnarFormat);
    }

    static class MetricsSnapshot {
        MetricsSnapshot(long seq) {
            this.seq = seq;
        }

        long seq;
        long timestamp;
        JvmSnapshot jvmSnapshot;
        ProcSnapshot procSnapshot;
        ColumnarMetrics columnarMetrics;
    }
}
