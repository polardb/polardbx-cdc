/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.error.PolardbxException;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class StreamPipe {

    private final int BUFFER_SIZE;
    private AtomicLong size = new AtomicLong(0);
    private LinkedBlockingQueue<byte[]> bufferList = new LinkedBlockingQueue<>();
    private byte[] first;
    private int innerOffset = 0;

    private static final long TIME_OUT = TimeUnit.MINUTES.toMillis(1);

    private long lastReceiveTime = System.currentTimeMillis();

    public StreamPipe() throws IOException {
        BUFFER_SIZE = 1024 * 1024 * 16;
    }

    public int read(byte[] dst, int offset, int limit) throws IOException {
        long now = System.currentTimeMillis();
        if (first == null) {
            try {
                first = bufferList.poll(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new IOException(e);
            }
            if (first == null) {
                if (now - lastReceiveTime > TIME_OUT) {
                    throw new PolardbxException(
                        "wait for dumper data timeout for : " + (now - lastReceiveTime) + " ms");
                }
                return 0;
            }
            innerOffset = 0;
        }
        lastReceiveTime = now;
        int remainDataSize = first.length - innerOffset;
        int readSize = Math.min(remainDataSize, limit);
        System.arraycopy(first, innerOffset, dst, offset, readSize);
        innerOffset += readSize;
        if (innerOffset >= first.length) {
            first = null;
            innerOffset = 0;
        }
        size.addAndGet(-readSize);
        return readSize;
    }

    public void write(byte[] data) throws IOException {
        bufferList.add(data);
        size.addAndGet(data.length);
        while (size.get() >= BUFFER_SIZE) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }
        }
    }

    public void close() throws IOException {
        first = null;
        bufferList = null;
    }

}
