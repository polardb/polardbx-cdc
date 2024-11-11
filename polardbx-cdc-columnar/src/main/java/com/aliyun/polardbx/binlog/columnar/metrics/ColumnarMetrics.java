/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.columnar.metrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskConfigMapper;
import com.aliyun.polardbx.binlog.domain.po.ColumnarTaskConfig;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;

@Slf4j
public class ColumnarMetrics {
    private static final String COLUMNAR_METRICS_URL = "http://%s:%s/columnarServer/metric";
    private static final AtomicBoolean INITED = new AtomicBoolean(false);
    private final Map<String, String> columnarMetricFieldMap = new HashMap<>();
    private final Map<String, Double> columnarMetricValueMap = new HashMap<>();

    /**
     * 列存副本数量
     */
    private double indexCount = 0;
    /**
     * 分区数量
     */
    private double partitionCount = 0;
    /**
     * Group Commit 延迟时间
     */
    private double groupCommitLatency = 0;
    /**
     * Group Commit 数据吞吐量
     */
    private double groupCommitThroughput = 0;
    /**
     * Group Commit 行数
     */
    private double groupCommitRow = 0;
    /**
     * 数据在队列中的延迟，攒批延迟
     */
    private double binlogQueueLatency = 0;
    /**
     * 写OSS延迟
     */
    private double ossWriteLatency = 0;
    /**
     * 主流程数据一致性锁等待时间
     */
    private double dataConsistencyLockGroupCommit = 0;
    /**
     * ORC 文件数量
     */
    private double orcCount = 0;
    /**
     * ORC 文件大小
     */
    private double orcSize = 0;
    /**
     * DEL 文件数量
     */
    private double delCount = 0;
    /**
     * DEL 文件大小
     */
    private double delSize = 0;
    /**
     * CSV 文件个数
     */
    private double csvCount = 0;
    /**
     * CSV 文件大小
     */
    private double csvSize = 0;
    /**
     * Compaction 任务数量
     */
    private double compactionTaskCount = 0;
    /**
     * Compaction 文件大小
     */
    private double compactionFileSize = 0;
    /**
     * Compaction 数据一致性锁等待时间
     */
    private double dataConsistencyLockCompaction = 0;
    /**
     * Compaction 读占用的 oss 带宽
     */
    private double compactionReadOssBandWidth = 0;
    /**
     * Compaction 写占用的 oss 带宽
     */
    private double compactionWriteOssBandWidth = 0;
    /**
     * KV写入量
     */
    private double pkIdxWriteCount = 0;
    /**
     * KV读取量
     */
    private double pkIdxReadCount = 0;
    /**
     * log写入字节数
     */
    private double pkIdxLogBytes = 0;
    /**
     * SST读取量（评估读放大）
     */
    private double pkIdxSstReadCount = 0;
    /**
     * SST读取缓存次数
     */
    private double pkIdxSstMemGetCount = 0;
    /**
     * SST读取缓存miss次数
     */
    private double pkIdxSstMemMissCount = 0;
    /**
     * 读本地磁盘缓存次数
     */
    private double pkIdxLocalReadCount = 0;
    /**
     * 读本地磁盘缓存字节数
     */
    private double pkIdxLocalReadBytes = 0;
    /**
     * 读远程存储次数
     */
    private double pkIdxRemoteReadCount = 0;
    /**
     * 读远程存储字节数
     */
    private double pkIdxRemoteReadBytes = 0;
    /**
     * 生成SST个数
     */
    private double pkIdxSstWriteCount = 0;
    /**
     * 生成SST字节数
     */
    private double pkIdxSstWriteBytes = 0;
    /**
     * log crash recovery次数
     */
    private double pkIdxLogFixCount = 0;
    /**
     * Binlog 网络吞吐
     */
    private double binlogNetThroughput = 0;
    /**
     * Binlog 事件吞吐
     */
    private double binlogEventThroughput = 0;
    /**
     * Binlog insert 事件数量
     */
    private double binlogEventInsert = 0;
    /**
     * Binlog delete 事件数量
     */
    private double binlogEventDelete = 0;
    /**
     * Binlog update 事件数量
     */
    private double binlogEventUpdate = 0;
    /**
     * Binlog ddl 事件数量
     */
    private double binlogEventDdl = 0;
    /**
     * Binlog insert 事件影响的行数
     */
    private double binlogEventInsertRows = 0;
    /**
     * Binlog delete 事件影响的行数
     */
    private double binlogEventDeleteRows = 0;
    /**
     * Binlog update 事件影响的行数
     */
    private double binlogEventUpdateRows = 0;
    /**
     * Binlog 事务个数
     */
    private double binlogTrxCount = 0;
    /**
     * Binlog 接收延迟
     */
    private double binlogReadLatency = 0;

