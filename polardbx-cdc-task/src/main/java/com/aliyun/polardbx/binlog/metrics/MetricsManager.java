/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.metrics;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.cdc.meta.MetaMetrics;
import com.aliyun.polardbx.binlog.extractor.MultiStreamStartTsoWindow;
import com.aliyun.polardbx.binlog.jvm.JvmSnapshot;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import com.aliyun.polardbx.binlog.metrics.format.TableFormat;
import com.aliyun.polardbx.binlog.storage.StorageMetrics;
import com.aliyun.polardbx.binlog.storage.TxnBuffer;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
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

import static com.aliyun.polardbx.binlog.ConfigKeys.PRINT_METRICS;

/**
 * Created by ziyang.lb
 **/
public class MetricsManager {
    private static final Logger METRICS_LOGGER = LoggerFactory.getLogger("METRICS");
    private static final Logger META_METRICS_LOGGER = LoggerFactory.getLogger("META-METRICS");
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
                    printMeta(snapshot);
                    metrics(snapshot);
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

    private void print(MetricsSnapshot snapshot) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n")
            .append(
                "--------------------------------------------------task metrics begin------------------------------------------------------")
            .append("\n");

        printExtractor(snapshot, stringBuilder);
        printMerger(snapshot, stringBuilder);
        printStorage(snapshot, stringBuilder);
        printJvm(snapshot, stringBuilder);

        stringBuilder.append("\r\n")
            .append(
                "--------------------------------------------------task metrics end--------------------------------------------------------")
            .append("\r\n");

        METRICS_LOGGER.info(stringBuilder.toString());
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

