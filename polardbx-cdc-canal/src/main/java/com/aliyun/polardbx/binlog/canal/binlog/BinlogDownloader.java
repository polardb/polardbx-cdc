/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadBarrier;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTask;
import com.aliyun.polardbx.binlog.canal.binlog.download.StorageDownloader;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class BinlogDownloader {

    private static final Logger logger = LoggerFactory.getLogger("rdsDownloadLogger");
    private static final BinlogDownloader instance = new BinlogDownloader();

    private String path;
    private boolean run;
    private volatile Throwable taskException;
    private ThreadPoolExecutor executorService;
    private Thread mainDownloadThread;
    private boolean init = false;
    private DownloadBarrier barrier;
    private LoadingCache<String, StorageDownloader> storageDownloaderMap = CacheBuilder.newBuilder().build(
        new CacheLoader<String, StorageDownloader>() {
            @Override
            public StorageDownloader load(String s) throws Exception {
                return new StorageDownloader(s, barrier, executorService);
            }
        });

    public static BinlogDownloader getInstance() {
        return instance;
    }

    public void init(String path, final int storageCount) {
        logger.warn("init downloader with dn count : " + storageCount);

        this.path = path;
        int downloadNum = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_THREAD_INIT_NUM);
        this.executorService = new ThreadPoolExecutor(downloadNum, downloadNum, 1, TimeUnit.HOURS,
            new LinkedBlockingQueue<>(5), r -> {
            Thread t = new Thread(r, "rds_binlog_download_thread");
            t.setDaemon(true);
            return t;
        });
        this.barrier = new DownloadBarrier(path, executorService);
        this.init = true;
    }

    public void addDownloadTask(String storageInstanceId, DownloadTask task) throws ExecutionException {
        StorageDownloader downloader = storageDownloaderMap.get(storageInstanceId);
        downloader.addTask(task);
    }

    public void start() {
        if (!init) {
            logger.warn("not init binlog downloader ,will not start!");
            return;
        }

        prepare();

        run = true;
        mainDownloadThread = new Thread(() -> {
            while (run) {
                try {
                    for (StorageDownloader downloader : storageDownloaderMap.asMap().values()) {
                        logger.info(
                            "begin download : " + downloader.getStorageInstance() + "[" + downloader.taskSize() + "]");
                        downloader.executeDownload();
                    }
                    if (barrier.testException()) {
                        Runtime.getRuntime().halt(1);
                    }
                } catch (Throwable e) {
                    logger.error("main download thread error", e);
                }
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (InterruptedException e) {
                    throw new PolardbxException(e);
                }
            }
        }, "main-download-thread");
        mainDownloadThread.setDaemon(true);
        mainDownloadThread.start();
    }

    private void prepare() {
        try {
            FileUtils.forceMkdir(new File(path));
            FileUtils.cleanDirectory(new File(path));
        } catch (IOException e) {
            throw new PolardbxException("Clean local binlog directory failed.", e);
        }
    }

    public void stop() {
        run = false;
        if (mainDownloadThread != null) {
            mainDownloadThread.interrupt();
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }
}
