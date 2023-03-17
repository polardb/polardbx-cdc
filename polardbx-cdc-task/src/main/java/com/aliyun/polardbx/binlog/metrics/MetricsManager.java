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
package com.aliyun.polardbx.binlog.metrics;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.cdc.meta.MetaMetrics;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;

import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.PRINT_METRICS;
import static com.aliyun.polardbx.binlog.canal.LogEventUtil.getGroupFromXid;
import static com.aliyun.polardbx.binlog.canal.LogEventUtil.getHexTranIdFromXid;

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

    private void print(MetricsSnapshot snapshot) {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("\n")
            .append(
                "################################################## task metrics begin ####################################################")
            .append("\n");

        contactExtractorMetrics(snapshot, stringBuilder);
        contactMergerMetrics(snapshot, stringBuilder);
        contactTransmitMetrics(snapshot, stringBuilder);
        contactStorageMetrics(snapshot, stringBuilder);
        contactJvmMetrics(snapshot, stringBuilder);
        contactRelayWriterMetrics(snapshot, stringBuilder);
        contactRelayStreamMetrics(snapshot, stringBuilder);

        stringBuilder.append("\r\n")
            .append(
                "################################################## task metrics end ########################################################")
            .append("\r\n");

        METRICS_LOGGER.info(stringBuilder.toString());
    }

    @SneakyThrows
    private void sendMetrics(MetricsSnapshot snapshot) {
        List<CommonMetrics> commonMetrics = Lists.newArrayList();
        AggregateCoreMetrics aggregateCoreMetrics = snapshot.aggregateCoreMetrics;
        Field[] averageFields = AggregateCoreMetrics.class.getDeclaredFields();
        for (Field f : averageFields) {
            CommonMetrics x = CommonMetricsHelper.getTask().get(f.getName());
            if (x != null) {
                f.setAccessible(true);
                if (aggregateCoreMetrics != null) {
                    Object o = f.get(aggregateCoreMetrics);
                    if (o != null) {
                        commonMetrics.add(x.var(o));
                    }
                }
            }
        }

        JvmSnapshot jvmSnapshot = snapshot.jvmSnapshot;
        if (jvmSnapshot != null) {
            String prefix = "polardbx_cdc_task_";
            CommonMetricsHelper.addJvmMetrics(commonMetrics, jvmSnapshot, prefix);
        }
        if (!CollectionUtils.isEmpty(commonMetrics)) {
            MetricsReporter.report(commonMetrics);
        }
    }

    private void contactExtractorMetrics(MetricsSnapshot snapshot, StringBuilder stringBuilder) {
        ExtractorMetrics extractorMetric = snapshot.extractorMetrics;

        //Extractor Basic Metrics
        long tsoC = extractorMetric.getTsoTranCount();
        long noTsoC = extractorMetric.getNoTsoTranCount();
        long hc = extractorMetric.getHeartbeatCount();
        TableFormat tableFormat = new TableFormat("Extractor Metrics");
        tableFormat.addColumn(
            "totalTxnCount",
            "tsoTxnCount",
            "noTsoTxnCount",
            "heartbeatCount",
            "netInBps",
            "inTps",
            "inEps",
            "maxDelay(ms)",
            "isAllReady");
        tableFormat.addRow(
            tsoC + noTsoC + hc,
            tsoC,
            noTsoC,
            hc,
            snapshot.aggregateCoreMetrics.netInBps,
            snapshot.aggregateCoreMetrics.inTps,
            snapshot.aggregateCoreMetrics.inEps,
            snapshot.aggregateCoreMetrics.inDelay,
            MultiStreamStartTsoWindow.getInstance().isAllReady());
        stringBuilder.append(tableFormat);

        //Extractor Thread Metrics
        if (ThreadRecorder.getRecorderMap().isEmpty()) {
            stringBuilder.append("binlog parser not ready!").append("\n");
        } else {
            TableFormat threadInfoFormat = new TableFormat("Extractor threadInfo");
            TableFormat threadExtInfoFormat = new TableFormat("Extractor threadExtInfo");
            threadInfoFormat.addColumn("tid",
                "storage",
                "stat",
                "rt(ms)",
                "status",
                "pos",
                "delay(ms)",
                "sorter_queue",
                "sorter_first_trans",
                "ms_queue_size",
                "ms_pass_cnt");
            threadExtInfoFormat.addColumn("tid", "storage", "firstTransXidInSorter", "firstTransDecoded");
            for (ThreadRecorder record : ThreadRecorder.getRecorderMap().values()) {
                threadInfoFormat.addRow(record.getTid(),
                    record.getStorageInstanceId(),
                    record.getState(),
                    record.getRt(),
                    record.isComplete() ? "RUNNING" : "BLOCK",
                    record.getPosition(),
                    Math.max(System.currentTimeMillis() - record.getWhen() * 1000, 0),
                    record.getQueuedTransSizeInSorter(),
                    record.getFirstTransInSorter(),
                    record.getMergeSourceQueueSize(),
                    record.getMergeSourcePassCount());

                String firstXid = record.getFirstTransXidInSorter();
                threadExtInfoFormat
                    .addRow(record.getTid(), record.getStorageInstanceId(), firstXid, decodeXid(firstXid));
            }
            stringBuilder.append(threadInfoFormat);
            stringBuilder.append(threadExtInfoFormat);
        }
    }

    private String decodeXid(String xid) {
        try {
            if (StringUtils.isNotBlank(xid)) {
                String tranId = getHexTranIdFromXid(xid, "utf8");
                String group = getGroupFromXid(xid, "utf8");
                return String.format("tranId : %s, group : %s", tranId, group);
            } else {
                return "";
            }
        } catch (Throwable t) {
            return t.getMessage();
        }
    }

    private void contactMergerMetrics(MetricsSnapshot snapshot, StringBuilder sb) {
        MergeMetrics mergeMetrics = snapshot.mergeMetrics;
        // first part
        TableFormat threadInfoFormat1 = new TableFormat("Merger Metrics");
        threadInfoFormat1.addColumn(
            "totalMergePassCount",
            "totalMergePass2PCCount",
            "totalMergePollEmptyCount",
            "collectQueuedSize",
            "delayTimeOnMerge(ms)",
            "delayTimeOnCollect(ms)");
        threadInfoFormat1.addRow(
            mergeMetrics.getTotalMergePassCount(),
            mergeMetrics.getTotalMergePass2PCCount(),
            mergeMetrics.getTotalMergePollEmptyCount(),
            mergeMetrics.getCollectQueuedSize(),
            mergeMetrics.getDelayTimeOnMerge(),
            mergeMetrics.getDelayTimeOnCollect());
        sb.append(threadInfoFormat1);
    }

    private void contactTransmitMetrics(MetricsSnapshot snapshot, StringBuilder sb) {
        // second part
        TransmitMetrics transmitMetrics = snapshot.transmitMetrics;
        TableFormat threadInfoFormat2 = new TableFormat("Transmit Metrics");
        threadInfoFormat2.addColumn(
            "transmitQueuedSize",
            "dumpingQueuedSize",
            "totalTransmitCount",
            "totalSingleTransmitCount",
            "totalChunkTransmitCount",
            "delayTimeOnTransmit(ms)");
        threadInfoFormat2.addRow(
            transmitMetrics.getTransmitQueuedSize(),
            transmitMetrics.getDumpingQueueSize(),
            transmitMetrics.getTotalTransmitCount(),
            transmitMetrics.getTotalSingleTransmitCount(),
            transmitMetrics.getTotalChunkTransmitCount(),
            transmitMetrics.getDelayTimeOnTransmit());
        sb.append(threadInfoFormat2);
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

    private void contactStorageMetrics(MetricsSnapshot snapshot, StringBuilder sb) {
        TableFormat storageMetrics = new TableFormat("Storage Metrics");
        storageMetrics.addColumn(
            "currentTxnBufferCount",
            "persistedTxnBufferCount",
            "currentTxnItemCount",
            "persistedTxnItemCount",
            "totalTxnCreateCount",
            "totalTxnCreateCostTime(nano)",
            "cleanerQueuedSize");
        storageMetrics.addRow(
            TxnBuffer.CURRENT_TXN_COUNT.get(),
            TxnBuffer.CURRENT_TXN_PERSISTED_COUNT.get(),
            TxnItemRef.CURRENT_TXN_ITEM_COUNT.get(),
            TxnItemRef.CURRENT_TXN_ITEM_PERSISTED_COUNT.get(),
            StorageMetrics.get().getTxnCreateCount(),
            StorageMetrics.get().getTxnCreateCostTime(),
            StorageMetrics.get().getCleanerQueuedSize());
        sb.append(storageMetrics);
    }

    private void contactRelayWriterMetrics(MetricsSnapshot snapshot, StringBuilder sb) {
        if (RelayWriterMetrics.getMetricsMap().isEmpty()) {
            return;
        }
        TableFormat relayWriterFormatInfo = new TableFormat("Relay Writer Metrics");
        relayWriterFormatInfo.addColumn(
            "threadId",
            "queuedSize",
            "putCount",
            "takeCount",
            "streams");
        for (RelayWriterMetrics metrics : RelayWriterMetrics.getMetricsMap().values()) {
            relayWriterFormatInfo.addRow(
                metrics.getThreadId(),
                metrics.getQueuedSize(),
                metrics.getPutCount(),
                metrics.getTakeCount(),
                metrics.getStreams()
            );
        }

        sb.append(relayWriterFormatInfo);
    }

    private void contactRelayStreamMetrics(MetricsSnapshot snapshot, StringBuilder sb) {
        if (RelayStreamMetrics.getMetricsMap().isEmpty()) {
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        TableFormat relayStreamFormatInfo = new TableFormat("Relay Stream Metrics");
        relayStreamFormatInfo.addColumn(
            "streamSeq",
            "writeEventCount",
            "readEventCount",
            "writeEps",
            "readEps",
            "writeBytes",
            "readBytes",
            "writeBps",
            "readBps",
            "writeDelay(ms)",
            "readDelay(ms)",
            "minRelayDataTime",
            "maxRelayDataTime",
            "fileCount");
        for (RelayStreamMetrics metrics : RelayStreamMetrics.getMetricsMap().values()) {
            relayStreamFormatInfo.addRow(
                metrics.getStreamSeq(),
                metrics.getWriteEventCount().get(),
                metrics.getReadEventCount().get(),
                metrics.getWriteEps().get(),
                metrics.getReadEps().get(),
                metrics.getWriteByteSize().get(),
                metrics.getReadByteSize().get(),
                metrics.getWriteBps().get(),
                metrics.getReadBps().get(),
                metrics.getWriteDelay().get(),
                metrics.getReadDelay().get(),
                sdf.format(new Date(metrics.getMinRelayTimestamp().get())),
                sdf.format(new Date(metrics.getMaxRelayTimestamp().get())),
                metrics.getFileCount().get()
            );
        }

        sb.append(relayStreamFormatInfo);
    }

    //meta信息不经常变动，且输出量比较大，单独放到一个文件中
    private void printMeta(MetricsSnapshot snapshot) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n")
            .append(
                "######################################################## meta metrics begin #####################################################")
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
                "######################################################## meta metrics end ########################################################")
            .append("\r\n");

        META_METRICS_LOGGER.info(sb.toString());
    }

    private MetricsSnapshot buildSnapshot() {
        MetricsSnapshot snapshot = new MetricsSnapshot();
        snapshot.timestamp = System.currentTimeMillis();
        if (lastSnapshot != null) {
            snapshot.period = TimeUnit.MILLISECONDS.toSeconds(snapshot.timestamp - lastSnapshot.timestamp);
        } else {
            snapshot.period = TimeUnit.MILLISECONDS.toSeconds(snapshot.timestamp - startTime);
        }

        snapshot.extractorMetrics = ExtractorMetrics.get().snapshot();
        snapshot.mergeMetrics = MergeMetrics.get().snapshot();
        snapshot.transmitMetrics = TransmitMetrics.get().snapshot();
        snapshot.jvmSnapshot = JvmUtils.buildJvmSnapshot();
        snapshot.metaMetrics = MetaMetrics.get().snapshot();
        snapshot.aggregateCoreMetrics = buildCoreMetrics(snapshot);

        return snapshot;
    }

    private AggregateCoreMetrics buildCoreMetrics(MetricsSnapshot snapshot) {
        AggregateCoreMetrics aggregateCoreMetrics = new AggregateCoreMetrics();
        if (lastSnapshot != null) {
            long d1 = snapshot.extractorMetrics.getTotalTranCount() -
                lastSnapshot.extractorMetrics.getTotalTranCount();
            aggregateCoreMetrics.inTps = Double.valueOf(((double) d1) / Math.max(snapshot.period, 1)).longValue();

            long d2 = snapshot.extractorMetrics.getEventTotalCount() -
                lastSnapshot.extractorMetrics.getEventTotalCount();
            aggregateCoreMetrics.inEps = Double.valueOf(((double) d2) / Math.max(snapshot.period, 1)).longValue();

            long d3 = snapshot.extractorMetrics.getNetIn() -
                lastSnapshot.extractorMetrics.getNetIn();
            aggregateCoreMetrics.netInBps = Double.valueOf(((double) d3) / Math.max(snapshot.period, 1)).longValue();

            long d4 = snapshot.mergeMetrics.getTotalMergePassCount()
                - lastSnapshot.mergeMetrics.getTotalMergePassCount();
            aggregateCoreMetrics.mergeTps = Double.valueOf(((double) d4) / Math.max(snapshot.period, 1)).longValue();

            long d5 = snapshot.mergeMetrics.getTotalMergePass2PCCount()
                - lastSnapshot.mergeMetrics.getTotalMergePass2PCCount();
            aggregateCoreMetrics.merge2pcTps = Double.valueOf(((double) d5) / Math.max(snapshot.period, 1)).longValue();

            long d6 = snapshot.mergeMetrics.getTotalMergePass1PCCount()
                - lastSnapshot.mergeMetrics.getTotalMergePass1PCCount();
            aggregateCoreMetrics.merge1pcTps = Double.valueOf(((double) d6) / Math.max(snapshot.period, 1)).longValue();
        }
        aggregateCoreMetrics.maxSorterQueuedSize = snapshot.extractorMetrics.getMaxSorterQueuedSize();
        aggregateCoreMetrics.inDelay = snapshot.extractorMetrics.getMaxDelay();
        aggregateCoreMetrics.mergeTxnTotal = snapshot.mergeMetrics.getTotalMergePassCount();
        aggregateCoreMetrics.merge1PcTxnTotal = snapshot.mergeMetrics.getTotalMergePass1PCCount();
        aggregateCoreMetrics.merge2PcTxnTotal = snapshot.mergeMetrics.getTotalMergePass2PCCount();
        aggregateCoreMetrics.collectQueuedSize = snapshot.mergeMetrics.getCollectQueuedSize();
        aggregateCoreMetrics.transmitQueuedSize = snapshot.transmitMetrics.getTransmitQueuedSize();
        aggregateCoreMetrics.dumpingQueueSize = snapshot.transmitMetrics.getDumpingQueueSize();
        aggregateCoreMetrics.logicDbCount = snapshot.metaMetrics.getLogicDbCount();
        aggregateCoreMetrics.logicTableCount = snapshot.metaMetrics.getLogicTableCount();
        aggregateCoreMetrics.phyDbCount = snapshot.metaMetrics.getPhyDbCount();
        aggregateCoreMetrics.phyTableCount = snapshot.metaMetrics.getPhyTableCount();
        aggregateCoreMetrics.logicDdlHistoryCount =
            SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class).count(s -> s);
        aggregateCoreMetrics.phyDdlHistoryCount =
            SpringContextHolder.getObject(BinlogPhyDdlHistoryMapper.class).count(s -> s);
        aggregateCoreMetrics.storeTxnCount = TxnBuffer.CURRENT_TXN_COUNT.get();
        aggregateCoreMetrics.storePersistedTxnCount = TxnBuffer.CURRENT_TXN_PERSISTED_COUNT.get();
        aggregateCoreMetrics.storeTxnItemCount = TxnItemRef.CURRENT_TXN_ITEM_COUNT.get();
        aggregateCoreMetrics.storePersistedTxnItemCount = TxnItemRef.CURRENT_TXN_ITEM_PERSISTED_COUNT.get();
        aggregateCoreMetrics.storeToCleanTxnSize = StorageMetrics.get().getCleanerQueuedSize().longValue();

        return aggregateCoreMetrics;
    }

    private static class MetricsSnapshot {
        long timestamp;
        long period;//单位s

        ExtractorMetrics extractorMetrics;
        MergeMetrics mergeMetrics;
        TransmitMetrics transmitMetrics;
        JvmSnapshot jvmSnapshot;
        MetaMetrics metaMetrics;
        AggregateCoreMetrics aggregateCoreMetrics;
    }

    private static class AggregateCoreMetrics {
        private long inTps;
        private long inEps;
        private long netInBps;
        private long inDelay;
        private long maxSorterQueuedSize;

        private long mergeTxnTotal;
        private long merge1PcTxnTotal;
        private long merge2PcTxnTotal;
        private long mergeTps;
        private long merge1pcTps;
        private long merge2pcTps;

        private long collectQueuedSize;
        private long transmitQueuedSize;
        private long dumpingQueueSize;

        private long logicDbCount;
        private long logicTableCount;
        private long phyDbCount;
        private long phyTableCount;
        private long logicDdlHistoryCount;
        private long phyDdlHistoryCount;

        private long storeTxnCount;
        private long storePersistedTxnCount;
        private long storeTxnItemCount;
        private long storePersistedTxnItemCount;
        private long storeToCleanTxnSize;
    }
}