        //Extractor Basic Metrics
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
            "tps(event)",
            "isAllReady");
        tableFormat.addRow(
            tsoC + noTsoC + hc,
            tsoC,
            noTsoC,
            extractorMetric.getNetIn().get(),
            hc,
            TimeUnit.MILLISECONDS.toSeconds(INTERVAL),
            periodTranCount / Math.max(snapshot.period, 1),
            periodEventCount / Math.max(snapshot.period, 1),
            MultiStreamStartTsoWindow.getInstance().isAllReady());
        stringBuilder.append(tableFormat);

        //Extractor Thread Metrics
        if (extractorMetric.getRecorderMap().isEmpty()) {
            stringBuilder.append("binlog parser not ready!").append("\n");
        } else {
            TableFormat threadInfoFormat = new TableFormat("Extractor threadInfo");
            TableFormat threadExtInfoFormat = new TableFormat("Extractor threadExtInfo");
            threadInfoFormat.addColumn("tid",
                "storage",
                "ex-lf-st",
                "rt(ms)",
                "ex-t-status",
                "pos",
                "delay(s)",
                "queuedTransSizeInSorter",
                "firstTransInSorter",
                "ms-queue-size",
                "ms-pass-count",
                "ms-poll-count");
            threadExtInfoFormat.addColumn("tid", "storage", "firstTransXidInSorter");
            for (ThreadRecorder record : extractorMetric.getRecorderMap().values()) {
                threadInfoFormat.addRow(record.getTid(),
                    record.getStorageInstanceId(),
                    record.getState(),
                    record.getRt(),
                    record.isComplete() ? "RUNNING" : "BLOCK",
                    record.getPosition(),
                    Math.max(System.currentTimeMillis() / 1000 - record.getWhen(), 0),
                    record.getQueuedTransSizeInSorter(),
                    record.getFirstTransInSorter(),
                    record.getMergeSourceQueueSize(),
                    record.getMergeSourcePassCount(),
                    record.getMergeSourcePollCount());

                threadExtInfoFormat
                    .addRow(record.getTid(), record.getStorageInstanceId(), record.getFirstTransXidInSorter());
            }
            stringBuilder.append(threadInfoFormat);
            stringBuilder.append(threadExtInfoFormat);
        }
    }

    private void printMerger(MetricsSnapshot snapshot, StringBuilder sb) {
        MergeMetrics mergeMetrics = snapshot.mergeMetrics;
        // first part
        TableFormat threadInfoFormat1 = new TableFormat("Merger Metrics-1");
        threadInfoFormat1.addColumn(
            "totalMergePassCount",
            "totalMergePass2PCCount",
            "totalMergePollEmptyCount",
            "delayTimeOnMerge(ms)",
            "delayTimeOnCollect(ms)",
            "delayTimeOnTransmit(ms)");
        threadInfoFormat1.addRow(
            mergeMetrics.getTotalMergePassCount(),
            mergeMetrics.getTotalMergePass2PCCount(),
            mergeMetrics.getTotalMergePollEmptyCount(),
            mergeMetrics.getDelayTimeOnMerge(),
            mergeMetrics.getDelayTimeOnCollect(),
            mergeMetrics.getDelayTimeOnTransmit());
        sb.append(threadInfoFormat1);

        // second part
        TableFormat threadInfoFormat2 = new TableFormat("Merger Metrics-2");
        threadInfoFormat2.addColumn(
            "ringBufferQueuedSize",
            "transmitQueuedSize",
            "dumpingQueueSize",
            "totalPushToCollectorBlockTime(nano)",
            "totalTransmitCount",
            "totalSingleTransmitCount",
            "totalChunkTransmitCount");
        threadInfoFormat2.addRow(
            mergeMetrics.getRingBufferQueuedSize(),
            mergeMetrics.getTransmitQueuedSize(),
            mergeMetrics.getDumpingQueueSize(),
            mergeMetrics.getTotalPushToCollectorBlockTime(),
            mergeMetrics.getTotalTransmitCount(),
            mergeMetrics.getTotalSingleTransmitCount(),
            mergeMetrics.getTotalChunkTransmitCount());
        sb.append(threadInfoFormat2);
    }

    private void printJvm(MetricsSnapshot snapshot, StringBuilder sb) {
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

    private void printStorage(MetricsSnapshot snapshot, StringBuilder sb) {
        TableFormat storageMetrics = new TableFormat("Storage Metrics");
        storageMetrics.addColumn(
            "currentTxnBufferCount",
            "persistedTxnBufferCount",
            "currentTxnItemCount",
            "txnTotalCreateCount",
            "txnTotalCostTime(nano)",
            "storageCleanerQueuedSize");
        storageMetrics.addRow(
            TxnBuffer.TOTAL_TXN_COUNT.get(),
            TxnBuffer.TOTAL_TXN_PERSISTED_COUNT.get(),
            TxnItemRef.TOTAL_TXN_ITEM_COUNT.get(),
            StorageMetrics.get().getTxnCreateCount(),
            StorageMetrics.get().getTxnCreateCostTime(),
            snapshot.mergeMetrics.getStorageCleanerQueuedSize());
        sb.append(storageMetrics);
    }

    //meta信息不经常变动，且输出量比较大，单独放到一个文件中
    private void printMeta(MetricsSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n")
            .append(
                "--------------------------------------------------meta metrics begin------------------------------------------------------")
            .append("\n");

        TableFormat metaMetrics1 = new TableFormat("Meta Metrics Common");
        metaMetrics1.addColumn(
            "logicDbCount",
            "logicTableCount",
            "phyDbCount",
            "phyTableCount",
            "rollbackFinishCount",
            "rollbackAvgTime(ms)",
            "rollbackMaxTime(ms)",
            "rollbackMinTime(ms)");
        metaMetrics1.addRow(
            snapshot.metaMetrics.getLogicDbCount(),
            snapshot.metaMetrics.getLogicTableCount(),
            snapshot.metaMetrics.getPhyDbCount(),
            snapshot.metaMetrics.getPhyTableCount(),
            snapshot.metaMetrics.getRollbackFinishCount(),
            snapshot.metaMetrics.getRollbackAvgTime(),
            snapshot.metaMetrics.getRollbackMaxTime(),
            snapshot.metaMetrics.getRollbackMinTime());
        sb.append(metaMetrics1);

        TableFormat metaMetrics2 =
            new TableFormat("Meta Metrics Logic Rollback,[Snap = Snapshot],[Hist = History],[T = Time]");
        metaMetrics2.addColumn(
            "applySnapAvgT(ms)",
            "applySnapMaxT(ms)",
            "applySnapMinT(ms)",
            "applyHisAvgT(ms)",
            "applyHistMaxT(ms)",
            "applyHistMinT(ms)",
            "queryDdlHistAvgT(ms)",
            "queryDdlHistMaxT(ms)",
            "queryDdlHistMinT(ms)",
            "querySnapAvgT(ms)",
            "avgQueryDdlHistCount");
        metaMetrics2.addRow(
            snapshot.metaMetrics.getLogicApplySnapshotAvgTime(),
            snapshot.metaMetrics.getLogicApplySnapshotMaxTime(),
            snapshot.metaMetrics.getLogicApplySnapshotMinTime(),
            snapshot.metaMetrics.getLogicApplyHistoryAvgTime(),
            snapshot.metaMetrics.getLogicApplyHistoryMaxTime(),
            snapshot.metaMetrics.getLogicApplyHistoryMinTime(),
            snapshot.metaMetrics.getLogicQueryDdlHistoryAvgTime(),
            snapshot.metaMetrics.getLogicQueryDdlHistoryMaxTime(),
            snapshot.metaMetrics.getLogicQueryDdlHistoryMinTime(),
            snapshot.metaMetrics.getAvgLogicQuerySnapshotCostTime(),
            snapshot.metaMetrics.getAvgLogicQueryDdlHistoryCount());
        sb.append(metaMetrics2);

        TableFormat metaMetrics3 =
            new TableFormat("Meta Metrics Physical Rollback,[Snap = Snapshot],[Hist = History],[T = Time]");
        metaMetrics3.addColumn(
            "applySnapAvgT(ms)",
            "applySnapMaxT(ms)",
            "applySnapMinT(ms)",
            "applyHistAvgT(ms)",
            "applyHistMaxT(ms)",
            "applyHistMinT(ms)",
            "queryDdlHistAvgT(ms)",
            "queryDdlHistMaxT(ms)",
            "queryDdlHistMinT(ms)",
            "avgQueryDdlHistCount");
        metaMetrics3.addRow(
            snapshot.metaMetrics.getPhyApplySnapshotAvgTime(),
            snapshot.metaMetrics.getPhyApplySnapshotMaxTime(),
            snapshot.metaMetrics.getPhyApplySnapshotMinTime(),
            snapshot.metaMetrics.getPhyApplyHistoryAvgTime(),
            snapshot.metaMetrics.getPhyApplyHistoryMaxTime(),
            snapshot.metaMetrics.getPhyApplyHistoryMinTime(),
            snapshot.metaMetrics.getPhyQueryDdlHistoryAvgTime(),
            snapshot.metaMetrics.getPhyQueryDdlHistoryMaxTime(),
            snapshot.metaMetrics.getPhyQueryDdlHistoryMinTime(),
            snapshot.metaMetrics.getAvgPhyQueryDdlHistoryCount());
        sb.append(metaMetrics3);

        sb.append("\r\n")
            .append(
                "--------------------------------------------------meta metrics end--------------------------------------------------------")
            .append("\r\n");

        META_METRICS_LOGGER.info(sb.toString());
    }

    private MetricsSnapshot buildSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        snapshot.extractorMetric = ExtractorMetrics.get().snapshot();
        snapshot.mergeMetrics = MergeMetrics.get().snapshot();
        snapshot.jvmSnapshot = JvmUtils.buildJvmSnapshot();
        snapshot.metaMetrics = MetaMetrics.get().snapshot();

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
        MetaMetrics metaMetrics;
    }
}
