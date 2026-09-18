/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.columnar.metrics;

import org.junit.Assert;
import org.junit.Test;

public class ColumnarMetricsTest {
    @Test
    public void test() {
        ColumnarMetrics.get().put("binlogEventSize", 100);
        ColumnarMetrics.get().put("allBinlogEventThroughput", 200);
        ColumnarMetrics.get().put("allBinlogEventSize", 300);
        ColumnarMetrics.get().buildMetricValue();

        Assert.assertEquals(100, ColumnarMetrics.get().getBinlogEventSize(), 0.001);
        Assert.assertEquals(200, ColumnarMetrics.get().getAllBinlogEventThroughput(), 0.001);
        Assert.assertEquals(300, ColumnarMetrics.get().getAllBinlogEventSize(), 0.001);

        MetricsManager.MetricsSnapshot snapshot = new MetricsManager.MetricsSnapshot(1);
        snapshot.columnarMetrics = ColumnarMetrics.get();
        MetricsManager manager = new MetricsManager();
        StringBuilder sb = new StringBuilder();
        manager.contactColumnarMetrics(snapshot, sb);
        System.out.println(sb);

        Assert.assertTrue(sb.toString().contains("getBinlogEventSize"));
        Assert.assertTrue(sb.toString().contains("getAllBinlogEventThroughput"));
        Assert.assertTrue(sb.toString().contains("getAllBinlogEventSize"));
        Assert.assertTrue(sb.toString().contains("100"));
        Assert.assertTrue(sb.toString().contains("200"));
        Assert.assertTrue(sb.toString().contains("300"));
    }
}
