/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.metrics;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumClientType;
import com.aliyun.polardbx.binlog.jvm.JvmSnapshot;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.metrics.format.TableFormat;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.monitor.MonitorValue;
import com.aliyun.polardbx.binlog.proc.ProcSnapshot;
import com.aliyun.polardbx.binlog.proc.ProcUtils;
import com.aliyun.polardbx.binlog.util.CommonMetricsHelper;
import com.aliyun.polardbx.binlog.util.MetricsReporter;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.hyperic.sigar.CpuPerc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.aliyun.polardbx.binlog.ConfigKeys.ALARM_DELAY_THRESHOLD_SECOND;
import static com.aliyun.polardbx.binlog.ConfigKeys.ALARM_NODATA_THRESHOLD_SECOND;
import static com.aliyun.polardbx.binlog.ConfigKeys.PRINT_METRICS;
import static com.aliyun.polardbx.binlog.util.CommonMetricsHelper.addJvmMetrics;
import static com.aliyun.polardbx.binlog.util.CommonMetricsHelper.addProcMetrics;

/**
 * Created by ziyang.lb
 **/
@Slf4j
public class MetricsManager {

    private static final Logger METRICS_LOGGER = LoggerFactory.getLogger("METRICS");
    private static final long INTERVAL = TimeUnit.SECONDS.toMillis(5);

    private final TaskType taskType;
    private final ScheduledExecutorService scheduledExecutorService;
    private final AtomicLong snapshotSeq = new AtomicLong(0);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final boolean leader;
    private MetricsSnapshot lastSnapshot;
    private long startTime;

    private final Map<String, DumpClientMetric> dumpClientMetricsMap = new ConcurrentHashMap<>();

