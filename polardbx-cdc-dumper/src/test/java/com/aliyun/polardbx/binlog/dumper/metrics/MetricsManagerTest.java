/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.metrics;

import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.TABLE_MAP_EVENT;
import static com.aliyun.polardbx.binlog.canal.binlog.LogEvent.WRITE_ROWS_EVENT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MetricsManagerTest extends BaseTest {

    @Test
    public void testBuildPeriodAverageWithNullLastSnapshot() {
        // 准备测试数据
        MetricsManager metricsManager = new MetricsManager(1L, "testTask", TaskType.Dumper);

        // 创建当前快照
        MetricsManager.MetricsSnapshot currentSnapshot = new MetricsManager.MetricsSnapshot(1L);
        currentSnapshot.timestamp = System.currentTimeMillis();
        currentSnapshot.streamMetrics = new HashMap<>();

        // 创建流指标
        StreamMetrics streamMetrics = new StreamMetrics("test-stream");
        streamMetrics.incrementTotalRevBytes(1000L);
        streamMetrics.incrementTotalWriteDmlEventCount(TABLE_MAP_EVENT);
        streamMetrics.incrementTotalWriteDmlEventCount(WRITE_ROWS_EVENT);
        streamMetrics.incrementTotalWriteDmlEventCount(WRITE_ROWS_EVENT);
        streamMetrics.incrementTotalWriteEventCount();
        streamMetrics.incrementTotalWriteEventCount();
        streamMetrics.incrementTotalWriteEventCount();
        streamMetrics.incrementTotalWriteTxnCount();
        streamMetrics.incrementTotalWriteBytes(500L);
        streamMetrics.incrementTotalUploadBytes(200L);
        streamMetrics.incrementTotalDumpBytes(150L);
        streamMetrics.incrementTotalSyncBytes(100L);

        currentSnapshot.streamMetrics.put("test-stream", streamMetrics);

        // 调用测试方法
        Map<String, MetricsManager.StreamMetricsAverage> result = metricsManager.buildPeriodAverage(currentSnapshot);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());

        MetricsManager.StreamMetricsAverage average = result.get("test-stream");
        assertNotNull(average);
        assertEquals("test-stream", average.streamId);
    }

    @Test
    public void testBuildPeriodAverageWithValidLastSnapshot() throws Exception {
        // 准备测试数据
        MetricsManager metricsManager = new MetricsManager(1L, "testTask", TaskType.Dumper);

        // 创建初始快照（模拟上一个快照）
        MetricsManager.MetricsSnapshot lastSnapshot = new MetricsManager.MetricsSnapshot(1L);
        lastSnapshot.timestamp = System.currentTimeMillis();
        lastSnapshot.streamMetrics = new HashMap<>();

        StreamMetrics lastStreamMetrics = new StreamMetrics("test-stream");
        lastStreamMetrics.incrementTotalRevBytes(500L);
        lastStreamMetrics.incrementTotalWriteDmlEventCount(TABLE_MAP_EVENT);
        lastStreamMetrics.incrementTotalWriteDmlEventCount(WRITE_ROWS_EVENT);
        lastStreamMetrics.incrementTotalWriteEventCount();
        lastStreamMetrics.incrementTotalWriteEventCount();
        lastStreamMetrics.incrementTotalWriteTxnCount();
        lastStreamMetrics.incrementTotalWriteBytes(200L);
        lastStreamMetrics.incrementTotalUploadBytes(100L);
        lastStreamMetrics.incrementTotalDumpBytes(50L);
        lastStreamMetrics.incrementTotalSyncBytes(25L);

        lastSnapshot.streamMetrics.put("test-stream", lastStreamMetrics);

        // 设置lastSnapshot（使用反射，因为字段是私有的）
        Field lastSnapshotField = MetricsManager.class.getDeclaredField("lastSnapshot");
        lastSnapshotField.setAccessible(true);
        lastSnapshotField.set(metricsManager, lastSnapshot);

        Thread.sleep(1000);

        // 创建当前快照
        MetricsManager.MetricsSnapshot currentSnapshot = new MetricsManager.MetricsSnapshot(2L);
        currentSnapshot.timestamp = System.currentTimeMillis();
        currentSnapshot.streamMetrics = new HashMap<>();

        StreamMetrics currentStreamMetrics = new StreamMetrics("test-stream");
        currentStreamMetrics.incrementTotalRevBytes(1500L);
        currentStreamMetrics.incrementTotalWriteDmlEventCount(TABLE_MAP_EVENT);
        currentStreamMetrics.incrementTotalWriteDmlEventCount(TABLE_MAP_EVENT);
        currentStreamMetrics.incrementTotalWriteDmlEventCount(TABLE_MAP_EVENT);
        currentStreamMetrics.incrementTotalWriteDmlEventCount(WRITE_ROWS_EVENT);
        currentStreamMetrics.incrementTotalWriteDmlEventCount(WRITE_ROWS_EVENT);
        currentStreamMetrics.incrementTotalWriteDmlEventCount(WRITE_ROWS_EVENT);
        currentStreamMetrics.incrementTotalWriteEventCount();
        currentStreamMetrics.incrementTotalWriteEventCount();
        currentStreamMetrics.incrementTotalWriteEventCount();
        currentStreamMetrics.incrementTotalWriteEventCount();
        currentStreamMetrics.incrementTotalWriteEventCount();
        currentStreamMetrics.incrementTotalWriteEventCount();
        currentStreamMetrics.incrementTotalWriteEventCount();
        currentStreamMetrics.incrementTotalWriteTxnCount();
        currentStreamMetrics.incrementTotalWriteTxnCount();
        currentStreamMetrics.incrementTotalWriteTxnCount();
        currentStreamMetrics.incrementTotalWriteBytes(700L);
        currentStreamMetrics.incrementTotalUploadBytes(300L);
        currentStreamMetrics.incrementTotalDumpBytes(250L);
        currentStreamMetrics.incrementTotalSyncBytes(225L);

        currentSnapshot.streamMetrics.put("test-stream", currentStreamMetrics);

        // 调用测试方法
        Map<String, MetricsManager.StreamMetricsAverage> result = metricsManager.buildPeriodAverage(currentSnapshot);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());

        MetricsManager.StreamMetricsAverage average = result.get("test-stream");
        assertNotNull(average);
        assertEquals("test-stream", average.streamId);

        // 验证BigDecimal类型的计算
        assertNotNull(average.avgWriteTimePerTxn);
        assertNotNull(average.avgWriteTimePerEvent);
    }

    @Test
    public void testBuildPeriodAverageWithZeroPeriodWriteTxnCount() throws Exception {
        // 准备测试数据
        MetricsManager metricsManager = new MetricsManager(1L, "testTask", TaskType.Dumper);

        // 创建当前快照
        MetricsManager.MetricsSnapshot currentSnapshot = new MetricsManager.MetricsSnapshot(1L);
        currentSnapshot.timestamp = System.currentTimeMillis();
        currentSnapshot.streamMetrics = new HashMap<>();

        // 创建流指标，但不增加任何事务计数
        StreamMetrics streamMetrics = new StreamMetrics("test-stream");
        streamMetrics.incrementTotalWriteEventCount();
        streamMetrics.incrementTotalWriteEventCount();
        streamMetrics.incrementTotalWriteEventCount();

        currentSnapshot.streamMetrics.put("test-stream", streamMetrics);

        // 调用测试方法
        Map<String, MetricsManager.StreamMetricsAverage> result = metricsManager.buildPeriodAverage(currentSnapshot);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());

        MetricsManager.StreamMetricsAverage average = result.get("test-stream");
        assertNotNull(average);
        assertEquals(BigDecimal.ZERO, average.avgWriteTimePerTxn);
    }

    @Test
    public void testBuildPeriodAverageWithZeroPeriodWriteEventCount() throws Exception {
        // 准备测试数据
        MetricsManager metricsManager = new MetricsManager(1L, "testTask", TaskType.Dumper);

        // 创建当前快照
        MetricsManager.MetricsSnapshot currentSnapshot = new MetricsManager.MetricsSnapshot(1L);
        currentSnapshot.timestamp = System.currentTimeMillis();
        currentSnapshot.streamMetrics = new HashMap<>();

        // 创建流指标，但不增加任何事件计数
        StreamMetrics streamMetrics = new StreamMetrics("test-stream");
        streamMetrics.incrementTotalWriteTxnCount();
        streamMetrics.incrementTotalWriteTxnCount();
        streamMetrics.incrementTotalWriteTxnCount();

        currentSnapshot.streamMetrics.put("test-stream", streamMetrics);

        // 调用测试方法
        Map<String, MetricsManager.StreamMetricsAverage> result = metricsManager.buildPeriodAverage(currentSnapshot);

        // 验证结果
        assertNotNull(result);
        assertEquals(1, result.size());

        MetricsManager.StreamMetricsAverage average = result.get("test-stream");
        assertNotNull(average);
        assertEquals(BigDecimal.ZERO, average.avgWriteTimePerEvent);
    }
}