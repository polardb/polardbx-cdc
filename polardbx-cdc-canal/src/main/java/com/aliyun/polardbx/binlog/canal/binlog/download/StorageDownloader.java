/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class StorageDownloader {
    private static final Logger logger = LoggerFactory.getLogger("rdsDownloadLogger");
    private final LinkedBlockingQueue<DownloadTask> downloadTasks = new LinkedBlockingQueue<>();
    private final ThreadPoolExecutor executorService;
    private final DownloadBarrier barrier;
    private final String storageInstance;
    private boolean run = false;
    private Thread mainDownloadThread;
    private final String path;
    private Throwable t;

    public StorageDownloader(String storageInstance, String path) {
        this.storageInstance = storageInstance;
        this.path = path;
        int downloadNum = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_THREAD_INIT_NUM);
        logger.warn("init downloader with dn : {} thread num : {}", storageInstance, downloadNum);
        this.executorService =
            new ThreadPoolExecutor(downloadNum, downloadNum, 1, TimeUnit.HOURS, new LinkedBlockingQueue<>(5), r -> {
                Thread t = new Thread(r, "rds_binlog_download_thread");
                t.setDaemon(true);
                return t;
            });
        this.barrier = new DownloadBarrier(path, executorService);
        ;

    }

    private void prepare() {
        try {
            FileUtils.forceMkdir(new File(path));
            FileUtils.cleanDirectory(new File(path));
        } catch (IOException e) {
            throw new PolardbxException("Clean local binlog directory failed.", e);
        }
    }

    public void start() {

        prepare();

        run = true;
        mainDownloadThread = new Thread(() -> {
            while (run) {
                try {
                    executeDownload();
                    if (barrier.testException()) {
                        logger.error("{} main download thread detected barrier has exception, will exit!",
                            storageInstance);
                        t = barrier.getException();
                        break;
                    }
                } catch (Throwable e) {
                    logger.error("{} main download thread error", storageInstance, e);
                    t = e;
                }
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(1));
                } catch (Exception e) {
                    t = e;
                    break;
                }
            }
            logger.info("all task finished! {} downloader thread exit!", storageInstance);
            executorService.shutdown();
        }, storageInstance + "-dispatcher-thread");
        mainDownloadThread.setDaemon(true);
        mainDownloadThread.start();
    }

    public void addTask(DownloadTask downloadTask) {
        downloadTask.registerListener(this.barrier);
        this.downloadTasks.add(downloadTask);
    }

    public void executeDownload() throws InterruptedException {
        logger.info("try begin download : {} queue size : [{}]", storageInstance, downloadTasks.size());
        if (!barrier.waitDownload(storageInstance)) {
            return;
        }
        DownloadTask task = downloadTasks.poll(1, TimeUnit.SECONDS);
        if (task == null) {
            run = false;
            return;
        }
        logger.info("{} add to executor task {}", storageInstance, task.getLocalFilePath());
        executorService.execute(task);

    }

    public Throwable getException() {
        return t;
    }

    public void stop() throws InterruptedException {
        logger.info("try stop storage {} downloader ", storageInstance);
        run = false;
        if (mainDownloadThread != null) {
            mainDownloadThread.interrupt();
            mainDownloadThread.join();
            logger.info("main dispatcher download task thread terminate success!");
        }
        if (executorService != null) {
            executorService.shutdownNow();
            executorService.awaitTermination(10, TimeUnit.SECONDS);
            logger.info("download thread executors terminate success!");
        }
        logger.info("stop storage {} downloader success!", storageInstance);
    }
}
