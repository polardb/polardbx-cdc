/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PartStreamMetricsTest {
    private PartStreamMetrics partStreamMetrics;

    public void setUp() {
        // Create an instance of PartStreamMetrics
        partStreamMetrics = new PartStreamMetrics(1, 1024L);
    }

    @Test
    public void testConstructor() {
        setUp();
        assertEquals(1, partStreamMetrics.getSeq());
        assertEquals(1024L, partStreamMetrics.getTotalBytes());
        assertEquals(0L, partStreamMetrics.getBytesRead());
        assertEquals(0L, partStreamMetrics.getStartTimestamp());
        assertEquals(0L, partStreamMetrics.getFinishTimestamp());
        assertNull(partStreamMetrics.getFinishCounter());
        assertEquals(0L, partStreamMetrics.getBps());
    }

    @Test
    public void testSetBytesRead() {
        setUp();
        partStreamMetrics.setBytesRead(512L);
        assertEquals(512L, partStreamMetrics.getBytesRead());

        partStreamMetrics.setBytesRead(1024L);
        assertEquals(1024L, partStreamMetrics.getBytesRead());
    }

    @Test
    public void testGetBytesRead() {
        setUp();
        assertEquals(0L, partStreamMetrics.getBytesRead());

        partStreamMetrics.setBytesRead(256L);
        assertEquals(256L, partStreamMetrics.getBytesRead());
    }

    @Test
    public void testSetStartTimestamp() {
        setUp();
        long startTime = System.currentTimeMillis();
        partStreamMetrics.setStartTimestamp(startTime);
        assertEquals(startTime, partStreamMetrics.getStartTimestamp());
    }

    @Test
    public void testSetFinishTimestamp() {
        setUp();
        long finishTime = System.currentTimeMillis();
        partStreamMetrics.setFinishTimestamp(finishTime);
        assertEquals(finishTime, partStreamMetrics.getFinishTimestamp());
    }

    @Test
    public void testSetFinishCounter() {
        setUp();
        AtomicInteger finishCounter = new AtomicInteger(1);
        partStreamMetrics.setFinishCounter(finishCounter);
        assertEquals(finishCounter, partStreamMetrics.getFinishCounter());
    }

    @Test
    public void testSetBps() {
        setUp();
        partStreamMetrics.setBps(100L);
        assertEquals(100L, partStreamMetrics.getBps());
    }
}
