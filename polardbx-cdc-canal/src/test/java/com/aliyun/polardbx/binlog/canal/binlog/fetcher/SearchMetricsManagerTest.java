/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.canal.unit.SearchRecorder;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class SearchMetricsManagerTest extends BaseTest {
    @Test
    public void testGetSearchRecorder() {
        SearchRecorder recorder = SearchMetricsManager.getInstance().getSearchRecorder("test-dn");
        Assert.assertNotNull(recorder);
    }

    @Test
    public void testMetrics() {
        SearchMetricsManager metricsManager = SearchMetricsManager.getInstance();
        Assert.assertFalse(metricsManager.metrics("test-dn-1"));
        metricsManager.put("test-dn-1", new MultiPartStreamMetrics("test-file", "test-url", "test-dn-1", null));
        Assert.assertTrue(metricsManager.metrics("test-dn-1"));
    }

    @Test
    public void formatDate() {
        SearchMetricsManager metricsManager = SearchMetricsManager.getInstance();
        long time = 1736140869487L;
        String expected = "2025-01-06 13:21:09";
        Assert.assertEquals(expected, metricsManager.formatDate(time, null));
        Assert.assertEquals(expected, metricsManager.formatDate(-1, expected));
    }

    @Test
    public void testRemainQueueSize() {
        SearchRecorder recorder = new SearchRecorder("test-dn");
        List<String> queueList = new ArrayList<>();
        String f1 = "f1";
        String f2 = "f2";
        queueList.add(f1);
        queueList.add(f2);
        recorder.setQueueList(queueList);
        recorder.setFileName(f2);
        Assert.assertEquals(queueList.indexOf(f2) + 1, SearchMetricsManager.getInstance().remainQueueSize(recorder));
        recorder.setFileName(null);
        Assert.assertEquals(-1, SearchMetricsManager.getInstance().remainQueueSize(recorder));
    }

    @Test
    public void testProgress() {
        SearchRecorder recorder = new SearchRecorder("test-dn");
        recorder.setSize(100);
        recorder.setPosition(50);
        Assert.assertEquals("50%", SearchMetricsManager.getInstance().progress(recorder));
        recorder.setSize(0);
        Assert.assertEquals("0%", SearchMetricsManager.getInstance().progress(recorder));
    }

    @Test
    public void testHumanReadableSpeed() {
        Assert.assertEquals("1 B", SearchMetricsManager.getInstance().getHumanReadableSpeed(1));
        Assert.assertEquals("1.00 KB", SearchMetricsManager.getInstance().getHumanReadableSpeed(1024));
        Assert.assertEquals("1.00 MB", SearchMetricsManager.getInstance().getHumanReadableSpeed(1024 * 1024));
        Assert.assertEquals("1.00 GB", SearchMetricsManager.getInstance().getHumanReadableSpeed(1024 * 1024 * 1024));
    }

}
