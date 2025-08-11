/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import lombok.Data;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Data
public class PartStreamMetrics {
    private final int seq;
    private final AtomicLong bytesRead = new AtomicLong(0);
    private final long totalBytes;
    private long startTimestamp;
    private long finishTimestamp;
    private boolean allocateBuffer;
    private AtomicInteger finishCounter;
    private long bps;

    public PartStreamMetrics(int seq, long totalBytes) {
        this.seq = seq;
        this.totalBytes = totalBytes;
    }

    public void setBytesRead(long bytesRead) {
        this.bytesRead.set(bytesRead);
    }

    public long getBytesRead() {
        return this.bytesRead.get();
    }
}
