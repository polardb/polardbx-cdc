/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.rocksdb.Options;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksDB;
import org.rocksdb.RocksDBException;
import org.rocksdb.WriteOptions;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 磁盘存储
 **/
@Slf4j
public class Repository {
    private final String basePath;
    private final Boolean forcePersist;
    private final Double persistMemoryThreshold;
    private final int txnPersistThreshold;
    private final int txnItemPersistThreshold;
    private final boolean isPersistOn;
    private final DeleteMode deleteMode;
    private final int repoUnitCount;
    private final List<RepoUnit> repoUnits;
    private final AtomicBoolean exceedPersistThreshold;
    private final AtomicBoolean isStarted;

    public Repository(boolean isPersistOn, String basePath, boolean forcePersist, double persistMemoryThreshold,
                      int txnPersistThreshold, int txnItemPersistThreshold,
                      DeleteMode deleteMode, int repoUnitCount) {
        this.isPersistOn = isPersistOn;
        this.basePath = basePath;
        this.forcePersist = forcePersist;
        this.persistMemoryThreshold = persistMemoryThreshold;
        this.txnPersistThreshold = txnPersistThreshold;
        this.txnItemPersistThreshold = txnItemPersistThreshold;
        this.deleteMode = deleteMode;
        this.repoUnitCount = repoUnitCount;
        this.repoUnits = new ArrayList<>(repoUnitCount);
        this.exceedPersistThreshold = new AtomicBoolean(false);
        this.isStarted = new AtomicBoolean(false);
    }

    public void open() {
        if (isStarted.compareAndSet(false, true)) {
            try {
                clearTempLibFiles();
                RocksDB.loadLibrary();
                FileUtils.forceMkdir(new File(basePath));
                FileUtils.cleanDirectory(new File(basePath));

                for (int i = 0; i < repoUnitCount; i++) {
                    RepoUnit unit = new RepoUnit(basePath + "/" + UUID.randomUUID().toString());
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

    public void put(byte[] key, byte[] value) throws RocksDBException {
        checkKeyNotNull(key);
        checkValueNotNull(value);
        selectUnit(key).put(key, value);
    }

    public void del(byte[] key) throws RocksDBException {
        checkKeyNotNull(key);
        selectUnit(key).delete(key);
    }

    public void delRange(byte[] beginKey, byte[] endKey) throws RocksDBException {
        checkKeyNotNull(beginKey);
        checkKeyNotNull(endKey);
        for (RepoUnit repoUnit : repoUnits) {
            repoUnit.deleteRange(beginKey, endKey);
        }
    }

    public byte[] get(byte[] key) throws RocksDBException {
        byte[] value = selectUnit(key).get(key);
        checkValueNotNull(value);
        return value;
    }

    public boolean isForcePersist() {
        return forcePersist;
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

    public boolean isReachPersistThreshold() {
        MemoryMXBean totalMemoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage totalMemoryUsage = totalMemoryMXBean.getHeapMemoryUsage();
        long totalMaxMemorySize = totalMemoryUsage.getMax(); //最大可用内存
        long totalUsedMemorySize = totalMemoryUsage.getUsed(); //已使用的内存
        double totalRatio = (double) totalUsedMemorySize / (double) totalMaxMemorySize;

        MemoryUsage oldMemoryUsage = null;
        List<MemoryPoolMXBean> mps = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean mp : mps) {
            MemoryType type = mp.getType();
            String name = mp.getName();
            if (type == MemoryType.HEAP) {
                switch (name) {
                case "CMS Old Gen":
                case "PS Old Gen": {
                    oldMemoryUsage = mp.getUsage();
                    break;
                }
                }
            }
        }
        long oldMaxMemorySize = Objects.requireNonNull(oldMemoryUsage).getMax();
        long oldUsedMemorySize = oldMemoryUsage.getUsed();
        double oldRatio = (double) oldUsedMemorySize / (double) oldMaxMemorySize;

        boolean result = (totalRatio >= persistMemoryThreshold) || (oldRatio >= persistMemoryThreshold);

        if (result && exceedPersistThreshold.compareAndSet(false, true)) {
            log.info(
                "Memory usage ratio changed greater than the persist threshold {}, totalRatio is {}, oldRatio is {}.",
                persistMemoryThreshold, totalRatio, oldRatio);
        }
        if (!result && exceedPersistThreshold.compareAndSet(true, false)) {
            log.info(
                "Memory usage ratio changed lower than the persist threshold {}, totalRatio is {}, oldRatio is {}.",
                persistMemoryThreshold, totalRatio, oldRatio);
        }

        return result;
    }

    private RepoUnit selectUnit(byte[] key) {
        if (repoUnits.size() == 1) {
            return repoUnits.get(0);
        } else {
            int index = Math.abs(Arrays.hashCode(key)) % repoUnits.size();
            return repoUnits.get(index);
        }
    }

    private void checkKeyNotNull(byte[] key) {
        if (key == null || key.length == 0) {
            throw new PolardbxException("key is null or empty.");
        }
    }

    private void checkValueNotNull(byte[] value) {
        if (value == null || value.length == 0) {
            throw new PolardbxException("value is null or empty.");
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

    private static class RepoUnit {
        RocksDB rocksDB;
        String persistPath;
        Options options;
        WriteOptions writeOptions;
        ReadOptions readOptions;

        RepoUnit(String persistPath) {
            this.persistPath = persistPath;
        }

        void put(byte[] key, byte[] value) throws RocksDBException {
            rocksDB.put(writeOptions, key, value);
        }

        byte[] get(byte[] key) throws RocksDBException {
            return rocksDB.get(readOptions, key);
        }

        void delete(byte[] key) throws RocksDBException {
            rocksDB.delete(writeOptions, key);
        }

        void deleteRange(byte[] beginKey, byte[] endKey) throws RocksDBException {
            rocksDB.deleteRange(writeOptions, beginKey, endKey);
        }

        void open() throws Throwable {
            try {
                options = new Options().setCreateIfMissing(true);
                writeOptions = new WriteOptions();
                writeOptions.disableWAL();
                readOptions = new ReadOptions();
                readOptions.setIgnoreRangeDeletions(true);

                FileUtils.forceMkdir(new File(persistPath));
                rocksDB = RocksDB.open(options, persistPath);

                log.info("Repo Unit is opened with path : " + persistPath);
            } catch (Throwable t) {
                log.error("Repo Unit is open failed with path : " + persistPath, t);
                throw t;
            }
        }

        void close() throws Throwable {
            try {
                if (options != null) {
                    options.close();
                }
                if (writeOptions != null) {
                    writeOptions.close();
                }
                if (readOptions != null) {
                    readOptions.close();
                }
                if (rocksDB != null) {
                    rocksDB.close();
                }

                File path = new File(persistPath);
                if (path.exists()) {
                    FileUtils.deleteDirectory(path);
                }

                log.info("Repo Unit is closed with path : " + persistPath);
            } catch (Throwable t) {
                log.error("Repo Unit is closing failed with path : " + persistPath, t);
                throw t;
            }
        }
    }
}
