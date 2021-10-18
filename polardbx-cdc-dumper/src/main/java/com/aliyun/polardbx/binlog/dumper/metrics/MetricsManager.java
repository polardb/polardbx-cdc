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

package com.aliyun.polardbx.binlog.dumper.metrics;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.jvm.JvmSnapshot;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.monitor.MonitorValue;
import com.aliyun.polardbx.binlog.util.CommonMetricsHelper;
import com.aliyun.polardbx.binlog.util.MetricsReporter;
import com.google.common.collect.Lists;
import lombok.SneakyThrows;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.aliyun.polardbx.binlog.ConfigKeys.ALARM_DELAY_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.ALARM_NODATA_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.PRINT_METRICS;

/**
 * Created by ziyang.lb
 **/
public class MetricsManager {

    private static final Logger logger = LoggerFactory.getLogger("METRICS");
    private static final long INTERVAL = TimeUnit.SECONDS.toMillis(5);

    private final ScheduledExecutorService scheduledExecutorService;
    private final AtomicLong snapshotSeq = new AtomicLong(0);
    private MetricsSnapshot lastSnapshot;
    private long startTime;
    private volatile boolean running;
    private boolean leader;

    public MetricsManager(boolean leader) {
        this.leader = leader;
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "dumper-metrics-manager");
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
                    metrics(snapshot);
                }

                tryAlarm(snapshot);
                lastSnapshot = snapshot;
            } catch (Throwable e) {
                logger.error("metrics print error!", e);
            }
        }, INTERVAL, INTERVAL, TimeUnit.MILLISECONDS);
        logger.info("metrics manager started.");
    }

    public void stop() {
        if (!running) {
            return;
        }
        running = false;

        scheduledExecutorService.shutdownNow();
        logger.info("metrics manager stopped.");
    }

    private void tryAlarm(MetricsSnapshot snapshot) {
        tryAlarmNoData(snapshot);
        tryAlarmDelay(snapshot);
    }

    private void tryAlarmNoData(MetricsSnapshot snapshot) {
        boolean doAlarm;
        long noDataTime;

        int threshold = DynamicApplicationConfig.getInt(ALARM_NODATA_THRESHOLD);
        if (snapshot.metrics.getLatestDataReceiveTime() == 0L) {
            noDataTime = System.currentTimeMillis() - startTime;
            doAlarm = noDataTime > threshold * 2 * 1000;
        } else {
            noDataTime = System.currentTimeMillis() - snapshot.metrics.getLatestDataReceiveTime();
            doAlarm = noDataTime > threshold * 1000;
        }

        if (doAlarm) {
            logger.warn("trigger no data alarm.");
            if (leader) {
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.DUMPER_STAGE_LEADER_NODATA_ERROR, new MonitorValue(noDataTime / 1000),
                        noDataTime / 1000);
            } else {
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.DUMPER_STAGE_FOLLOWER_NODATA_ERROR, new MonitorValue(noDataTime / 1000),
                        noDataTime / 1000);
            }
        }
    }

    private void tryAlarmDelay(MetricsSnapshot snapshot) {
        int threshold = DynamicApplicationConfig.getInt(ALARM_DELAY_THRESHOLD) * 1000;
        long delayTime = snapshot.metrics.getLatestDelayTimeOnCommit();
        if (delayTime > threshold) {
            logger.warn("trigger delay alarm.");
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
        sb.append("--------------------------------dumper metrics begin------------------------------------");
        sb.append("\r\n");

        sb.append("Total Metrics:");
        sb.append("\r\n");
        sb.append(String.format("totalRevEventCount : %s, totalWriteEventCount : %s,totalWriteTxnCount : %s, \r\n"
                + "totalWriteEventTime(ms): %s, totalWriteTxnTime(ms) : %s, totalWriteEventBytes : %s, \r\n"
                + "totalRevEventBytes : %s, totalFlushWriteCount : %s, totalForceFlushWriteCount : %s ",
            snapshot.metrics.getTotalRevEventCount(),
            snapshot.metrics.getTotalWriteEventCount(),
            snapshot.metrics.getTotalWriteTxnCount(),
            snapshot.metrics.getTotalWriteEventTime(),
            snapshot.metrics.getTotalWriteTxnTime(),
            snapshot.metrics.getTotalWriteEventBytes(),
            snapshot.metrics.getTotalRevEventBytes(),
            snapshot.metrics.getTotalFlushWriteCount(),
            snapshot.metrics.getTotalForceFlushWriteCount()));
        sb.append("\r\n");
        sb.append("\r\n");

        sb.append("Period Average Metrics:");
        sb.append("\r\n");
        sb.append(String
            .format("avgRevEventWriteTps : %s, avgEventWriteTps : %s, avgTxnWriteTps : %s, \r\n"
                    + "avgWriteBytesTps : %s, avgWriteTimePerEvent(ms) : %s, avgWriteTimePerTxn(ms) : %s \r\n"
                    + "avgRevWriteBytesTps : %s ",
                snapshot.periodAverage.avgRevEventWriteTps,
                snapshot.periodAverage.avgEventWriteTps,
                snapshot.periodAverage.avgTxnWriteTps,
                snapshot.periodAverage.avgWriteBytesTps,
                String.format("%.2f", snapshot.periodAverage.avgWriteTimePerEvent),
                String.format("%.2f", snapshot.periodAverage.avgWriteTimePerTxn),
                snapshot.periodAverage.avgRevWriteBytesTps));
        sb.append("\r\n");
        sb.append("\r\n");

        sb.append("Jvm Metrics:");
        sb.append("\r\n");
        sb.append(String
            .format("youngUsed : %s, youngMax : %s, youngCollectionCount : %s, youngCollectionTime : %s, \r\n"
                    + "oldUsed : %s, oldMax : %s, oldCollectionCount : %s, oldCollectionTime : %s",
                snapshot.jvmSnapshot.getYoungUsed(),
                snapshot.jvmSnapshot.getYoungMax(),
                snapshot.jvmSnapshot.getYoungCollectionCount(),
                snapshot.jvmSnapshot.getYoungCollectionTime(),
                snapshot.jvmSnapshot.getOldUsed(),
                snapshot.jvmSnapshot.getOldMax(),
                snapshot.jvmSnapshot.getOldCollectionCount(),
                snapshot.jvmSnapshot.getOldCollectionTime()));
        sb.append("\r\n");
        sb.append("\r\n");

        sb.append("Delay Time Metrics:");
        sb.append("\r\n");
        sb.append(String.format("latestDelayTimeOnReceive(ms) : %s, latestDelayTimeOnCommit(ms) : %s ",
            snapshot.metrics.getLatestDelayTimeOnReceive(),
            snapshot.metrics.getLatestDelayTimeOnCommit()));
        sb.append("\r\n");

        sb.append("--------------------------------dumper metrics end--------------------------------------");
        sb.append("\r\n");

        logger.info(sb.toString());
    }

    @SneakyThrows
    private void metrics(MetricsSnapshot snapshot) {
        List<CommonMetrics> commonMetrics = Lists.newArrayList();
        Metrics metrics = snapshot.metrics;
        Field[] metricsFields = Metrics.class.getDeclaredFields();
        for (Field f : metricsFields) {
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

            CommonMetrics x2 = CommonMetricsHelper.getTask().get(f.getName());
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
        Average average = snapshot.periodAverage;
        Field[] averageFields = Average.class.getDeclaredFields();
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

            CommonMetrics x2 = CommonMetricsHelper.getTask().get(f.getName());
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

        JvmSnapshot jvmSnapshot = snapshot.jvmSnapshot;
        if (jvmSnapshot != null) {
            String prefix = leader ? "polardbx_cdc_dumper_m_" : "polardbx_cdc_dumper_s_";
            commonMetrics
                .add(CommonMetrics.builder().key(prefix + "youngUsed").type(1).value(jvmSnapshot.getYoungUsed())
                    .build());
            commonMetrics.add(CommonMetrics.builder().key(prefix + "youngMax").type(1).value(jvmSnapshot.getYoungMax())
                .build());
            commonMetrics.add(CommonMetrics.builder().key(prefix + "oldUsed").type(1).value(jvmSnapshot.getOldUsed())
                .build());
            commonMetrics.add(CommonMetrics.builder().key(prefix + "oldMax").type(1).value(jvmSnapshot.getOldMax())
                .build());
            commonMetrics.add(CommonMetrics.builder().key(prefix + "heapUsage").type(1).value(
                BigDecimal.valueOf(
                    100 * (jvmSnapshot.getYoungUsed() + jvmSnapshot.getOldUsed() + jvmSnapshot.getMetaUsed()))
                    .divide(
                        BigDecimal
                            .valueOf((jvmSnapshot.getYoungMax() + jvmSnapshot.getOldMax() + jvmSnapshot.getMetaMax())),
                        2,
                        BigDecimal.ROUND_HALF_UP).doubleValue())
                .build());
            commonMetrics.add(CommonMetrics.builder().key(prefix + "youngCollectionCount").type(1)
                .value(jvmSnapshot.getYoungCollectionCount()).build());
            commonMetrics.add(CommonMetrics.builder().key(prefix + "oldCollectionCount").type(1)
                .value(jvmSnapshot.getOldCollectionCount()).build());
            commonMetrics.add(CommonMetrics.builder().key(prefix + "youngCollectionTime").type(1)
                .value(jvmSnapshot.getYoungCollectionTime()).build());
            commonMetrics.add(CommonMetrics.builder().key(prefix + "oldCollectionTime").type(1)
                .value(jvmSnapshot.getOldCollectionTime()).build());
            commonMetrics.add(CommonMetrics.builder().key(prefix + "currentThreadCount").type(1)
                .value(jvmSnapshot.getCurrentThreadCount()).build());
        }

        if (!CollectionUtils.isEmpty(commonMetrics)) {
            MetricsReporter.report(commonMetrics);
        }
    }

    private MetricsSnapshot buildSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot(snapshotSeq.incrementAndGet());
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.metrics = Metrics.get().snapshot();
        snapshot.jvmSnapshot = JvmUtils.buildJvmSnapshot();
        snapshot.periodAverage = buildPeriodAverage(snapshot);
        return snapshot;
    }

    private Average buildPeriodAverage(MetricsSnapshot snapshot) {
        Average periodAverage = new Average();
        Metrics metrics = snapshot.metrics;
        long currentTime = snapshot.timestamp;

        long period;
        long periodWriteEventCount;
        double periodWriteEventTime;
        long periodWriteTxnCount;
        double periodWriteTxnTime;
        long periodRevEventWriteCount;
        long periodWriteEventBytes;
        long periodRevWriteEventBytes;

        if (lastSnapshot == null) {
            period = (currentTime - startTime) / 1000;
            periodWriteEventCount = metrics.getTotalWriteEventCount();
            periodWriteEventTime = (double) (metrics.getTotalWriteEventTime());
            periodWriteTxnCount = metrics.getTotalWriteTxnCount();
            periodWriteTxnTime = (double) (metrics.getTotalWriteTxnTime());
            periodRevEventWriteCount = metrics.getTotalRevEventCount();
            periodWriteEventBytes = metrics.getTotalWriteEventBytes();
            periodRevWriteEventBytes = metrics.getTotalRevEventBytes();
        } else {
            period = (currentTime - lastSnapshot.timestamp) / 1000;
            periodWriteEventCount = metrics.getTotalWriteEventCount() - lastSnapshot.metrics.getTotalWriteEventCount();
            periodWriteEventTime = (double) (metrics.getTotalWriteEventTime()
                - lastSnapshot.metrics.getTotalWriteEventTime());
            periodWriteTxnCount = metrics.getTotalWriteTxnCount() - lastSnapshot.metrics.getTotalWriteTxnCount();
            periodWriteTxnTime = (double) (metrics.getTotalWriteTxnTime()
                - lastSnapshot.metrics.getTotalWriteTxnTime());
            periodRevEventWriteCount = metrics.getTotalRevEventCount() - lastSnapshot.metrics.getTotalRevEventCount();
            periodWriteEventBytes = metrics.getTotalWriteEventBytes() - lastSnapshot.metrics.getTotalWriteEventBytes();
            periodRevWriteEventBytes = metrics.getTotalRevEventBytes() - lastSnapshot.metrics.getTotalRevEventBytes();
        }

        periodAverage.avgWriteTimePerEvent =
            periodWriteEventCount == 0 ? BigDecimal.ZERO :
                new BigDecimal(periodWriteEventTime).divide(new BigDecimal(periodWriteEventCount), 2,
                    BigDecimal.ROUND_HALF_UP);
        periodAverage.avgWriteTimePerTxn = periodWriteTxnCount == 0 ? BigDecimal.ZERO :
            new BigDecimal(periodWriteTxnTime).divide(new BigDecimal(periodWriteTxnCount), 2,
                BigDecimal.ROUND_HALF_UP);
        periodAverage.avgWriteBytesTps = periodWriteEventBytes / period;
        periodAverage.avgTxnWriteTps = periodWriteTxnCount / period;
        periodAverage.avgEventWriteTps = periodWriteEventCount / period;
        periodAverage.avgRevEventWriteTps = periodRevEventWriteCount / period;
        periodAverage.avgRevWriteBytesTps = periodRevWriteEventBytes / period;
        periodAverage.avgYoungUsed = snapshot.jvmSnapshot.getYoungUsed();
        periodAverage.avgOldUsed = snapshot.jvmSnapshot.getOldUsed();

        return periodAverage;
    }

    static class MetricsSnapshot {
        MetricsSnapshot(long seq) {
            this.seq = seq;
        }

        long seq;
        long timestamp;
        Metrics metrics;
        Average periodAverage;
        JvmSnapshot jvmSnapshot;
    }

    static class Average {

        /**
         * 完成一个事件写入的平均耗时
         */
        BigDecimal avgWriteTimePerEvent;
        /**
         * 完成一个事务写入的平均耗时
         */
        BigDecimal avgWriteTimePerTxn;
        /**
         * 平均每秒写入binlog文件的字节数
         */
        long avgWriteBytesTps;
        /**
         * 从Task接收到的事件写入binlog文件的平均tps(按字节数)
         */
        long avgRevWriteBytesTps;
        /**
         * 事务写入binlog文件的平均tps
         */
        long avgTxnWriteTps;
        /**
         * 所有事件写入binlog文件的平均tps
         */
        long avgEventWriteTps;
        /**
         * 从Task接收到的事件写入binlog文件的平均tps(按个数)
         */
        long avgRevEventWriteTps;
        /**
         * 新生代内存使用情况的平均值
         */
        long avgYoungUsed;
        /**
         * 老年代内存使用情况的平均值
         */
        long avgOldUsed;
    }
}
