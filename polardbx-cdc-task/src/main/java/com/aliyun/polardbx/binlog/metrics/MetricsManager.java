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

package com.aliyun.polardbx.binlog.metrics;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.jvm.JvmSnapshot;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.metrics.format.TableFormat;
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
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.PRINT_METRICS;

/**
 * Created by ziyang.lb
 **/
public class MetricsManager {
    private static final Logger logger = LoggerFactory.getLogger("METRICS");
    private static final long INTERVAL = TimeUnit.SECONDS.toMillis(5);

    private final ScheduledExecutorService scheduledExecutorService;
    private MetricsSnapshot lastSnapshot;
    private long startTime;
    private volatile boolean running;

    public MetricsManager() {
        scheduledExecutorService = Executors.newSingleThreadScheduledExecutor((r) -> {
            Thread t = new Thread(r, "task-metrics-manager");
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

    private void print(MetricsSnapshot snapshot) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n")
            .append("----------------------task metrics begin--------------------------")
            .append("\n");

        printExtractor(snapshot, stringBuilder);
        printMerger(snapshot, stringBuilder);
        printJvm(snapshot, stringBuilder);

        stringBuilder.append("\r\n")
            .append("----------------------task metrics end----------------------------")
            .append("\r\n");

        logger.info(stringBuilder.toString());
    }

    @SneakyThrows
    private void metrics(MetricsSnapshot snapshot) {
        List<CommonMetrics> commonMetrics = Lists.newArrayList();
        ExtractorMetrics extractorMetric = snapshot.extractorMetric;

        Field[] averageFields = ExtractorMetrics.class.getDeclaredFields();
        for (Field f : averageFields) {
            CommonMetrics x = CommonMetricsHelper.getTask().get(f.getName());
            if (x != null) {
                f.setAccessible(true);
                if (extractorMetric != null) {
                    Object o = f.get(extractorMetric);
                    if (o != null) {
                        commonMetrics.add(x.var(o));
                    }
                }
            }
        }

        MergeMetrics mergeMetrics = snapshot.mergeMetrics;
        Field[] mergeFields = MergeMetrics.class.getDeclaredFields();
        for (Field f : mergeFields) {
            CommonMetrics x = CommonMetricsHelper.getTask().get(f.getName());
            if (x != null) {
                f.setAccessible(true);
                if (mergeMetrics != null) {
                    Object o = f.get(mergeMetrics);
                    if (o != null) {
                        commonMetrics.add(x.var(o));
                    }
                }
            }
        }
        JvmSnapshot jvmSnapshot = snapshot.jvmSnapshot;
        if (jvmSnapshot != null) {
            String prefix = "polardbx_cdc_task_";
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

    private void printExtractor(MetricsSnapshot snapshot, StringBuilder stringBuilder) {
        ExtractorMetrics extractorMetric = snapshot.extractorMetric;

        long tsoC = extractorMetric.getTsoCount().get();
        long noTsoC = extractorMetric.getNoTsoCount().get();
        long hc = extractorMetric.getHeartbeatCount().get();
        long periodTranCount = extractorMetric.getTranTotalCount().getAndSet(0);
        long periodEventCount = extractorMetric.getEventTotalCount().getAndSet(0);
        TableFormat tableFormat = new TableFormat("Extractor Metrics");
        tableFormat.addColumn(
            "totalCount",
            "tsoCount",
            "noTsoCount",
            "netIn",
            "heartbeatCount",
            "lastPeriod",
            "tps(tran)",
            "tps(event)");
        tableFormat.addRow(
            tsoC + noTsoC + hc,
            tsoC,
            noTsoC,
            extractorMetric.getNetIn().get(),
            hc,
            TimeUnit.MILLISECONDS.toSeconds(INTERVAL),
            periodTranCount / Math.max(snapshot.period, 1),
            periodEventCount / Math.max(snapshot.period, 1));

        stringBuilder.append(tableFormat).append("\n");
        if (extractorMetric.getRecorderMap().isEmpty()) {
            stringBuilder.append("binlog parser not ready!");
        } else {
            TableFormat threadInfoFormat = new TableFormat("threadInfo");
            threadInfoFormat.addColumn("tid",
                "storage",
                "ex-lf-st",
                "rt(ms)",
                "ex-t-status",
                "pos",
                "delay(s)",
                "tranBuferSizeChange");
            for (ThreadRecorder record : extractorMetric.getRecorderMap().values()) {
                threadInfoFormat.addRow(record.getTid(),
                    record.getStorageInstanceId(),
                    record.getState(),
                    record.getRt(),
                    record.isComplete() ? "RUNNING" : "BLOCK",
                    record.getPosition(),
                    Math.max(System.currentTimeMillis() / 1000 - record.getWhen(), 0),
                    record.getCommitSizeChange());
            }
            stringBuilder.append(threadInfoFormat);
        }
        stringBuilder.append("\n\n");
    }

    private void printMerger(MetricsSnapshot snapshot, StringBuilder sb) {
        MergeMetrics mergeMetrics = snapshot.mergeMetrics;
        long mergeCountPeriod = mergeMetrics.getTotalMergePassCount() - (lastSnapshot == null ? 0 :
            lastSnapshot.mergeMetrics.getTotalMergePassCount());

        sb.append("\r\n");
        sb.append("Merger Metrics:");
        sb.append("\r\n");
        sb.append(String.format(
            "totalMergePassCount : %s, mergePassTps : %s, totalMergePass2PCCount : %s ,totalMergePollEmptyCount : %s",
            mergeMetrics.getTotalMergePassCount(),
            mergeCountPeriod / Math.max(snapshot.period, 1),
            mergeMetrics.getTotalMergePass2PCCount(),
            mergeMetrics.getTotalMergePollEmptyCount()));
        sb.append("\r\n");
        sb.append(String.format(
            "delayTimeOnMerge(ms) : %s, delayTimeOnCollect(ms): %s, delayTimeOnTransmit(ms) : %s",
            mergeMetrics.getDelayTimeOnMerge(),
            mergeMetrics.getDelayTimeOnCollect(),
            mergeMetrics.getDelayTimeOnTransmit()));
        sb.append("\r\n");
        sb.append(String.format(
            "ringBufferQueuedSize : %s, transmitQueuedSize : %s, totalPushToCollectorBlockTime : %s, storageCleanerQueuedSize: %s",
            mergeMetrics.getRingBufferQueuedSize(),
            mergeMetrics.getTransmitQueuedSize(),
            mergeMetrics.getTotalPushToCollectorBlockTime(),
            mergeMetrics.getStorageCleanerQueuedSize()));
        sb.append("\r\n");
        sb.append(String.format(
            "totalTransmitCount : %s, totalSingleTransmitCount : %s, totalChunkTransmitCount : %s",
            mergeMetrics.getTotalTransmitCount(),
            mergeMetrics.getTotalSingleTransmitCount(),
            mergeMetrics.getTotalChunkTransmitCount()));

        for (Map.Entry<String, Long> entry : mergeMetrics.getMergeSourceQueuedSize().entrySet()) {
            sb.append("\r\n");
            sb.append(String.format("Merge source [%s] queued size : %s", entry.getKey(), entry.getValue()));
        }
        sb.append("\n\n");
    }

    private void printJvm(MetricsSnapshot snapshot, StringBuilder sb) {
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
    }

    private MetricsSnapshot buildSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.extractorMetric = ExtractorMetrics.get().snapshot();
        snapshot.mergeMetrics = MergeMetrics.get().snapshot();
        snapshot.jvmSnapshot = JvmUtils.buildJvmSnapshot();

        if (lastSnapshot != null) {
            snapshot.period = TimeUnit.MILLISECONDS.toSeconds(snapshot.timestamp - lastSnapshot.timestamp);
        } else {
            snapshot.period = TimeUnit.MILLISECONDS.toSeconds(snapshot.timestamp - startTime);
        }
        return snapshot;
    }

    private static class MetricsSnapshot {
        long timestamp;
        long period;//单位s
        ExtractorMetrics extractorMetric;
        MergeMetrics mergeMetrics;
        JvmSnapshot jvmSnapshot;
    }
}