    public MetricsManager(long version, String taskName, TaskType taskType) {
        this.taskType = taskType;
        this.leader = RuntimeLeaderElector.isDumperMasterOrX(version, taskType, taskName);
        this.scheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "dumper-metrics-manager");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        if (running.compareAndSet(false, true)) {
            startTime = System.currentTimeMillis();
            scheduledExecutorService.scheduleAtFixedRate(() -> {
                try {
                    MetricsSnapshot snapshot = buildSnapshot();
                    if (DynamicApplicationConfig.getBoolean(PRINT_METRICS)) {
                        print(snapshot);
                    }

                    for (DumpClientMetric metric : dumpClientMetricsMap.values()) {
                        if (metric.getClientType() != EnumClientType.COLUMNAR) {
                            reportConsumerExists();
                            break;
                        }
                    }

                    sendMetrics(snapshot);
                    tryAlarm(snapshot);
                    lastSnapshot = snapshot;
                } catch (Throwable e) {
                    METRICS_LOGGER.error("metrics print error!", e);
                }
            }, INTERVAL, INTERVAL, TimeUnit.MILLISECONDS);
            METRICS_LOGGER.info("metrics manager started.");
        }
    }

    public void stop() {
        if (running.compareAndSet(true, false)) {
            scheduledExecutorService.shutdownNow();
            METRICS_LOGGER.info("metrics manager stopped.");
        }
    }

    private void tryAlarm(MetricsSnapshot snapshot) {
        for (StreamMetrics metrics : snapshot.streamMetrics.values()) {
            tryAlarmNoData(metrics);
            tryAlarmDelay(metrics);
        }
    }

    private void tryAlarmNoData(StreamMetrics streamMetrics) {
        boolean doAlarm;
        long noDataTime;

        int threshold = DynamicApplicationConfig.getInt(ALARM_NODATA_THRESHOLD_SECOND);
        if (streamMetrics.getLatestDataReceiveTime() == 0L) {
            noDataTime = System.currentTimeMillis() - startTime;
            doAlarm = noDataTime > threshold * 2 * 1000;
        } else {
            noDataTime = System.currentTimeMillis() - streamMetrics.getLatestDataReceiveTime();
            doAlarm = noDataTime > threshold * 1000;
        }

        if (doAlarm) {
            if (leader) {
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.DUMPER_STAGE_LEADER_NODATA_ERROR, new MonitorValue(noDataTime / 1000),
                        noDataTime / 1000);
            } else {
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.DUMPER_STAGE_FOLLOWER_NODATA_ERROR, new MonitorValue(noDataTime / 1000),
                        noDataTime / 1000);
            }

            log.info("send a no data alarm : {}s", noDataTime / 1000);
        }
    }

    private void tryAlarmDelay(StreamMetrics streamMetrics) {
        int threshold = DynamicApplicationConfig.getInt(ALARM_DELAY_THRESHOLD_SECOND) * 1000;
        long delayTime = streamMetrics.getLatestDelayTimeOnCommit();
        if (delayTime > threshold) {
            if (leader) {
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.DUMPER_STAGE_LEADER_DELAY, new MonitorValue(delayTime), delayTime);
            } else {
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.DUMPER_STAGE_FOLLOWER_DELAY, new MonitorValue(delayTime), delayTime);
            }
        }
    }

    private void print(MetricsSnapshot snapshot) {

        StringBuilder sb = new StringBuilder();
        sb.append("\r\n");
        sb.append(
            "######################################################## dumper metrics begin #####################################################");
        sb.append("\r\n");

        contactStreamTotalMetrics(snapshot, sb);
        contactStreamAvgMetrics(snapshot, sb);
        contactStreamInstantMetrics(snapshot, sb);
        contactJvmMetrics(snapshot, sb);
        contactProcMetrics(snapshot, sb);
        contactDumpClientMetrics(sb);
        sb.append("\r\n");
        sb.append(
            "######################################################## dumper metrics end ########################################################");
        sb.append("\r\n");

        METRICS_LOGGER.info(sb.toString());
    }

    private void contactDumpClientMetrics(StringBuilder sb) {
        if (dumpClientMetricsMap.isEmpty()) {
            return;
        }
        TableFormat dumperClientFormat = new TableFormat("Dumper Client Metrics");
        dumperClientFormat.addColumn(
            "ip",
            "port",
            "fileName",
            "position",
            "delay(s)",
            "bps",
            "lastSyncTimestamp",
            "alive(s)"
        );
        long now = System.currentTimeMillis();
        for (DumpClientMetric metric : dumpClientMetricsMap.values()) {
            long delay = TimeUnit.MILLISECONDS.toSeconds(now) - metric.getTimestamp();
            long alive = TimeUnit.MILLISECONDS.toSeconds(now - metric.getDumpStartTimestamp());
            dumperClientFormat.addRow(
                metric.getRemoteIp(),
                metric.getRemotePort(),
                metric.getFileName(),
                metric.getPosition(),
                delay,
                metric.getDumpBps(),
                DateFormatUtils.format(metric.getLastSyncTimestamp(), "yyyy-MM-dd HH:mm:ss"),
                alive
            );
        }
        sb.append(dumperClientFormat);
    }

    private void contactStreamTotalMetrics(MetricsSnapshot snapshot, StringBuilder sb) {
        if (snapshot.streamMetrics.isEmpty()) {
            return;
        }
        TableFormat streamTotalFormatInfo = new TableFormat("Stream Total Metrics");
        streamTotalFormatInfo.addColumn(
            "streamId",
            "revEventCnt",
            "revEventBytes",
            "writeEventCnt",
            "wrDmlCnt",
            "wrDmlCnt(M)",
            "wrDmlCnt(I)",
            "wrDmlCnt(U)",
            "wrDmlCnt(D)",
            "writeEventBytes",
            "writeTxnCnt",
            "writeFlushCnt");
        for (StreamMetrics metrics : snapshot.streamMetrics.values()) {
            streamTotalFormatInfo.addRow(
                metrics.getStreamId(),
                metrics.getTotalRevEventCount(),
                metrics.getTotalRevEventBytes(),
                metrics.getTotalWriteEventCount(),
                metrics.getTotalWriteDmlEventCount(),
                metrics.getTotalWriteDmlTabMapEventCount(),
                metrics.getTotalWriteDmlInsertEventCount(),
                metrics.getTotalWriteDmlUpdateEventCount(),
                metrics.getTotalWriteDmlDeleteEventCount(),
                metrics.getTotalWriteEventBytes(),
                metrics.getTotalWriteTxnCount(),
                metrics.getTotalWriteFlushCount());
        }
        sb.append(streamTotalFormatInfo);
    }

    private void contactStreamAvgMetrics(MetricsSnapshot snapshot, StringBuilder sb) {
        if (snapshot.periodAverage.isEmpty()) {
            return;
        }
        TableFormat streamAvgFormatInfo =
            new TableFormat("Stream Average Metrics (wt = write time ; PT = Per Txn ; PE = Per Event)");
        streamAvgFormatInfo.addColumn(
            "streamId",
            "revEps",
            "revBps",
            "writeEps",
            "wrDmlEps",
            "wrDmlEps(M)",
            "wrDmlEps(I)",
            "wrDmlEps(U)",
            "wrDmlEps(D)",
            "writeTps",
            "writeBps",
            "uploadBps",
            "dumpBps",
            "wtPT(ms)",
            "wtPE(ms)");
        for (StreamMetricsAverage metrics : snapshot.periodAverage.values()) {
            streamAvgFormatInfo.addRow(
                metrics.streamId,
                metrics.avgRevEps,
                metrics.avgRevBps,
                metrics.avgWriteEps,
                metrics.avgWriteDmlEps,
                metrics.avgWriteDmlTabMapEps,
                metrics.avgWriteDmlInsertEps,
                metrics.avgWriteDmlUpdateEps,
                metrics.avgWriteDmlDeleteEps,
                metrics.avgWriteTps,
                metrics.avgWriteBps,
                metrics.avgUploadBps,
                metrics.avgDumpBps,
                String.format("%.2f", metrics.avgWriteTimePerTxn),
                String.format("%.2f", metrics.avgWriteTimePerEvent));
        }
        sb.append(streamAvgFormatInfo);
    }

    private void contactStreamInstantMetrics(MetricsSnapshot snapshot, StringBuilder sb) {
        if (snapshot.streamMetrics.isEmpty()) {
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        TableFormat streamTotalFormatInfo = new TableFormat("Stream Instant Metrics");
        streamTotalFormatInfo.addColumn(
            "streamId",
            "delay(ms)",
            "latestRevTime",
            "latestTsoTime",
            "latestBinlogFile",
            "revQueueSize",
            "kwaySourceQueueSize",
            "writeQueueSize");
        for (StreamMetrics metrics : snapshot.streamMetrics.values()) {
            streamTotalFormatInfo.addRow(
                metrics.getStreamId(),
                metrics.getLatestDelayTimeOnCommit(),
                sdf.format(new Date(metrics.getLatestDataReceiveTime())),
                sdf.format(new Date(metrics.getLatestTsoTime())),
                metrics.getLatestBinlogFile(),
                metrics.getReceiveQueueSize(),
                new TreeMap<>(metrics.getKwaySourceQueueSizeSupplier().get()),
                metrics.getWriteQueueSize());
        }
        sb.append(streamTotalFormatInfo);
    }

    private void contactJvmMetrics(MetricsSnapshot snapshot, StringBuilder sb) {
        TableFormat jvmFormatInfo = new TableFormat("Jvm Metrics");
        jvmFormatInfo.addColumn(
            "youngUsed",
            "youngMax",
            "youngCollectionCount",
            "youngCollectionTime(ms)",
            "oldUsed",
            "oldMax",
            "oldCollectionCount",
            "oldCollectionTime(ms)");
        jvmFormatInfo.addRow(
            snapshot.jvmSnapshot.getYoungUsed(),
            snapshot.jvmSnapshot.getYoungMax(),
            snapshot.jvmSnapshot.getYoungCollectionCount(),
            snapshot.jvmSnapshot.getYoungCollectionTime(),
            snapshot.jvmSnapshot.getOldUsed(),
            snapshot.jvmSnapshot.getOldMax(),
            snapshot.jvmSnapshot.getOldCollectionCount(),
            snapshot.jvmSnapshot.getOldCollectionTime());
        sb.append(jvmFormatInfo);
    }

    private void contactProcMetrics(MetricsSnapshot snapshot, StringBuilder sb) {
        if (snapshot.procSnapshot == null) {
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        TableFormat osFormatInfo = new TableFormat("Proc Metrics");
        osFormatInfo.addColumn(
            "pid",
            "startTime",
            "cpuPercent",
            "cpuTotal",
            "cpuUser",
            "cpuSys",
            "memSize",
            "fdNum");
        osFormatInfo.addRow(
            snapshot.procSnapshot.getPid(),
            sdf.format(new Date(snapshot.procSnapshot.getStartTime())),
            CpuPerc.format(snapshot.procSnapshot.getCpuPercent()),
            snapshot.procSnapshot.getCpuTotal(),
            snapshot.procSnapshot.getCpuUser(),
            snapshot.procSnapshot.getCpuSys(),
            snapshot.procSnapshot.getMemSize(),
            snapshot.procSnapshot.getFdNum());
        sb.append(osFormatInfo);
    }

    private void sendMetrics(MetricsSnapshot snapshot) {
        if (taskType == TaskType.Dumper) {
            // 只会有一个流
            for (StreamMetrics metrics : snapshot.streamMetrics.values()) {
                sendMetrics4Dumper(metrics, snapshot.periodAverage.get(metrics.getStreamId()), snapshot.jvmSnapshot,
                    snapshot.procSnapshot);
            }
        } else {
            DumperXMetrics dumperXMetrics = buildDumperXMetrics(snapshot);
            sendMetrics4DumperX(dumperXMetrics, snapshot.jvmSnapshot, snapshot.procSnapshot);
        }
    }

    @SneakyThrows
    private void sendMetrics4DumperX(DumperXMetrics dumperXMetrics, JvmSnapshot jvmSnapshot,
                                     ProcSnapshot procSnapshot) {
        List<CommonMetrics> commonMetrics = Lists.newArrayList();
        Field[] metricsFields = DumperXMetrics.class.getDeclaredFields();
        for (Field f : metricsFields) {
            CommonMetrics x1 = CommonMetricsHelper.getDumperX().get(f.getName());
            if (x1 != null) {
                f.setAccessible(true);
                if (dumperXMetrics != null) {
                    Object o = f.get(dumperXMetrics);
                    if (o != null) {
                        commonMetrics.add(x1.var(o));
                    }
                }
            }
        }
        if (jvmSnapshot != null) {
            String prefix = "polardbx_cdc_dumper_x_";
            addJvmMetrics(commonMetrics, jvmSnapshot, prefix);
        }

        if (procSnapshot != null) {
            addProcMetrics(commonMetrics, procSnapshot, "polardbx_cdc_dumper_x_");
        }

        if (!CollectionUtils.isEmpty(commonMetrics)) {
            MetricsReporter.report(commonMetrics);
        }
    }

    @SneakyThrows
    private void sendMetrics4Dumper(StreamMetrics metrics, StreamMetricsAverage average, JvmSnapshot jvmSnapshot,
                                    ProcSnapshot procSnapshot) {
        List<CommonMetrics> commonMetrics = Lists.newArrayList();
        Field[] metricsFields = StreamMetrics.class.getDeclaredFields();
        for (Field f : metricsFields) {
            //历史原因，导致规划的不合理，其实没必要区分m和s，只需要dumper就好了
            CommonMetrics x1 = leader ? CommonMetricsHelper.getDumperM().get(f.getName())
                : CommonMetricsHelper.getDumperS().get(f.getName());
            if (x1 != null) {
                f.setAccessible(true);
                if (metrics != null) {
                    Object o = f.get(metrics);
                    if (o != null) {
                        commonMetrics.add(x1.var(o));
                    }
                }
            }
            CommonMetrics x2 = CommonMetricsHelper.getDumper().get(f.getName());
            if (x2 != null) {
                f.setAccessible(true);
                if (metrics != null) {
                    Object o = f.get(metrics);
                    if (o != null) {
                        commonMetrics.add(x2.var(o));
                    }
                }
            }
        }

        Field[] averageFields = StreamMetricsAverage.class.getDeclaredFields();
        for (Field f : averageFields) {
            CommonMetrics x1 = leader ? CommonMetricsHelper.getDumperM().get(f.getName())
                : CommonMetricsHelper.getDumperS().get(f.getName());
            if (x1 != null) {
                f.setAccessible(true);
                if (average != null) {
                    Object o = f.get(average);
                    if (o != null) {
                        commonMetrics.add(x1.var(o));
                    }
                }
            }
            CommonMetrics x2 = CommonMetricsHelper.getDumper().get(f.getName());
            if (x2 != null) {
                f.setAccessible(true);
                if (average != null) {
                    Object o = f.get(average);
                    if (o != null) {
                        commonMetrics.add(x2.var(o));
                    }
                }
            }
        }

        if (jvmSnapshot != null) {
            String prefix = leader ? "polardbx_cdc_dumper_m_" : "polardbx_cdc_dumper_s_";
            addJvmMetrics(commonMetrics, jvmSnapshot, prefix);
        }

        if (procSnapshot != null) {
            addProcMetrics(commonMetrics, procSnapshot, "polardbx_cdc_dumper_");
        }

        if (!CollectionUtils.isEmpty(commonMetrics)) {
            MetricsReporter.report(commonMetrics);
        }
    }

    private MetricsSnapshot buildSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot(snapshotSeq.incrementAndGet());
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.jvmSnapshot = JvmUtils.buildJvmSnapshot();
        snapshot.procSnapshot = ProcUtils.buildProcSnapshot();
        snapshot.streamMetrics = new HashMap<>();
        for (StreamMetrics metrics : StreamMetrics.getMetricsMap().values()) {
            snapshot.streamMetrics.put(metrics.getStreamId(), metrics.snapshot());
        }

        snapshot.periodAverage = buildPeriodAverage(snapshot);
        return snapshot;
    }

    private Map<String, StreamMetricsAverage> buildPeriodAverage(MetricsSnapshot snapshot) {
        Map<String, StreamMetricsAverage> result = new HashMap<>();
        for (StreamMetrics latestMetrics : snapshot.streamMetrics.values()) {
            StreamMetricsAverage periodAverage = new StreamMetricsAverage();
            long currentTime = snapshot.timestamp;

            long period;
            long periodRevEventCount;
            long periodRevEventBytes;
            long periodWriteEventCount;
            long periodWriteDmlEventCount;
            long periodWriteDmlTabMapEventCount;
            long periodWriteDmlInsertEventCount;
            long periodWriteDmlUpdateEventCount;
            long periodWriteDmlDeleteEventCount;
            long periodWriteTxnCount;
            long periodWriteEventBytes;
            double periodWriteTxnTime;
            long periodUploadBytes;
            long periodDumpBytes;

            if (lastSnapshot == null) {
                period = (currentTime - startTime) / 1000;
                periodRevEventCount = latestMetrics.getTotalRevEventCount();
                periodRevEventBytes = latestMetrics.getTotalRevEventBytes();
                periodWriteEventCount = latestMetrics.getTotalWriteEventCount();
                periodWriteDmlEventCount = latestMetrics.getTotalWriteDmlEventCount();
                periodWriteDmlTabMapEventCount = latestMetrics.getTotalWriteDmlTabMapEventCount();
                periodWriteDmlInsertEventCount = latestMetrics.getTotalWriteDmlInsertEventCount();
                periodWriteDmlUpdateEventCount = latestMetrics.getTotalWriteDmlUpdateEventCount();
                periodWriteDmlDeleteEventCount = latestMetrics.getTotalWriteDmlDeleteEventCount();
                periodWriteTxnCount = latestMetrics.getTotalWriteTxnCount();
                periodWriteEventBytes = latestMetrics.getTotalWriteEventBytes();
                periodWriteTxnTime = (double) (latestMetrics.getTotalWriteTxnTime());
                periodUploadBytes = latestMetrics.getTotalUploadBytes();
                periodDumpBytes = latestMetrics.getTotalDumpBytes();
            } else {
                period = (currentTime - lastSnapshot.timestamp) / 1000;
                periodRevEventCount = latestMetrics.getTotalRevEventCount() - lastSnapshot.streamMetrics
                    .get(latestMetrics.getStreamId()).getTotalRevEventCount();
                periodRevEventBytes = latestMetrics.getTotalRevEventBytes() - lastSnapshot.streamMetrics
                    .get(latestMetrics.getStreamId()).getTotalRevEventBytes();
                periodWriteEventCount = latestMetrics.getTotalWriteEventCount() - lastSnapshot.streamMetrics
                    .get(latestMetrics.getStreamId()).getTotalWriteEventCount();
                periodWriteDmlEventCount = latestMetrics.getTotalWriteDmlEventCount() - lastSnapshot.streamMetrics
                    .get(latestMetrics.getStreamId()).getTotalWriteDmlEventCount();
                periodWriteDmlTabMapEventCount = latestMetrics.getTotalWriteDmlTabMapEventCount() - lastSnapshot
                    .streamMetrics.get(latestMetrics.getStreamId()).getTotalWriteDmlTabMapEventCount();
                periodWriteDmlInsertEventCount = latestMetrics.getTotalWriteDmlInsertEventCount() - lastSnapshot
                    .streamMetrics.get(latestMetrics.getStreamId()).getTotalWriteDmlInsertEventCount();
                periodWriteDmlUpdateEventCount = latestMetrics.getTotalWriteDmlUpdateEventCount() - lastSnapshot
                    .streamMetrics.get(latestMetrics.getStreamId()).getTotalWriteDmlUpdateEventCount();
                periodWriteDmlDeleteEventCount = latestMetrics.getTotalWriteDmlDeleteEventCount() - lastSnapshot
                    .streamMetrics.get(latestMetrics.getStreamId()).getTotalWriteDmlDeleteEventCount();
                periodWriteTxnCount = latestMetrics.getTotalWriteTxnCount() - lastSnapshot.streamMetrics
                    .get(latestMetrics.getStreamId()).getTotalWriteTxnCount();
                periodWriteEventBytes = latestMetrics.getTotalWriteEventBytes() - lastSnapshot.streamMetrics
                    .get(latestMetrics.getStreamId()).getTotalWriteEventBytes();
                periodWriteTxnTime = (double) (latestMetrics.getTotalWriteTxnTime() - lastSnapshot.streamMetrics
                    .get(latestMetrics.getStreamId()).getTotalWriteTxnTime());
                periodUploadBytes =
                    latestMetrics.getTotalUploadBytes() - lastSnapshot.streamMetrics.get(latestMetrics.getStreamId())
                        .getTotalUploadBytes();
                periodDumpBytes =
                    latestMetrics.getTotalDumpBytes() - lastSnapshot.streamMetrics.get(latestMetrics.getStreamId())
                        .getTotalDumpBytes();
            }

            periodAverage.streamId = latestMetrics.getStreamId();
            periodAverage.avgRevEps = periodRevEventCount / period;
            periodAverage.avgRevBps = periodRevEventBytes / period;
            periodAverage.avgWriteTimePerTxn = periodWriteTxnCount == 0 ? BigDecimal.ZERO :
                new BigDecimal(periodWriteTxnTime).divide(new BigDecimal(periodWriteTxnCount), 2,
                    BigDecimal.ROUND_HALF_UP);
            periodAverage.avgWriteTimePerEvent = periodWriteEventCount == 0 ? BigDecimal.ZERO :
                new BigDecimal(periodWriteTxnTime).divide(new BigDecimal(periodWriteEventCount), 2,
                    BigDecimal.ROUND_HALF_UP);
            periodAverage.avgWriteEps = periodWriteEventCount / period;
            periodAverage.avgWriteDmlEps = periodWriteDmlEventCount / period;
            periodAverage.avgWriteDmlTabMapEps = periodWriteDmlTabMapEventCount / period;
            periodAverage.avgWriteDmlInsertEps = periodWriteDmlInsertEventCount / period;
            periodAverage.avgWriteDmlUpdateEps = periodWriteDmlUpdateEventCount / period;
            periodAverage.avgWriteDmlDeleteEps = periodWriteDmlDeleteEventCount / period;
            periodAverage.avgWriteBps = periodWriteEventBytes / period;
            periodAverage.avgWriteTps = periodWriteTxnCount / period;
            periodAverage.avgUploadBps = periodUploadBytes / period;
            periodAverage.avgDumpBps = periodDumpBytes / period;

            result.put(latestMetrics.getStreamId(), periodAverage);
        }
        return result;
    }

    private DumperXMetrics buildDumperXMetrics(MetricsSnapshot snapshot) {
        DumperXMetrics xMetrics = new DumperXMetrics();
        xMetrics.avgDelayTime = Double.valueOf(snapshot.streamMetrics.values().stream()
            .mapToLong(StreamMetrics::getLatestDelayTimeOnCommit).average().orElse(0)).longValue();
        xMetrics.maxDelayTime = snapshot.streamMetrics.values().stream()
            .mapToLong(StreamMetrics::getLatestDelayTimeOnCommit).max().orElse(0);
        xMetrics.minDelayTime = snapshot.streamMetrics.values().stream()
            .mapToLong(StreamMetrics::getLatestDelayTimeOnCommit).min().orElse(0);

        xMetrics.avgWriteEps = Double.valueOf(snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteEps).average().orElse(0)).longValue();
        xMetrics.maxWriteEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteEps).max().orElse(0);
        xMetrics.minWriteEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteEps).min().orElse(0);
        xMetrics.sumWriteEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteEps).sum();

        xMetrics.avgWriteDmlEps = Double.valueOf(snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlEps).average().orElse(0)).longValue();
        xMetrics.maxWriteDmlEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlEps).max().orElse(0);
        xMetrics.minWriteDmlEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlEps).min().orElse(0);
        xMetrics.sumWriteDmlEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlEps).sum();

        xMetrics.avgWriteDmlTabMapEps = Double.valueOf(snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlTabMapEps).average().orElse(0)).longValue();
        xMetrics.maxWriteDmlTabMapEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlTabMapEps).max().orElse(0);
        xMetrics.minWriteDmlTabMapEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlTabMapEps).min().orElse(0);
        xMetrics.sumWriteDmlTabMapEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlTabMapEps).sum();

        xMetrics.avgWriteDmlInsertEps = Double.valueOf(snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlInsertEps).average().orElse(0)).longValue();
        xMetrics.maxWriteDmlInsertEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlInsertEps).max().orElse(0);
        xMetrics.minWriteDmlInsertEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlInsertEps).min().orElse(0);
        xMetrics.sumWriteDmlInsertEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlInsertEps).sum();

        xMetrics.avgWriteDmlUpdateEps = Double.valueOf(snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlUpdateEps).average().orElse(0)).longValue();
        xMetrics.maxWriteDmlUpdateEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlUpdateEps).max().orElse(0);
        xMetrics.minWriteDmlUpdateEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlUpdateEps).min().orElse(0);
        xMetrics.sumWriteDmlUpdateEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlUpdateEps).sum();

        xMetrics.avgWriteDmlDeleteEps = Double.valueOf(snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlDeleteEps).average().orElse(0)).longValue();
        xMetrics.maxWriteDmlDeleteEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlDeleteEps).max().orElse(0);
        xMetrics.minWriteDmlDeleteEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlDeleteEps).min().orElse(0);
        xMetrics.sumWriteDmlDeleteEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteDmlDeleteEps).sum();

        xMetrics.avgRevEps = Double.valueOf(snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgRevEps).average().orElse(0)).longValue();
        xMetrics.maxRevEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgRevEps).max().orElse(0);
        xMetrics.minRevEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgRevEps).min().orElse(0);
        xMetrics.sumRevEps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgRevEps).sum();

        xMetrics.avgWriteTps = Double.valueOf(snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteTps).average().orElse(0)).longValue();
        xMetrics.maxWriteTps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteTps).max().orElse(0);
        xMetrics.minWriteTps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteTps).min().orElse(0);
        xMetrics.sumWriteTps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteTps).sum();

        xMetrics.avgWriteBps = Double.valueOf(snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteBps).average().orElse(0)).longValue();
        xMetrics.maxWriteBps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteBps).max().orElse(0);
        xMetrics.minWriteBps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteBps).min().orElse(0);
        xMetrics.sumWriteBps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgWriteBps).sum();

        xMetrics.avgWriteTimePerEvent = BigDecimal.valueOf(snapshot.periodAverage.values().stream()
                .mapToDouble(s -> s.avgWriteTimePerEvent.doubleValue()).average().orElse(0))
            .setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
        xMetrics.maxWriteTimePerEvent = snapshot.periodAverage.values().stream()
            .mapToDouble(s -> s.avgWriteTimePerEvent.doubleValue()).max().orElse(0);
        xMetrics.minWriteTimePerEvent = snapshot.periodAverage.values().stream()
            .mapToDouble(s -> s.avgWriteTimePerEvent.doubleValue()).min().orElse(0);

        xMetrics.avgWriteTimePerTxn = BigDecimal.valueOf(snapshot.periodAverage.values().stream()
                .mapToDouble(s -> s.avgWriteTimePerTxn.doubleValue()).average().orElse(0))
            .setScale(3, BigDecimal.ROUND_HALF_UP).doubleValue();
        xMetrics.maxWriteTimePerTxn = snapshot.periodAverage.values().stream()
            .mapToDouble(s -> s.avgWriteTimePerTxn.doubleValue()).max().orElse(0);
        xMetrics.minWriteTimePerTxn = snapshot.periodAverage.values().stream()
            .mapToDouble(s -> s.avgWriteTimePerTxn.doubleValue()).min().orElse(0);

        xMetrics.avgUploadBps = Double.valueOf(snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgUploadBps).average().orElse(0)).longValue();
        xMetrics.maxUploadBps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgUploadBps).max().orElse(0);
        xMetrics.minUploadBps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgUploadBps).min().orElse(0);
        xMetrics.sumUploadBps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgUploadBps).sum();

        xMetrics.avgDumpBps = Double.valueOf(snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgDumpBps).average().orElse(0)).longValue();
        xMetrics.maxDumpBps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgDumpBps).max().orElse(0);
        xMetrics.minDumpBps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgDumpBps).min().orElse(0);
        xMetrics.sumDumpBps = snapshot.periodAverage.values().stream()
            .mapToLong(s -> s.avgDumpBps).sum();

        xMetrics.avgWriteQueueSize = Double.valueOf(snapshot.streamMetrics.values().stream()
            .mapToLong(StreamMetrics::getWriteQueueSize).average().orElse(0)).longValue();
        xMetrics.maxWriteQueueSize = snapshot.streamMetrics.values().stream()
            .mapToLong(StreamMetrics::getWriteQueueSize).max().orElse(0);
        xMetrics.minWriteQueueSize = snapshot.streamMetrics.values().stream()
            .mapToLong(StreamMetrics::getWriteQueueSize).min().orElse(0);
        xMetrics.sumWriteQueueSize = snapshot.streamMetrics.values().stream()
            .mapToLong(StreamMetrics::getWriteQueueSize).sum();

        return xMetrics;
    }

    static class MetricsSnapshot {
        MetricsSnapshot(long seq) {
            this.seq = seq;
        }

        long seq;
        long timestamp;
        Map<String, StreamMetrics> streamMetrics;
        Map<String, StreamMetricsAverage> periodAverage;
        JvmSnapshot jvmSnapshot;
        ProcSnapshot procSnapshot;
    }

    @Data
    public static class StreamMetricsAverage {
        String streamId;
        /**
         * 从Task接收到的事件写入binlog文件的平均tps(按个数)
         */
        long avgRevEps;
        /**
         * 从Task接收到的事件写入binlog文件的平均tps(按字节数)
         */
        long avgRevBps;
        /**
         * 平均每秒写入binlog文件的字节数
         */
        long avgWriteBps;
        /**
         * 事务写入binlog文件的平均tps
         */
        long avgWriteTps;
        /**
         * 所有事件写入binlog文件的平均tps
         */
        long avgWriteEps;
        long avgWriteDmlEps;
        long avgWriteDmlTabMapEps;
        long avgWriteDmlInsertEps;
        long avgWriteDmlUpdateEps;
        long avgWriteDmlDeleteEps;
        /**
         * 完成一个事务写入的平均耗时
         */
        BigDecimal avgWriteTimePerTxn;
        /**
         * 完成一个Event写入的平均耗时
         */
        BigDecimal avgWriteTimePerEvent;
        /**
         * 平均每秒上传到远端存储的字节数
         */
        long avgUploadBps;
        /**
         * 平均每秒通过mysql dump发送的字节数
         */
        long avgDumpBps;
    }

    static class DumperXMetrics {
        long avgDelayTime;
        long maxDelayTime;
        long minDelayTime;

        long avgWriteEps;
        long maxWriteEps;
        long minWriteEps;
        long sumWriteEps;

        long avgWriteDmlEps;
        long maxWriteDmlEps;
        long minWriteDmlEps;
        long sumWriteDmlEps;

        long avgWriteDmlTabMapEps;
        long maxWriteDmlTabMapEps;
        long minWriteDmlTabMapEps;
        long sumWriteDmlTabMapEps;

        long avgWriteDmlInsertEps;
        long maxWriteDmlInsertEps;
        long minWriteDmlInsertEps;
        long sumWriteDmlInsertEps;

        long avgWriteDmlUpdateEps;
        long maxWriteDmlUpdateEps;
        long minWriteDmlUpdateEps;
        long sumWriteDmlUpdateEps;

        long avgWriteDmlDeleteEps;
        long maxWriteDmlDeleteEps;
        long minWriteDmlDeleteEps;
        long sumWriteDmlDeleteEps;

        long avgRevEps;
        long maxRevEps;
        long minRevEps;
        long sumRevEps;

        long avgWriteTps;
        long maxWriteTps;
        long minWriteTps;
        long sumWriteTps;

        long avgWriteBps;
        long maxWriteBps;
        long minWriteBps;
        long sumWriteBps;

        double avgWriteTimePerEvent;
        double maxWriteTimePerEvent;
        double minWriteTimePerEvent;

        double avgWriteTimePerTxn;
        double maxWriteTimePerTxn;
        double minWriteTimePerTxn;

        long avgUploadBps;
        long maxUploadBps;
        long minUploadBps;
        long sumUploadBps;

        long avgDumpBps;
        long maxDumpBps;
        long minDumpBps;
        long sumDumpBps;

        long avgWriteQueueSize;
        long maxWriteQueueSize;
        long minWriteQueueSize;
        long sumWriteQueueSize;
    }

    public void addClientMetric(DumpClientMetric metric) {
        this.dumpClientMetricsMap.put(metric.getDestination(), metric);
    }

    public void removeClientMetric(DumpClientMetric metric) {
        this.dumpClientMetricsMap.remove(metric.getDestination());
    }

    public StreamMetricsAverage getStreamMetricsAvg(String streamId) {
        return lastSnapshot.periodAverage.get(streamId);
    }

    public StreamMetrics getStreamMetrics(String streamId) {
        return lastSnapshot.streamMetrics.get(streamId);
    }

    private void reportConsumerExists() {
        String time = Long.valueOf(System.currentTimeMillis()).toString();
        log.info("Report Consumer Exists: latest consume time : {}", time);
        DynamicApplicationConfig.setValue(ConfigKeys.ALARM_LATEST_CONSUME_TIME_MS, time);
    }
}
