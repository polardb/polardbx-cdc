/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheManager;
import com.aliyun.polardbx.binlog.canal.unit.SearchRecorder;
import com.aliyun.polardbx.binlog.util.format.TableFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class SearchMetricsManager {

    private static final SearchMetricsManager instance = new SearchMetricsManager();
    private static Logger logger = LoggerFactory.getLogger("searchLogger");
    private static final Logger searchLogger = LoggerFactory.getLogger("searchLogger");

    private final AtomicInteger startedRefCount = new AtomicInteger(0);
    private final AtomicBoolean started = new AtomicBoolean(false);
    private ScheduledExecutorService executorService;
    private long millsecondDownloadFile = 0;
    private long countDownloadFile = 0;
    private long millsecondPerFile = -1;

    private SearchMetricsManager() {
    }

    public static SearchMetricsManager getInstance() {
        return instance;
    }

    private ConcurrentHashMap<String, SearchRecorder> searchRecorderMap = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, MultiPartStreamMetrics> storageMetricsMap = new ConcurrentHashMap<>();

    public static void setLogger(Logger logger) {
        SearchMetricsManager.logger = logger;
    }

    public static void resetLogger() {
        SearchMetricsManager.logger = searchLogger;
    }

    public boolean metrics(String storageInstance) {
        return storageMetricsMap.containsKey(storageInstance);
    }

    public void startSearch() {
        startedRefCount.incrementAndGet();
        if (!started.compareAndSet(false, true)) {
            return;
        }
        executorService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "search-metrics");
            t.setDaemon(true);
            return t;
        });

        executorService.scheduleAtFixedRate(this::printMetrics, 1, 1, java.util.concurrent.TimeUnit.SECONDS);
    }

    public synchronized SearchRecorder getSearchRecorder(String storageInstance) {
        SearchRecorder searchRecorder = searchRecorderMap.get(storageInstance);
        if (searchRecorder == null) {
            searchRecorder = new SearchRecorder(storageInstance);
            searchRecorderMap.put(storageInstance, searchRecorder);
        }
        return searchRecorder;
    }

    public void stopSearch(String storageInstance) {
        startedRefCount.decrementAndGet();
        if (startedRefCount.get() <= 0 && started.compareAndSet(true, false)) {
            executorService.shutdownNow();
            executorService = null;
        }
        searchRecorderMap.remove(storageInstance);
    }

    public void put(String storageInstance, MultiPartStreamMetrics metrics) {
        MultiPartStreamMetrics oldFileMetrics = storageMetricsMap.put(storageInstance, metrics);
        if (oldFileMetrics != null && oldFileMetrics.isFinish()) {
            synchronized (this) {
                millsecondDownloadFile += oldFileMetrics.getFinishedTime() - oldFileMetrics.getStartTime();
                countDownloadFile++;
            }
        }
    }

    public void remove(String storageInstance) {
        storageMetricsMap.remove(storageInstance);
    }

    public String formatDate(long timestamp, String defaultValue) {
        if (timestamp <= 0) {
            return defaultValue;
        } else {
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(timestamp);
        }
    }

    public int remainQueueSize(SearchRecorder recorder) {
        List<String> queueList = recorder.getQueueList();
        String binlogFileName = recorder.getFileName();
        if (queueList == null || binlogFileName == null) {
            return -1;
        }
        return queueList.indexOf(binlogFileName) + 1;
    }

    public String progress(SearchRecorder recorder) {
        if (recorder.getSize() <= 0) {
            return "0%";
        }

        return ((int) (recorder.getPosition() * 100 / recorder.getSize())) + "%";
    }

    public String getHumanReadableSpeed(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        int unit = 1024;
        String[] units = new String[] {"KB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"};
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        double value = bytes / Math.pow(unit, exp);

        // 格式化输出，保留两位小数
        return String.format("%.2f %s", value, units[exp - 1]);
    }

    public void printMetrics() {
        StringBuilder sb = new StringBuilder();
        sb.append("**************** cache metrics: begin *******************\n");
        CacheManager cacheManager = SpringContextHolder.getObject(CacheManager.class);
        sb.append(cacheManager.buildCacheDetail());
        sb.append("**************** cache metrics: end *******************\n");
        sb.append("**************** download metrics: begin *******************\n");
        synchronized (this) {
            if (countDownloadFile != 0) {
                millsecondPerFile = millsecondDownloadFile / countDownloadFile;
                millsecondDownloadFile = 0;
                countDownloadFile = 0;
            }
        }
        sb.append("finished download file cost avg : ").append(millsecondPerFile).append("ms\n");
        for (MultiPartStreamMetrics metrics : storageMetricsMap.values()) {
            sb.append(metrics.getStorageInstanceId()).append(":\n");
            sb.append(metrics.getFileName()).append(":");
            long bps = 0;
            for (PartStreamMetrics partStreamMetrics : metrics.getPartMetrics()) {
                if (!partStreamMetrics.isAllocateBuffer()) {
                    sb.append("[x]");
                } else {
                    int progress = (int) (partStreamMetrics.getBytesRead() * 100 / partStreamMetrics.getTotalBytes());
                    sb.append("[").append(progress).append("%]");
                }
                bps += partStreamMetrics.getBps();
            }
            sb.append("costs: ").append(System.currentTimeMillis() - metrics.getStartTime()).append("ms , ")
                .append(getHumanReadableSpeed(bps)).append("/s  \n");
        }
        sb.append("**************** download metrics: end *******************\n");

        if (!searchRecorderMap.isEmpty()) {
            sb.append("**************** search metrics: begin *******************\n");
            TableFormat tableFormat = new TableFormat("search metrics");
            tableFormat.addColumn("storage", "file", "position", "timestamp", "size", "progress", "search time(ms)",
                "quick mode", "binlog-queue-size");

            for (SearchRecorder searchRecorder : searchRecorderMap.values()) {
                tableFormat.addRow(searchRecorder.getStorageName(), searchRecorder.getFileName(),
                    searchRecorder.getPosition(), formatDate(searchRecorder.getTimestamp() * 1000, "null"),
                    searchRecorder.getSize(), progress(searchRecorder),
                    formatDate(searchRecorder.getSearchTime(), "CdcStart"), searchRecorder.isQuickMode(),
                    remainQueueSize(searchRecorder));
            }
            sb.append(tableFormat.print());

            sb.append("unComplete tran: \n");
            for (SearchRecorder searchRecorder : searchRecorderMap.values()) {
                sb.append(searchRecorder.getStorageName()).append(":\n\t\t");
                sb.append(searchRecorder.getUnCompleteTran()).append("\n");
            }
            sb.append("**************** search metrics: end   *******************\n");
        }

        logger.info(sb.toString());
    }
}
