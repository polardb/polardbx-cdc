/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.SearchMetricsManager;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.URLLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class DryRunMemoryDownload {

    public static Logger logger = LoggerFactory.getLogger("consoleLogger");

    private String url;

    public String storageInstanceId = "test-dn";
    public String fileName = "test-file";

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }

    public URLLogFetcher createFetcher() {
        return new URLLogFetcher(storageInstanceId, fileName);
    }

    public void dryRun() throws IOException {
        final SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
        String storageInstanceId = "test-dn";
        String fileName = "test-file";
        URLLogFetcher urlLogFetcher = createFetcher();
        SearchMetricsManager.setLogger(logger);
        SearchMetricsManager.getInstance().startSearch();
        final int newThreadNum =
            DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_THREAD_NUM);
        ExecutorService directConsumeExecutor = new ThreadPoolExecutor(1, newThreadNum,
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(8192),
            r -> {
                Thread t = new Thread(r, storageInstanceId + "oss-download-thread");
                t.setDaemon(true);
                return t;
            });

        try {
            urlLogFetcher.open(url, -1L, directConsumeExecutor);
            LogDecoder decoder = new LogDecoder();
            decoder.handle(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
            LogContext lc = new LogContext();
            lc.setServerCharactorSet(new ServerCharactorSet());
            lc.setLogPosition(new LogPosition(fileName, 0));
            while (urlLogFetcher.fetch()) {
                LogBuffer buffer = urlLogFetcher.buffer();
                LogEvent le = decoder.decode(buffer, lc);
            }

            urlLogFetcher.close();
        } finally {
            SearchMetricsManager.getInstance().stopSearch(storageInstanceId);
            SearchMetricsManager.resetLogger();
        }
    }

    public static void main(String[] args) throws IOException {
        // spring context
        DryRunMemoryDownload dryRunMemoryDownload = new DryRunMemoryDownload();
        dryRunMemoryDownload.setUrl(args[0]);
        dryRunMemoryDownload.dryRun();
    }
}
