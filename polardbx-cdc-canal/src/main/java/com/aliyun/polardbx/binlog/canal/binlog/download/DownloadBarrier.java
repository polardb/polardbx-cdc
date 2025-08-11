/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.util.StorageUnit;
import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadBarrier implements DownloadTaskListener {

    private static final Logger logger = LoggerFactory.getLogger("rdsDownloadLogger");
    private Throwable t;
    private final AtomicInteger downloadTaskCounter = new AtomicInteger(0);
    private final ThreadPoolExecutor executor;
    private final String binlogDirPath;

    public DownloadBarrier(String binlogDirPath, ThreadPoolExecutor executor) {
        this.binlogDirPath = binlogDirPath;
        this.executor = executor;
    }

    public boolean waitDownload(String storageInstance) throws InterruptedException {
        if (!testDiskUse(storageInstance)) {
            Thread.sleep(100);
            return false;
        }
        return executor.getCorePoolSize() > downloadTaskCounter.get();
    }

    @Override
    public void beginDownload(String storageInstanceId) {
        downloadTaskCounter.incrementAndGet();
    }

    @Override
    public void catchException(Throwable t) {
        this.t = t;
    }

    public boolean testException() {
        return t != null;
    }

    public Throwable getException() {
        return t;
    }

    @Override
    public void endDownload(String storageInstanceId) {
        downloadTaskCounter.decrementAndGet();
    }

    private boolean testDiskUse(String storageInstanceId) {
        Map<String, Long> localStorageUseSizeMap = Maps.newHashMap();
        long totalUseSize = buildLocalUseSize(localStorageUseSizeMap);

        long binlogFileSize = StorageUnit.bToM(DynamicApplicationConfig.getInt(ConfigKeys.BINLOG_FILE_SIZE));
        Long diskSize = localStorageUseSizeMap.get(storageInstanceId);
        if (diskSize == null) {
            diskSize = 0L;
        }
        if (diskSize + downloadTaskCounter.get() * binlogFileSize > DynamicApplicationConfig.getInt(
            ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DISK_LIMIT_PER_DN)) {
            logger.info("storage " + storageInstanceId + " reach disk limit, will not download binlog files!");
            return false;
        }

        int diskLimit = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DISK_LIMIT_TOTAL);

        long use = downloadTaskCounter.get() * binlogFileSize + totalUseSize;
        boolean ret = use < diskLimit;
        if (!ret) {
            logger.info("total storage reach disk limit, will not download binlog files!");
        }
        return ret;
    }

    private long buildLocalUseSize(Map<String, Long> localStorageUseSizeMap) {
        File binlogDir = new File(binlogDirPath);
        long totalSize = 0;
        File[] storageDirs = binlogDir.listFiles(File::isDirectory);
        for (File sd : storageDirs) {
            String storageName = sd.getName();
            File bfs[] = sd.listFiles((dir, name) -> {
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
