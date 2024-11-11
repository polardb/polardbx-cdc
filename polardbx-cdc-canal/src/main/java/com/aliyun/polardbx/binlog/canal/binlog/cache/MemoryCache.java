/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

import java.io.IOException;
import java.io.InputStream;

public class MemoryCache implements Cache {
    private int SOCKET_BUFFER_SIZE = 8192;
    private int readOffset;
    private volatile int limit;
    private byte[] buff;
    private CacheManager cacheManager;
    private InputStream in;
    private volatile boolean open = true;
    private volatile boolean finish = false;

    public MemoryCache(CacheManager cacheManager, InputStream in) throws IOException {
        this.cacheManager = cacheManager;
        this.in = in;
    }

    @Override
    public void fetchData() throws IOException {
        int len = -1;
        this.buff = cacheManager.allocateBuffer();
        int writeOffset = 0;
        while (open
            && (len = in.read(buff, writeOffset, Math.min(buff.length - writeOffset, SOCKET_BUFFER_SIZE))) > -1) {
            limit += len;
            writeOffset += len;
        }
        this.finish = true;
    }

    @Override
    public void resetStream(InputStream in) {
        this.in = in;
    }

    @Override
    public int skip(int n) {
        return this.readOffset += n;
    }

    @Override
    public int read(byte[] data, int offset, int size) throws IOException {
        int remainSize = Math.min(limit - this.readOffset, size);

        while (remainSize <= 0) {
            if (finish) {
                remainSize = Math.min(limit - this.readOffset, size);
                if (remainSize > 0) {
                    break;
                }
                return -1;
            }
        }
        System.arraycopy(buff, this.readOffset, data, offset, remainSize);
        this.readOffset += remainSize;
        return remainSize;

    }

    @Override
    public void close() throws IOException {
        if (in != null) {
            in.close();
            in = null;
        }
        cacheManager.releaseBuffer(this.buff);
        this.buff = null;
    }

    @Override
    public String toString() {
        return "MemoryCache{" +
            "readOffset=" + readOffset +
            ", limit=" + limit +
            ", open=" + open +
            ", finish=" + finish +
            '}';
    }
}
