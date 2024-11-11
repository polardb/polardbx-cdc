/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.util.StorageUnit;
import com.aliyun.polardbx.binlog.util.WgetContext;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadBarrier implements DownloadTaskListener {

    private static final long ADJUST_INTERVAL = TimeUnit.SECONDS.toMillis(10);
    private static final Logger logger = LoggerFactory.getLogger("rdsDownloadLogger");
    private static long DISK_SIZE = DynamicApplicationConfig.getInt(ConfigKeys.DISK_SIZE);
    private Throwable t;
    private Map<String, AtomicInteger> downloadTaskCounterMap = Maps.newConcurrentMap();
    private ThreadPoolExecutor executor;
    private String binlogDirPath;
    private long lastSpeed;
    private long lastAdjustTime = System.currentTimeMillis();
    private int dir = 1;

    public DownloadBarrier(String binlogDirPath, ThreadPoolExecutor executor) {
        this.binlogDirPath = binlogDirPath;
        this.executor = executor;
    }

    public boolean waitDownload(String storageInstance) {
        if (!testDiskUse(storageInstance)) {
            try {
                Thread.sleep(TimeUnit.SECONDS.toMillis(10));
            } catch (InterruptedException e) {
            }
            return false;
        }
        optBestSpeed();
        return executor.getCorePoolSize() > WgetContext.size();
    }

    @Override
    public void beginDownload(String storageInstanceId) {
        AtomicInteger counter = downloadTaskCounterMap.get(storageInstanceId);
        if (counter == null) {
            synchronized (this) {
                counter = downloadTaskCounterMap.get(storageInstanceId);
                if (counter == null) {
                    counter = new AtomicInteger(0);
                    downloadTaskCounterMap.put(storageInstanceId, counter);
                }
            }
        }
        counter.incrementAndGet();
    }

    @Override
    public void catchException(Throwable t) {
        this.t = t;
    }

    public boolean testException() {
        return t != null;
    }

    @Override
    public void endDownload(String storageInstanceId) {
        downloadTaskCounterMap.get(storageInstanceId).decrementAndGet();
    }

    private boolean testDiskUse(String storageInstanceId) {
        Map<String, Long> localStorageUseSizeMap = Maps.newHashMap();
        long totalUseSize = buildLocalUseSize(localStorageUseSizeMap);

        AtomicInteger atomicInteger = downloadTaskCounterMap.get(storageInstanceId);
        if (atomicInteger == null) {
            return true;
        }

        long binlogFileSize = StorageUnit.bToM(DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_FILE_SIZE));

        if (localStorageUseSizeMap.get(storageInstanceId) + atomicInteger.get() * binlogFileSize
            > DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DISK_LIMIT_PER_DN)) {
            logger.info("storage " + storageInstanceId + " reach disk limit, will not download binlog files!");
            return false;
        }

        int diskLimit = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DISK_LIMIT_TOTAL);

        long use = atomicInteger.get() * binlogFileSize + totalUseSize;
        boolean ret = use < diskLimit;
        if (!ret) {
            logger.info("total storage reach disk limit, will not download binlog files!");
        }
        return ret;
    }

    private void optBestSpeed() {
        long now = System.currentTimeMillis();
        if (now - lastAdjustTime < ADJUST_INTERVAL) {
            return;
        }
        lastAdjustTime = now;
        int maxThreadLimit =
            DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_THREAD_MAX_NUM);
        logger.info("poolSize : " + executor.getCorePoolSize() + " , run thread size : " + WgetContext.size());
        long currentSpeed = WgetContext.totalSpeed();
        logger.info(
            "total download speed: " + StorageUnit.bToM(currentSpeed) + "M(" + currentSpeed + ") , pre download speed: "
                + StorageUnit.bToM(lastSpeed) + "M(" + lastSpeed + ")");
        if (executor.getCorePoolSize() == WgetContext.size()) {
            if (lastSpeed > 0) {
                if (lastSpeed > currentSpeed) {
                    dir = -dir;
                }
                int nextCorePoolSize = executor.getCorePoolSize();
                nextCorePoolSize = nextCorePoolSize + dir;
                if (nextCorePoolSize > maxThreadLimit) {
                    nextCorePoolSize = maxThreadLimit;
                }
                executor.setCorePoolSize(nextCorePoolSize);
                executor.setMaximumPoolSize(nextCorePoolSize);
                logger.info("adjust rds binlog download size : " + nextCorePoolSize);
            }
        }
        lastSpeed = currentSpeed;
    }

    private long buildLocalUseSize(Map<String, Long> localStorageUseSizeMap) {
        File binlogDir = new File(binlogDirPath);
        long totalSize = 0;
        File[] storageDirs = binlogDir.listFiles(File::isDirectory);
        for (File sd : storageDirs) {
            String storageName = sd.getName();
            File bfs[] = sd.listFiles(
                (dir, name) -> {
                    int idx = name.indexOf(".");
                    if (idx < 0) {
                        return false;
                    }
                    return name.substring(idx + 1).matches("\\d+");
                });
            long useSize = 0;
            for (File f : bfs) {
                useSize += f.length();
            }
            localStorageUseSizeMap.put(storageName, StorageUnit.bToM(useSize));
            totalSize += useSize;
        }
        return StorageUnit.bToM(totalSize);
    }
}
