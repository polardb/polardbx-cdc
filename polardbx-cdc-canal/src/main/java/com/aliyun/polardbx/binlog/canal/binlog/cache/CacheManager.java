/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;

public class CacheManager {

    private static CacheManager instance = new CacheManager();
    private final int cacheSize;
    private final int maxCacheSize;
    private final AtomicInteger bufferLimitCounter = new AtomicInteger(0);
    private CacheMode mode;
    private LinkedList<byte[]> bufferPools = new LinkedList<>();
    private String path;
    private AtomicInteger seq = new AtomicInteger(0);

    private CacheManager() {
        cacheSize = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE);
        maxCacheSize = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_LIMIT);
        bufferLimitCounter.set(maxCacheSize / cacheSize);
        path = DynamicApplicationConfig.getString(TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR) + File.separator +
            DynamicApplicationConfig.getString(TASK_NAME) + File.separator + "cache";
        mode = CacheMode.valueOf(DynamicApplicationConfig.getString(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_MODE));

        FileUtils.deleteQuietly(new File(path));
    }

    public static CacheManager getInstance() {
        return instance;
    }

    public CacheMode getMode() {
        return mode;
    }

    public void setMode(CacheMode mode) {
        this.mode = mode;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Cache allocate(InputStream in, long size) throws IOException {
        if (mode == CacheMode.MEMORY) {
            return new MemoryCache(this, in);
        } else if (mode == CacheMode.DISK) {
            return new DiskCache(this, in,
                path + File.separator + "cache-" + seq.incrementAndGet() + "-" + RandomUtils.nextInt() +
                    ".tmp", size);
        } else {
            int counter = bufferLimitCounter.get();
            while (counter > 0 && !bufferLimitCounter.compareAndSet(counter, counter - 1)) {
                counter = bufferLimitCounter.get();
            }
            if (counter > 0) {
                return new MemoryCache(this, in);
            }
            return new DiskCache(this, in,
                path + File.separator + "cache-" + seq.incrementAndGet() + "-" + RandomUtils.nextInt() + ".tmp", size);
        }
    }

    public synchronized byte[] allocateBuffer() {
        if (!bufferPools.isEmpty()) {
            return bufferPools.pollLast();
        }
        return new byte[cacheSize];
    }

    public synchronized void releaseBuffer(byte[] buff) {
        if (buff == null) {
            return;
        }
        bufferPools.add(buff);
        bufferLimitCounter.incrementAndGet();
    }
}