    public ColumnarMetrics snapshot() {
        ColumnarMetrics snapshot = new ColumnarMetrics();
        getColumnarMetricsMap();
        getMetricsByHttp();

        snapshot.indexCount = this.indexCount;
        snapshot.partitionCount = this.partitionCount;
        snapshot.groupCommitLatency = this.groupCommitLatency;
        snapshot.groupCommitThroughput = this.groupCommitThroughput;
        snapshot.groupCommitRow = this.groupCommitRow;
        snapshot.binlogQueueLatency = this.binlogQueueLatency;
        snapshot.ossWriteLatency = this.ossWriteLatency;
        snapshot.dataConsistencyLockGroupCommit = this.dataConsistencyLockGroupCommit;
        snapshot.orcCount = this.orcCount;
        snapshot.orcSize = this.orcSize;
        snapshot.delCount = this.delCount;
        snapshot.delSize = this.delSize;
        snapshot.csvCount = this.csvCount;
        snapshot.csvSize = this.csvSize;
        snapshot.compactionTaskCount = this.compactionTaskCount;
        snapshot.compactionFileSize = this.compactionFileSize;
        snapshot.dataConsistencyLockCompaction = this.dataConsistencyLockCompaction;
        snapshot.compactionReadOssBandWidth = this.compactionReadOssBandWidth;
        snapshot.compactionWriteOssBandWidth = this.compactionWriteOssBandWidth;
        snapshot.pkIdxWriteCount = this.pkIdxWriteCount;
        snapshot.pkIdxReadCount = this.pkIdxReadCount;
        snapshot.pkIdxLogBytes = this.pkIdxLogBytes;
        snapshot.pkIdxSstReadCount = this.pkIdxSstReadCount;
        snapshot.pkIdxSstMemGetCount = this.pkIdxSstMemGetCount;
        snapshot.pkIdxSstMemMissCount = this.pkIdxSstMemMissCount;
        snapshot.pkIdxLocalReadCount = this.pkIdxLocalReadCount;
        snapshot.pkIdxLocalReadBytes = this.pkIdxLocalReadBytes;
        snapshot.pkIdxRemoteReadCount = this.pkIdxRemoteReadCount;
        snapshot.pkIdxRemoteReadBytes = this.pkIdxRemoteReadBytes;
        snapshot.pkIdxSstWriteCount = this.pkIdxSstWriteCount;
        snapshot.pkIdxSstWriteBytes = this.pkIdxSstWriteBytes;
        snapshot.pkIdxLogFixCount = this.pkIdxLogFixCount;
        snapshot.binlogNetThroughput = this.binlogNetThroughput;
        snapshot.binlogEventThroughput = this.binlogEventThroughput;
        snapshot.binlogEventInsert = this.binlogEventInsert;
        snapshot.binlogEventDelete = this.binlogEventDelete;
        snapshot.binlogEventUpdate = this.binlogEventUpdate;
        snapshot.binlogEventDdl = this.binlogEventDdl;
        snapshot.binlogEventInsertRows = this.binlogEventInsertRows;
        snapshot.binlogEventDeleteRows = this.binlogEventDeleteRows;
        snapshot.binlogEventUpdateRows = this.binlogEventUpdateRows;
        snapshot.binlogTrxCount = this.binlogTrxCount;
        snapshot.binlogReadLatency = this.binlogReadLatency;

        return snapshot;
    }

    // -------------------------------------------------- constructor --------------------------------------------------
    private static final ColumnarMetrics COLUMNAR_METRICS;

    static {
        COLUMNAR_METRICS = new ColumnarMetrics();
    }

    private ColumnarMetrics() {
    }

    public static ColumnarMetrics get() {
        return COLUMNAR_METRICS;
    }

    // ---------------------------------------------------- setters ----------------------------------------------------

    // ---------------------------------------------------- getters ----------------------------------------------------

    public double getIndexCount() {
        return indexCount;
    }

    public double getPartitionCount() {
        return partitionCount;
    }

    public double getGroupCommitLatency() {
        return groupCommitLatency;
    }

    public double getGroupCommitThroughput() {
        return groupCommitThroughput;
    }

    public double getGroupCommitRow() {
        return groupCommitRow;
    }

