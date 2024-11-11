/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class StorageDownloader {
    private static final Logger logger = LoggerFactory.getLogger("rdsDownloadLogger");
    private LinkedBlockingQueue<DownloadTask> downloadTasks = new LinkedBlockingQueue<>();
    private ThreadPoolExecutor executorService;
    private DownloadBarrier barrier;
    private String storageInstance;

    public StorageDownloader(String storageInstance, DownloadBarrier barrier, ThreadPoolExecutor executorService) {
        this.storageInstance = storageInstance;
        this.barrier = barrier;
        this.executorService = executorService;
    }

    public void addTask(DownloadTask downloadTask) {
        downloadTask.registerListener(this.barrier);
        this.downloadTasks.add(downloadTask);
    }

    public String getStorageInstance() {
        return storageInstance;
    }

    public int taskSize() {
        return downloadTasks.size();
    }

    public void executeDownload() throws InterruptedException {
        if (!barrier.waitDownload(storageInstance)) {
            return;
        }
        DownloadTask task =
            downloadTasks.poll(1, TimeUnit.SECONDS);
        if (task == null) {
            return;
        }
        logger.info(storageInstance + " add to executor task " + task.getLocalFilePath());
        while (true) {
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            try {
                executorService.execute(task);
                break;
            } catch (Exception e) {
                logger.info(e.getMessage());
                if (executorService.isShutdown()) {
                    break;
                } else {
                    Thread.sleep(1000L);
                }
            }
        }
    }
}
