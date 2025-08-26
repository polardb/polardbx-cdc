/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

@Slf4j
public class MemoryCache implements Cache {
    private volatile int readOffset;
    private volatile int limit;
    private volatile byte[] buff;
    private InputStream in;
    private volatile boolean finish = false;
    private final String url;
    private final int seq;
    @Setter
    private CacheProgressListener progressListener;
    private final long expectedSize;
    private final AtomicInteger sequencer;
    private volatile boolean interrupted = false;
    private final String storageInstanceId;
    private String uuid;
    private long lastPrintWaitInfo = System.currentTimeMillis();

    public MemoryCache(String storageInstanceId, String url, int seq, long expectedSize, AtomicInteger sequencer) {
        this.storageInstanceId = storageInstanceId;
        this.url = url;
        this.seq = seq;
        this.expectedSize = expectedSize;
        this.sequencer = sequencer;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public boolean interrupted() {
        return Thread.currentThread().isInterrupted() || interrupted;
    }

    @Override
    public void fetchData(InputStream is) throws Exception {
        int len = -1;
        int writeOffset = 0;
        int counter = 1;
        this.in = is;
        try {
            long timestamp = System.currentTimeMillis();
            final long warningInterval = TimeUnit.SECONDS.toMillis(30);
            if (log.isDebugEnabled()) {
                log.debug("{} {} acquire for sequence base {}!", getFileName(url), seq, sequencer.get());
            }
            while (sequencer.get() < seq) {
                LockSupport.parkNanos(100000);
                if (interrupted()) {
                    throw new PolardbxException("thread interrupted when wait for allocate cache buffer!");
                }
                final long now = System.currentTimeMillis();
                if (now - timestamp > warningInterval) {
                    log.warn("{} {} {} wait for sequence base {} failed!, wait time {} s! id : {}", storageInstanceId,
                        getFileName(url), seq, sequencer.get(), counter++ * 5, uuid);
                    timestamp = now;
                }
            }
            CacheLoggerContext.setFileName(getFileName(url));
            CacheLoggerContext.setSeq(seq);
            CacheLoggerContext.setStorageInstanceId(storageInstanceId);
            CacheLoggerContext.setUuid(uuid);
            CacheManager cacheManager = SpringContextHolder.getObject(CacheManager.class);
            byte[] allocateBuffer = null;
            while ((allocateBuffer = cacheManager.allocateBuffer(storageInstanceId, url, seq, expectedSize)) == null) {
                LockSupport.parkNanos(100000);
                if (interrupted()) {
                    throw new PolardbxException("thread interrupted when allocate cache buffer!");
                }
            }
            this.buff = allocateBuffer;
            if (progressListener != null) {
                progressListener.onAllocateBuffer();
            }
            int tempSeq = sequencer.get();
            while (tempSeq < seq + 1 && !sequencer.compareAndSet(tempSeq, seq + 1)) {
                if (interrupted()) {
                    throw new PolardbxException("thread interrupted when increment sequencer !");
                }
            }
            progressListener.onStart();
            int socketBufferSize =
                DynamicApplicationConfig.getInt(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SOCKET_BUFFER);
            while ((len = in.read(buff, writeOffset, Math.min(buff.length - writeOffset, socketBufferSize))) > -1) {
                limit += len;
                writeOffset += len;
                if (progressListener != null) {
                    progressListener.onProgress(limit);
                }
            }
        } finally {
            this.finish = true;
            if (progressListener != null) {
                progressListener.onFinish();
            }
        }

    }

    @Override
    public void interrupt() {
        this.interrupted = true;
    }

    public String getFileName(String url) {
        int b = url.lastIndexOf("/");
        int e = url.indexOf("?");
        if (e > b) {
            return url.substring(b + 1, e);
        }
        return url;
    }

    @Override
    public int skip(int n) {
        return this.readOffset += n;
    }

    private void waitForFinish() {
        while (!finish) {
            LockSupport.parkNanos(100000);
            if (Thread.currentThread().isInterrupted()) {
                throw new PolardbxException(
                    "wait for read cache finish occur interrupted exception! dn : " + storageInstanceId + ", file: "
                        + getFileName(url) + ", seq : " + seq + " id : " + uuid);
            }
            if (interrupted) {
                throw new PolardbxException(
                    "wait for read cache finish occur interrupted exception! dn : " + storageInstanceId + ", file: "
                        + getFileName(url) + ", seq : " + seq + " id : " + uuid);
            }
            if (System.currentTimeMillis() - lastPrintWaitInfo > TimeUnit.SECONDS.toMillis(5)) {
                log.info("{} {} {} wait for finish! id : {}", storageInstanceId, getFileName(url), seq, uuid);
                lastPrintWaitInfo = System.currentTimeMillis();
            }
        }
    }

    @Override
    public int read(byte[] data, int offset, int size) throws IOException {
        waitForFinish();
        int remainSize = Math.min(limit - this.readOffset, size);

        while (remainSize <= 0) {
            remainSize = Math.min(limit - this.readOffset, size);
            if (remainSize > 0) {
                break;
            }
            return -1;
        }
        System.arraycopy(buff, this.readOffset, data, offset, remainSize);
        this.readOffset += remainSize;
        return remainSize;
    }

    @Override
    public void close() throws IOException {
        try {
            if (in != null) {
                in.close();
                in = null;
            }
        } catch (Exception ignore) {
        }
        CacheLoggerContext.setStorageInstanceId(storageInstanceId);
        CacheLoggerContext.setFileName(getFileName(url));
        CacheLoggerContext.setSeq(seq);
        CacheLoggerContext.setUuid(uuid);
        synchronized (this) {
            CacheManager cacheManager = SpringContextHolder.getObject(CacheManager.class);
            cacheManager.releaseBuffer(storageInstanceId, this.buff);
            this.buff = null;
        }
    }

    @Override
    public String toString() {
        return "MemoryCache{" + "readOffset=" + readOffset + ", limit=" + limit + ", finish=" + finish + '}';
    }
}
