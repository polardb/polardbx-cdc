/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import org.junit.Test;
import org.mockito.Mock;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MultiPartStreamMetricsTest {
    @Mock
    private SearchMetricsManager searchMetricsManager;

    private MultiPartStreamMetrics multiPartStreamMetrics;
    private AtomicInteger finishCounter;

    public void setUp() {
        // Mock the singleton instance of SearchMetricsManager
//        when(SearchMetricsManager.getInstance()).thenReturn(searchMetricsManager);
        finishCounter = new AtomicInteger(0);
        // Create an instance of MultiPartStreamMetrics
        multiPartStreamMetrics =
            new MultiPartStreamMetrics("testFileName", "testUrl", "testStorageInstanceId", finishCounter);
    }

    @Test
    public void testAddPartMetrics() {
        setUp();
        PartStreamMetrics partStreamMetrics = new PartStreamMetrics(0, 100L);
        multiPartStreamMetrics.addPartMetrics(partStreamMetrics);

        assertEquals(1, multiPartStreamMetrics.getPartMetrics().size());
        assertEquals(partStreamMetrics, multiPartStreamMetrics.getPartMetrics().get(0));
    }

    @Test
    public void testStartMetrics() {
        setUp();
        long startTime = System.currentTimeMillis();
        multiPartStreamMetrics.startMetrics();
        assertTrue(multiPartStreamMetrics.getStartTime() >= startTime);
        assertTrue(SearchMetricsManager.getInstance().metrics("testStorageInstanceId"));
        multiPartStreamMetrics.finishMetrics();
        assertFalse(SearchMetricsManager.getInstance().metrics("testStorageInstanceId"));
    }

    @Test
    public void testIsFinish() {
        setUp();
        assertTrue(multiPartStreamMetrics.isFinish());

        PartStreamMetrics partStreamMetrics = new PartStreamMetrics(1, 100L);
        multiPartStreamMetrics.addPartMetrics(partStreamMetrics);
        assertFalse(multiPartStreamMetrics.isFinish());
        finishCounter.incrementAndGet();

        assertTrue(multiPartStreamMetrics.isFinish());
    }

    @Test
    public void testGetFinishedTime() {
        setUp();
        PartStreamMetrics partStreamMetrics1 = new PartStreamMetrics(1, 100L);
        PartStreamMetrics partStreamMetrics2 = new PartStreamMetrics(2, 200L);
        partStreamMetrics1.setFinishTimestamp(100L);
        partStreamMetrics2.setFinishTimestamp(200L);
        multiPartStreamMetrics.addPartMetrics(partStreamMetrics1);
        multiPartStreamMetrics.addPartMetrics(partStreamMetrics2);

        assertEquals(200L, multiPartStreamMetrics.getFinishedTime());
    }
}
