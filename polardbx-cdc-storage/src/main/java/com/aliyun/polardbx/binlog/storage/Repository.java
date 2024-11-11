/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.jvm.JvmUtils;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.rocksdb.RocksDB;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_CHECK_INTERVAL_MILLS;

/**
 * 磁盘存储
 **/
@Slf4j
public class Repository {
    private final String basePath;
    private final PersistMode persistMode;
    private final Double persistNewThreshold;
    private final int txnPersistThreshold;
    private final int txnItemPersistThreshold;
    private final boolean isPersistOn;
    private final DeleteMode deleteMode;
    private final int repoUnitCount;
    private final List<RepoUnit> repoUnits;
    private final AtomicBoolean exceedPersistThreshold;
    private final AtomicBoolean isStarted;
    private PersistCheckResult persistCheckResult;

    public Repository(boolean isPersistOn, String basePath, PersistMode persistMode, double persistNewThreshold,
                      int txnPersistThreshold, int txnItemPersistThreshold, DeleteMode deleteMode, int repoUnitCount) {
        this.isPersistOn = isPersistOn;
        this.basePath = basePath;
        this.persistMode = persistMode;
        this.persistNewThreshold = persistNewThreshold;
        this.txnPersistThreshold = txnPersistThreshold;
        this.txnItemPersistThreshold = txnItemPersistThreshold;
        this.deleteMode = deleteMode;
        this.repoUnitCount = repoUnitCount;
        this.repoUnits = new ArrayList<>(repoUnitCount);
        this.exceedPersistThreshold = new AtomicBoolean(false);
        this.isStarted = new AtomicBoolean(false);
        this.persistCheckResult = new PersistCheckResult(false, System.currentTimeMillis());
    }

    public void open() {
        if (isStarted.compareAndSet(false, true)) {
            try {
                clearTempLibFiles();
                RocksDB.loadLibrary();
                FileUtils.forceMkdir(new File(basePath));
                FileUtils.cleanDirectory(new File(basePath));

                for (int i = 0; i < repoUnitCount; i++) {
                    RepoUnit unit = new RepoUnit(basePath + "/" + UUID.randomUUID().toString(), true, true, true);
                    repoUnits.add(unit);
                    unit.open();
                }
            } catch (Throwable e) {
                releaseResource();
                throw new PolardbxException("Open Repository failed.", e);
            }

            log.info("Repository is opened with path :" + basePath);
        }
    }

    public void close() {
        if (isStarted.compareAndSet(true, false)) {
            try {
                releaseResource();
            } catch (Exception e) {
                throw new PolardbxException("Close Repository failed.", e);
            }
            log.info("Repository is closed with path :" + basePath);
        }
    }

    public boolean isForcePersist() {
        if (persistMode == PersistMode.AUTO) {
            return false;
        } else if (persistMode == PersistMode.FORCE) {
            return true;
        } else if (persistMode == PersistMode.RANDOM) {
            Random random = new Random();
            return random.nextBoolean();
        } else {
            throw new PolardbxException("unsupported persist mode " + persistMode);
        }
    }

    public boolean isPersistOn() {
        return isPersistOn;
    }

    public DeleteMode getDeleteMode() {
        return deleteMode;
    }

    public int getTxnPersistThreshold() {
        return txnPersistThreshold;
    }

    public int getTxnItemPersistThreshold() {
        return txnItemPersistThreshold;
    }

    public boolean isReachPersistThreshold(boolean instantCheck) {
        int checkInterval = DynamicApplicationConfig.getInt(STORAGE_PERSIST_CHECK_INTERVAL_MILLS);
        if (instantCheck || System.currentTimeMillis() - persistCheckResult.checkTime >= checkInterval) {
            double totalRatio = JvmUtils.getTotalUsedRatio();
            double oldRatio = JvmUtils.getOldUsedRatio();
            boolean result = (totalRatio >= persistNewThreshold) || (oldRatio >= persistNewThreshold);

            if (result && exceedPersistThreshold.compareAndSet(false, true)) {
                log.info(
                    "Memory usage ratio changed greater than the persist threshold {}, totalRatio is {}, oldRatio is {}.",
                    persistNewThreshold, totalRatio, oldRatio);
            }
            if (!result && exceedPersistThreshold.compareAndSet(true, false)) {
                log.info(
                    "Memory usage ratio changed lower than the persist threshold {}, totalRatio is {}, oldRatio is {}.",
                    persistNewThreshold, totalRatio, oldRatio);
            }

            this.persistCheckResult = new PersistCheckResult(result, System.currentTimeMillis());
        }

        return persistCheckResult.shouldPersist;
    }

    public RepoUnit selectUnit(byte[] key) {
        if (repoUnits.size() == 1) {
            return repoUnits.get(0);
        } else {
            int index = Math.abs(Arrays.hashCode(key) % repoUnits.size());
            return repoUnits.get(index);
        }
    }

    public RepoUnit selectUnit(Long seed) {
        if (repoUnits.size() == 1) {
            return repoUnits.get(0);
        } else {
            int index = Math.abs(seed.hashCode() % repoUnits.size());
            return repoUnits.get(index);
        }
    }

    private void releaseResource() {
        repoUnits.forEach(u -> {
            try {
                u.close();
            } catch (Throwable throwable) {
                //do nothing
            }
        });
    }

    // RocksDB会在临时目录生成临时的lib文件，当通过kill命令的方式终止进程时，临时文件可以被释放掉
    // 但通过kill -9命令的方式终止进程时，临时文件不会被释放掉，此处做一下手动清理
    private void clearTempLibFiles() {
        File directory = new File("/tmp");
        if (directory.exists()) {
            File[] files = directory.listFiles((dir, name) ->
                name.startsWith("librocksdbjni") && name.endsWith(".so")
            );

            if (files != null && files.length > 0) {
                for (File file : files) {
                    FileUtils.deleteQuietly(file);
                }
            }
        }
    }

    @Data
    @AllArgsConstructor
    static class PersistCheckResult {
        boolean shouldPersist;
        long checkTime;
    }
}
