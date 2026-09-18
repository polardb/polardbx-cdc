/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.domain.po.RplStatMetrics;
import com.aliyun.polardbx.binlog.util.CommonMetricsHelper;
import com.aliyun.polardbx.rpl.common.TaskContext;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class StatisticalProxyDelayTest {

    @Test
    public void testValidPosition() {
        long now = 1_800_000_000_500L;
        BinlogPosition position = new BinlogPosition("binlog.000001", 4, 1, 1_799_999_995L);

        Assert.assertEquals(Long.valueOf(5500L), StatisticalProxy.calculatePositionDelayMillis(position, now));
    }

    @Test
    public void testMissingOrZeroTimestampPosition() {
        long now = 1_800_000_000_500L;
        BinlogPosition zeroTimestamp = new BinlogPosition("binlog.000001", 4, 1, 0);

        // 无有效位点 => 延迟未知，返回 null（该指标不上报）
        Assert.assertNull(StatisticalProxy.calculatePositionDelayMillis(null, now));
        Assert.assertNull(StatisticalProxy.calculatePositionDelayMillis(zeroTimestamp, now));
    }

    @Test
    public void testFuturePosition() {
        long now = 1_800_000_000_500L;
        BinlogPosition position = new BinlogPosition("binlog.000001", 4, 1, 1_800_000_010L);

        // 未来位点视为时钟偏斜，近似追平，返回 0 而非 null
        Assert.assertEquals(Long.valueOf(0L), StatisticalProxy.calculatePositionDelayMillis(position, now));
    }

    @Test
    public void testAddReplicaMetricsSkipNullTrueDelay() {
        RplStatMetrics statMetrics = buildStatMetricsWithoutTrueDelay();

        List<CommonMetrics> commonMetrics = new ArrayList<>();
        CommonMetricsHelper.addReplicaMetrics(commonMetrics, statMetrics, "replica_test_");

        // trueDelayMills 为 null => 不产生该 key 的指标且不抛 NPE
        Assert.assertTrue(commonMetrics.stream().noneMatch(m -> m.getKey().endsWith("trueDelayMills")));
        // 其余 20 个指标正常产生
        Assert.assertEquals(20, commonMetrics.size());
        Assert.assertTrue(commonMetrics.stream().anyMatch(m -> m.getKey().equals("replica_test_outRps")));
    }

    @Test
    public void testAddReplicaMetricsWithTrueDelay() {
        RplStatMetrics statMetrics = buildStatMetricsWithoutTrueDelay();
        statMetrics.setTrueDelayMills(123L);

        List<CommonMetrics> commonMetrics = new ArrayList<>();
        CommonMetricsHelper.addReplicaMetrics(commonMetrics, statMetrics, "replica_test_");

        Assert.assertEquals(21, commonMetrics.size());
        Assert.assertTrue(commonMetrics.stream()
            .anyMatch(m -> m.getKey().equals("replica_test_trueDelayMills") && m.getValue() == 123d));
    }

    @Test
    public void fill_ApplyAttemptWithoutMeasuredDelayUsesCurrentPosition() {
        StatMetrics source = new StatMetrics();
        source.getApplyCount().set(1);
        source.addApplyAttemptCount(1);
        long beforeFill = System.currentTimeMillis();
        RplStatMetrics target = fillWithPosition(source, 5);
        long fillCost = System.currentTimeMillis() - beforeFill;

        Assert.assertNotNull(target.getTrueDelayMills());
        Assert.assertTrue(target.getTrueDelayMills() >= 5000L);
        Assert.assertTrue(target.getTrueDelayMills() <= 6000L + fillCost);
    }

    @Test
    public void fill_NoRecentEventsFallsBackToCurrentPosition() {
        StatMetrics source = new StatMetrics();
        source.getApplyCount().set(1);
        long beforeFill = System.currentTimeMillis();
        RplStatMetrics target = fillWithPosition(source, 3);
        long fillCost = System.currentTimeMillis() - beforeFill;

        Assert.assertNotNull(target.getTrueDelayMills());
        Assert.assertTrue(target.getTrueDelayMills() >= 3000L);
        Assert.assertTrue(target.getTrueDelayMills() <= 4000L + fillCost);
    }

    private static RplStatMetrics fillWithPosition(StatMetrics source, long secondsAgo) {
        long positionTimestamp = System.currentTimeMillis() / 1000 - secondsAgo;
        StatisticalProxy proxy = StatisticalProxy.getInstance();
        proxy.flushInterval = 1;
        try {
            Field position = StatisticalProxy.class.getDeclaredField("position");
            position.setAccessible(true);
            position.set(proxy, "binlog.000001:0000000004#1." + positionTimestamp);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("failed to install test position", e);
        }
        RplStatMetrics target = new RplStatMetrics();
        TaskContext taskContext = mock(TaskContext.class);
        when(taskContext.getTaskId()).thenReturn(1L);
        when(taskContext.getStateMachineId()).thenReturn(2L);
        try (MockedStatic<TaskContext> context = Mockito.mockStatic(TaskContext.class)) {
            context.when(TaskContext::getInstance).thenReturn(taskContext);
            proxy.fill(target, source, null, null);
        }
        return target;
    }

    private static RplStatMetrics buildStatMetricsWithoutTrueDelay() {
        RplStatMetrics statMetrics = new RplStatMetrics();
        statMetrics.setOutRps(1L);
        statMetrics.setApplyCount(1L);
        statMetrics.setInEps(1L);
        statMetrics.setOutBps(1L);
        statMetrics.setInBps(1L);
        statMetrics.setOutInsertRps(1L);
        statMetrics.setOutUpdateRps(1L);
        statMetrics.setOutDeleteRps(1L);
        statMetrics.setReceiveDelay(1L);
        statMetrics.setProcessDelay(1L);
        statMetrics.setMergeBatchSize(1L);
        statMetrics.setRt(1L);
        statMetrics.setSkipCounter(1L);
        statMetrics.setSkipExceptionCounter(1L);
        statMetrics.setPersistMsgCounter(1L);
        statMetrics.setMsgCacheSize(1L);
        statMetrics.setCpuUseRatio(1);
        statMetrics.setMemUseRatio(1);
        statMetrics.setFullGcCount(1L);
        statMetrics.setTotalCommitCount(1L);
        return statMetrics;
    }
}