    public double getBinlogQueueLatency() {
        return binlogQueueLatency;
    }

    public double getOssWriteLatency() {
        return ossWriteLatency;
    }

    public double getDataConsistencyLockGroupCommit() {
        return dataConsistencyLockGroupCommit;
    }

    public double getOrcCount() {
        return orcCount;
    }

    public double getOrcSize() {
        return orcSize;
    }

    public double getDelCount() {
        return delCount;
    }

    public double getDelSize() {
        return delSize;
    }

    public double getCsvCount() {
        return csvCount;
    }

    public double getCsvSize() {
        return csvSize;
    }

    public double getCompactionTaskCount() {
        return compactionTaskCount;
    }

    public double getCompactionFileSize() {
        return compactionFileSize;
    }

    public double getDataConsistencyLockCompaction() {
        return dataConsistencyLockCompaction;
    }

    public double getCompactionReadOssBandWidth() {
        return compactionReadOssBandWidth;
    }

    public double getCompactionWriteOssBandWidth() {
        return compactionWriteOssBandWidth;
    }

    public double getPkIdxWriteCount() {
        return pkIdxWriteCount;
    }

    public double getPkIdxReadCount() {
        return pkIdxReadCount;
    }

    public double getPkIdxLogBytes() {
        return pkIdxLogBytes;
    }

    public double getPkIdxSstReadCount() {
        return pkIdxSstReadCount;
    }

    public double getPkIdxSstMemGetCount() {
        return pkIdxSstMemGetCount;
    }

    public double getPkIdxSstMemMissCount() {
        return pkIdxSstMemMissCount;
    }

    public double getPkIdxLocalReadCount() {
        return pkIdxLocalReadCount;
    }

    public double getPkIdxLocalReadBytes() {
        return pkIdxLocalReadBytes;
    }

    public double getPkIdxRemoteReadCount() {
        return pkIdxRemoteReadCount;
    }

    public double getPkIdxRemoteReadBytes() {
        return pkIdxRemoteReadBytes;
    }

    public double getPkIdxSstWriteCount() {
        return pkIdxSstWriteCount;
    }

    public double getPkIdxSstWriteBytes() {
        return pkIdxSstWriteBytes;
    }

    public double getPkIdxLogFixCount() {
        return pkIdxLogFixCount;
    }

    public double getBinlogNetThroughput() {
        return binlogNetThroughput;
    }

    public double getBinlogEventThroughput() {
        return binlogEventThroughput;
    }

    public double getBinlogEventInsert() {
        return binlogEventInsert;
    }

    public double getBinlogEventDelete() {
        return binlogEventDelete;
    }

    public double getBinlogEventUpdate() {
        return binlogEventUpdate;
    }

    public double getBinlogEventDDL() {
        return binlogEventDdl;
    }

    public double getBinlogEventInsertRows() {
        return binlogEventInsertRows;
    }

    public double getBinlogEventDeleteRows() {
        return binlogEventDeleteRows;
    }

    public double getBinlogEventUpdateRows() {
        return binlogEventUpdateRows;
    }

    public double getBinlogTrxCount() {
        return binlogTrxCount;
    }

    public double getBinlogReadLatency() {
        return binlogReadLatency;
    }

