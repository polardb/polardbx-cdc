/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class CacheManager {

    private final LinkedBlockingQueue<AutoTimeoutBuffer> bufferPools = new LinkedBlockingQueue<>();
    private final Set<String> downloadStorageMap = new ConcurrentSkipListSet<>();
    private long lastMaxCacheSize = -1;
    private ScheduledExecutorService scheduledExecutorService;
    private final AtomicBoolean cleanerStarted = new AtomicBoolean(false);
    private CacheDistribution distribution;

    public void registerStorage(String storageInstanceId) {
        downloadStorageMap.add(storageInstanceId);
    }

    public void unregisterStorage(String storageInstanceId) {
        downloadStorageMap.remove(storageInstanceId);
    }

    private void startSchedule() {
        if (cleanerStarted.compareAndSet(false, true)) {
            int interval =
                DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_CLEAN_INTERVAL_SECOND);
            log.info("start cache cleaner {} sec", interval);
            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdownNow();
            }
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cache-cleaner");
                t.setDaemon(true);
                return t;
            });
            scheduledExecutorService.scheduleAtFixedRate(() -> {
                // no downloading file
                try {
                    getBuffer(false);
                } catch (Exception e) {
                    log.error("clean buffer pool occur interrupted exception ", e);
                }
            }, interval, interval, TimeUnit.SECONDS);
        }
    }

    public long maxCacheSize() {
        final long oldMaxCacheSize =
            DynamicApplicationConfig.getLong(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_LIMIT);
        long maxCacheSize = oldMaxCacheSize;
        final int size = downloadStorageMap.size();
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_AUTO_ADJUST)
            && size > 0) {
            long maxMemory = Runtime.getRuntime().maxMemory();

            long expectedMem =
                size * DynamicApplicationConfig.getLong(ConfigKeys.TASK_DUMP_DN_DEFAULT_BINLOG_FILE_SIZE);
            double useRatio = DynamicApplicationConfig.getDouble(
                ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_AUTO_ADJUST_USE_RATIO);
            long realMem = (long) (maxMemory * useRatio);
            maxCacheSize = Math.min(realMem, expectedMem);
            if (maxCacheSize != lastMaxCacheSize) {
                log.info("adjust max cache from {} to {}", oldMaxCacheSize, maxCacheSize);
            }
            lastMaxCacheSize = maxCacheSize;
        }
        return maxCacheSize;
    }

    public String getFileName(String url) {
        int b = url.lastIndexOf("/");
        int e = url.indexOf("?");
        if (e > b) {
            return url.substring(b + 1, e);
        }
        return url;
    }

    public synchronized void checkDistribution() {
        long cacheSize = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE);
        long maxSize = maxCacheSize();
        String slotDistStrategy =
            DynamicApplicationConfig.getString(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_DISTRIBUTION_SLOT_STRATEGY);
        CacheDistributionType type = CacheDistributionType.valueOf(slotDistStrategy);
        if (distribution != null) {
            distribution.checkBufferLimitAndStrategy(cacheSize, maxSize, type, downloadStorageMap.size());
        }
    }

    public synchronized CacheDistribution getDistribution() {
        if (distribution == null) {
            long cacheSize = DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE);
            long maxCacheSize = maxCacheSize();
            String slotDistStrategy = DynamicApplicationConfig.getString(
                ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_DISTRIBUTION_SLOT_STRATEGY);
            CacheDistributionType type = CacheDistributionType.valueOf(slotDistStrategy);
            CacheDistributionStrategy strategy = CacheDistributionStrategyFactory.create(type);
            distribution = new CacheDistribution(downloadStorageMap.size(), cacheSize, maxCacheSize, strategy);
        }
        return distribution;
    }

    /**
     * 避免cache slot 不够用出现死锁， 两种策略：
     * 1、为每个dn保留至少一个slot，已解除死锁状态， 剩余情况采用完全竞争机制
     * 2、提供参数配置，为每个dn 提供固定上限的slot 数量，均匀分配资源
     */
    public byte[] allocateBuffer(String storageInstanceId, String url, int seq, long expectedSize)
        throws InterruptedException {
        startSchedule();

        checkDistribution();
        CacheDistribution cacheDistribution = getDistribution();
        if (!cacheDistribution.tryAcquireBuffer(storageInstanceId)) {
            return null;
        }
        if (log.isDebugEnabled()) {
            log.debug("create buffer for {}  file {} seq {} uuid {}", storageInstanceId, getFileName(url), seq,
                CacheLoggerContext.getUuid());
        }
        return createOrGetBuffer(expectedSize);
    }

    /**
     * 先进先出队列，大多数情况下，队列按照时间先后顺序排列 队列首部的时间超过配置的时长，则释放该buffer
     */
    public byte[] getBuffer(boolean acquireBuffer) {
        synchronized (this) {
            final long timeoutSec = DynamicApplicationConfig.getInt(
                ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_KEEP_ALIVE_TIMEOUT_SECOND);
            long endTime = System.currentTimeMillis() - timeoutSec;
            AutoTimeoutBuffer buffer = null;
            int counter = 0;
            do {
                if (acquireBuffer) {
                    buffer = bufferPools.poll();
                } else {
                    buffer = bufferPools.peek();
                }
                if (buffer == null) {
                    return null;
                }
                if (buffer.getTimeout() < endTime) {
                    if (!acquireBuffer) {
                        bufferPools.poll();
                        counter++;
                    }
                    buffer = null;
                }
            } while (buffer == null);
            if (log.isDebugEnabled()) {
                log.debug("release buffer count : {}, pool size is {}", counter, bufferPools.size());
            }
            if (acquireBuffer) {
                return buffer.getBuff();
            }
            return null;
        }
    }

    public byte[] createOrGetBuffer(long expectedSize) throws InterruptedException {
        byte[] returnBuffer = getBuffer(true);
        final long bufferSize = getDistribution().getUnitBuffer();
        long maxBufferSize = Math.max(bufferSize, expectedSize);
        if (returnBuffer != null && returnBuffer.length != maxBufferSize) {
            // 可能是mem_unit 调整了
            log.warn("expected size {} is not equal buffer in pool {}, will renew with new size {}", expectedSize,
                returnBuffer.length, maxBufferSize);
            returnBuffer = new byte[(int) maxBufferSize];
        }
        if (returnBuffer == null) {
            returnBuffer = new byte[(int) maxBufferSize];
        }
        return returnBuffer;
    }

    public void releaseBuffer(String storageInstanceId, byte[] buff) {
        long nowCacheUnitSize = DynamicApplicationConfig.getLong(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE);
        if (buff == null) {
            return;
        }
        if (buff.length == nowCacheUnitSize) {
            bufferPools.add(new AutoTimeoutBuffer(System.currentTimeMillis(), buff));
        } else {
            log.warn("release buffer size not match, old:{}, new:{}", buff.length, nowCacheUnitSize);
        }
        getDistribution().releaseBuffer(storageInstanceId);

    }

    public String buildCacheDetail() {
        return "buffer pool size is " + bufferPools.size() + "\n" + getDistribution().buildDetail();
    }

    public class AutoTimeoutBuffer {
        @Getter
        private final long timeout;
        @Getter
        private final byte[] buff;

        public AutoTimeoutBuffer(long timeout, byte[] buff) {
            this.timeout = timeout;
            this.buff = buff;
        }
    }
}