    public void getMetricsByHttp() {
        ColumnarTaskConfigMapper mapper = SpringContextHolder.getObject(ColumnarTaskConfigMapper.class);
        Optional<ColumnarTaskConfig> opTask = mapper
            .selectOne(s -> s.where(ColumnarTaskConfigDynamicSqlSupport.role, IsEqualTo.of(() -> "Leader")));
        if (!opTask.isPresent()) {
            log.warn("Columnar leader not found in columnar_task_config");
            return;
        }

        ColumnarTaskConfig taskConfig = opTask.get();

        String url = String.format(COLUMNAR_METRICS_URL, taskConfig.getIp(), taskConfig.getPort());
        try {
            URL obj = new URL(url);
            HttpURLConnection con = (HttpURLConnection) obj.openConnection();
            con.setRequestMethod("GET");
            int responseCode = con.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                Gson gson = new Gson();
                ColumnarMetricsData columnarMetricsData = gson.fromJson(response.toString(), ColumnarMetricsData.class);
                List<CommonMetrics> metrics = columnarMetricsData.getMetrics();

                for (CommonMetrics metric : metrics) {
                    String metricName = metric.getKey();
                    double metricValue = metric.getValue();
                    String fieldName = columnarMetricFieldMap.get(metricName);
                    if (!fieldName.isEmpty()) {
                        columnarMetricValueMap.put(fieldName, metricValue);
                    }
                }

                buildMetricValue();
            } else {
                log.error("获取Columnar metrics失败：" + responseCode);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void buildMetricValue() {
        this.indexCount = columnarMetricValueMap.containsKey("indexCount") ?
            columnarMetricValueMap.get("indexCount") : 0;
        this.partitionCount = columnarMetricValueMap.containsKey("partitionCount") ?
            columnarMetricValueMap.get("partitionCount") : 0;
        this.groupCommitLatency = columnarMetricValueMap.containsKey("groupCommitLatency") ?
            columnarMetricValueMap.get("groupCommitLatency") : 0;
        this.groupCommitThroughput = columnarMetricValueMap.containsKey("groupCommitThroughput") ?
            columnarMetricValueMap.get("groupCommitThroughput") : 0;
        this.groupCommitRow = columnarMetricValueMap.containsKey("groupCommitRow") ?
            columnarMetricValueMap.get("groupCommitRow") : 0;
        this.binlogQueueLatency = columnarMetricValueMap.containsKey("binlogQueueLatency") ?
            columnarMetricValueMap.get("binlogQueueLatency") : 0;
        this.ossWriteLatency = columnarMetricValueMap.containsKey("ossWriteLatency") ?
            columnarMetricValueMap.get("ossWriteLatency") : 0;
        this.dataConsistencyLockGroupCommit = columnarMetricValueMap.containsKey("dataConsistencyLockGroupCommit") ?
            columnarMetricValueMap.get("dataConsistencyLockGroupCommit") : 0;
        this.orcCount = columnarMetricValueMap.containsKey("orcCount") ?
            columnarMetricValueMap.get("orcCount") : 0;
        this.orcSize = columnarMetricValueMap.containsKey("orcSize") ?
            columnarMetricValueMap.get("orcSize") : 0;
        this.delCount = columnarMetricValueMap.containsKey("delCount") ?
            columnarMetricValueMap.get("delCount") : 0;
        this.delSize = columnarMetricValueMap.containsKey("delSize") ?
            columnarMetricValueMap.get("delSize") : 0;
        this.csvCount = columnarMetricValueMap.containsKey("csvCount") ?
            columnarMetricValueMap.get("csvCount") : 0;
        this.csvSize = columnarMetricValueMap.containsKey("csvSize") ?
            columnarMetricValueMap.get("csvSize") : 0;
        this.compactionTaskCount = columnarMetricValueMap.containsKey("compactionTaskCount") ?
            columnarMetricValueMap.get("compactionTaskCount") : 0;
        this.compactionFileSize = columnarMetricValueMap.containsKey("compactionFileSize") ?
            columnarMetricValueMap.get("compactionFileSize") : 0;
        this.dataConsistencyLockCompaction = columnarMetricValueMap.containsKey("dataConsistencyLockCompaction") ?
            columnarMetricValueMap.get("dataConsistencyLockCompaction") : 0;
        this.compactionReadOssBandWidth = columnarMetricValueMap.containsKey("compactionReadOssBandWidth") ?
            columnarMetricValueMap.get("compactionReadOssBandWidth") : 0;
        this.compactionWriteOssBandWidth = columnarMetricValueMap.containsKey("compactionWriteOssBandWidth") ?
            columnarMetricValueMap.get("compactionWriteOssBandWidth") : 0;
        this.pkIdxWriteCount = columnarMetricValueMap.containsKey("pkIdxWriteCount") ?
            columnarMetricValueMap.get("pkIdxWriteCount") : 0;
        this.pkIdxReadCount = columnarMetricValueMap.containsKey("pkIdxReadCount") ?
            columnarMetricValueMap.get("pkIdxReadCount") : 0;
        this.pkIdxLogBytes = columnarMetricValueMap.containsKey("pkIdxLogBytes") ?
            columnarMetricValueMap.get("pkIdxLogBytes") : 0;
        this.pkIdxSstReadCount = columnarMetricValueMap.containsKey("pkIdxSstReadCount") ?
            columnarMetricValueMap.get("pkIdxSstReadCount") : 0;
        this.pkIdxSstMemGetCount = columnarMetricValueMap.containsKey("pkIdxSstMemGetCount") ?
            columnarMetricValueMap.get("pkIdxSstMemGetCount") : 0;
        this.pkIdxSstMemMissCount = columnarMetricValueMap.containsKey("pkIdxSstMemMissCount") ?
            columnarMetricValueMap.get("pkIdxSstMemMissCount") : 0;
        this.pkIdxLocalReadCount = columnarMetricValueMap.containsKey("pkIdxLocalReadCount") ?
            columnarMetricValueMap.get("pkIdxLocalReadCount") : 0;
        this.pkIdxLocalReadBytes = columnarMetricValueMap.containsKey("pkIdxLocalReadBytes") ?
            columnarMetricValueMap.get("pkIdxLocalReadBytes") : 0;
        this.pkIdxRemoteReadCount = columnarMetricValueMap.containsKey("pkIdxRemoteReadCount") ?
            columnarMetricValueMap.get("pkIdxRemoteReadCount") : 0;
        this.pkIdxRemoteReadBytes = columnarMetricValueMap.containsKey("pkIdxRemoteReadBytes") ?
            columnarMetricValueMap.get("pkIdxRemoteReadBytes") : 0;
        this.pkIdxSstWriteCount = columnarMetricValueMap.containsKey("pkIdxSstWriteCount") ?
            columnarMetricValueMap.get("pkIdxSstWriteCount") : 0;
        this.pkIdxSstWriteBytes = columnarMetricValueMap.containsKey("pkIdxSstWriteBytes") ?
            columnarMetricValueMap.get("pkIdxSstWriteBytes") : 0;
        this.pkIdxLogFixCount = columnarMetricValueMap.containsKey("pkIdxLogFixCount") ?
            columnarMetricValueMap.get("pkIdxLogFixCount") : 0;
        this.binlogNetThroughput = columnarMetricValueMap.containsKey("binlogNetThroughput") ?
            columnarMetricValueMap.get("binlogNetThroughput") : 0;
        this.binlogEventThroughput = columnarMetricValueMap.containsKey("binlogEventThroughput") ?
            columnarMetricValueMap.get("binlogEventThroughput") : 0;
        this.binlogEventInsert = columnarMetricValueMap.containsKey("binlogEventInsert") ?
            columnarMetricValueMap.get("binlogEventInsert") : 0;
        this.binlogEventDelete = columnarMetricValueMap.containsKey("binlogEventDelete") ?
            columnarMetricValueMap.get("binlogEventDelete") : 0;
        this.binlogEventUpdate = columnarMetricValueMap.containsKey("binlogEventUpdate") ?
            columnarMetricValueMap.get("binlogEventUpdate") : 0;
        this.binlogEventDdl = columnarMetricValueMap.containsKey("binlogEventDdl") ?
            columnarMetricValueMap.get("binlogEventDdl") : 0;
        this.binlogEventInsertRows = columnarMetricValueMap.containsKey("binlogEventInsertRows") ?
            columnarMetricValueMap.get("binlogEventInsertRows") : 0;
        this.binlogEventDeleteRows = columnarMetricValueMap.containsKey("binlogEventDeleteRows") ?
            columnarMetricValueMap.get("binlogEventDeleteRows") : 0;
        this.binlogEventUpdateRows = columnarMetricValueMap.containsKey("binlogEventUpdateRows") ?
            columnarMetricValueMap.get("binlogEventUpdateRows") : 0;
        this.binlogTrxCount = columnarMetricValueMap.containsKey("binlogTrxCount") ?
            columnarMetricValueMap.get("binlogTrxCount") : 0;
        this.binlogReadLatency = columnarMetricValueMap.containsKey("binlogReadLatency") ?
            columnarMetricValueMap.get("binlogReadLatency") : 0;
    }

    public static class ColumnarMetricsData {
        private List<CommonMetrics> metrics;

        public List<CommonMetrics> getMetrics() {
            return metrics;
        }

        public void setMetrics(List<CommonMetrics> metrics) {
            this.metrics = metrics;
        }
    }

    public void getColumnarMetricsMap() {
        if (INITED.getAndSet(true)) {
            return;
        }
        List<String> lines;
        try {
            lines = FileUtils.readLines(
                new File(Resources.getResource("metrics.txt").getFile()), StandardCharsets.UTF_8);
            log.info("init metrics lines {}", lines);
            for (String line : lines) {
                String[] ss = StringUtils.split(line, "|");
                if (ss.length < 5) {
                    continue;
                }
                if (ss[0].contains("_columnar_")) {
                    columnarMetricFieldMap.put(ss[0], ss[3]);
                }
            }

            log.info("init columnar metrics maps done {}", columnarMetricFieldMap);
        } catch (IOException e) {
            log.error("prepare columnar metrics map fail", e);
        }
    }
}
