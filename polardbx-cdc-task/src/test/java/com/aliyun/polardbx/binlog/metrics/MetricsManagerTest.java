/*
 * *
 *  * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 *  * All rights reserved.
 *  * <p>
 *  * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

package com.aliyun.polardbx.binlog.metrics;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;

import org.apache.commons.lang3.StringUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.mybatis.dynamic.sql.select.CountDSLCompleter;

public class MetricsManagerTest {

    private MockedStatic<DynamicApplicationConfig> mockedConfig;
    private MockedStatic<SpringContextHolder> mockedSpring;
    private BinlogPhyDdlHistoryMapper phyMapper;
    private BinlogLogicMetaHistoryMapper logicMapper;

    @Before
    public void setUp() {
        mockedConfig = mockStatic(DynamicApplicationConfig.class);
        mockedSpring = mockStatic(SpringContextHolder.class);

        phyMapper = Mockito.mock(BinlogPhyDdlHistoryMapper.class);
        logicMapper = Mockito.mock(BinlogLogicMetaHistoryMapper.class);
        when(phyMapper.count(any(CountDSLCompleter.class))).thenReturn(42L);
        when(logicMapper.count(any(CountDSLCompleter.class))).thenReturn(10L);

        mockedSpring.when(() -> SpringContextHolder.getObject(BinlogPhyDdlHistoryMapper.class)).thenReturn(phyMapper);
        mockedSpring.when(() -> SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class))
            .thenReturn(logicMapper);

        // 默认配置
        mockedConfig.when(() -> DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_COLLECT_DDL_HISTORY_COUNT_ENABLED))
            .thenReturn(true);
        mockedConfig.when(
                () -> DynamicApplicationConfig.getInt(ConfigKeys.TASK_COLLECT_DDL_HISTORY_COUNT_INTERVAL_ROUND))
            .thenReturn(60);
        mockedConfig.when(() -> DynamicApplicationConfig.getBoolean(ConfigKeys.PRINT_METRICS))
            .thenReturn(false);
    }

    @After
    public void tearDown() {
        mockedConfig.close();
        mockedSpring.close();
    }

    @Test
    public void contactJVMMetrics() {
        MetricsManager manager = Mockito.mock(MetricsManager.class, Mockito.CALLS_REAL_METHODS);
        StringBuilder sb = new StringBuilder();
        manager.contactJvmMetrics(manager.buildSnapshot(), sb);
        Assert.assertTrue(StringUtils.isNotBlank(sb.toString()));
    }

    @Test
    public void testDdlHistoryCount_WhenDisabled_ReturnsZero() throws Exception {
        mockedConfig.when(() -> DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_COLLECT_DDL_HISTORY_COUNT_ENABLED))
            .thenReturn(false);

        MetricsManager manager = new MetricsManager();
        Object snapshot = manager.buildSnapshot();

        long phyCount = getCoreMetricsField(snapshot, "phyDdlHistoryCount");
        long logicCount = getCoreMetricsField(snapshot, "logicDdlHistoryCount");

        Assert.assertEquals(0, phyCount);
        Assert.assertEquals(0, logicCount);
    }

    @Test
    public void testDdlHistoryCount_FirstRoundAlwaysCollects() throws Exception {
        // 需求：首次启动第一轮一定采集一次（即使 interval 很大，如默认 720）
        mockedConfig.when(() -> DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_COLLECT_DDL_HISTORY_COUNT_ENABLED))
            .thenReturn(true);
        mockedConfig.when(
                () -> DynamicApplicationConfig.getInt(ConfigKeys.TASK_COLLECT_DDL_HISTORY_COUNT_INTERVAL_ROUND))
            .thenReturn(720);

        MetricsManager manager = new MetricsManager();

        // 首轮即采集
        Object s1 = manager.buildSnapshot();
        Assert.assertEquals(42, getCoreMetricsField(s1, "phyDdlHistoryCount"));
        Assert.assertEquals(10, getCoreMetricsField(s1, "logicDdlHistoryCount"));
    }

    @Test
    public void testDdlHistoryCount_RespectsIntervalRound() throws Exception {
        mockedConfig.when(() -> DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_COLLECT_DDL_HISTORY_COUNT_ENABLED))
            .thenReturn(true);
        mockedConfig.when(
                () -> DynamicApplicationConfig.getInt(ConfigKeys.TASK_COLLECT_DDL_HISTORY_COUNT_INTERVAL_ROUND))
            .thenReturn(3);

        MetricsManager manager = new MetricsManager();
        manager.buildSnapshot(); // round0: 0%3==0 采集
        manager.buildSnapshot(); // round1: 不采集
        manager.buildSnapshot(); // round2: 不采集
        manager.buildSnapshot(); // round3: 3%3==0 采集

        // interval=3，4 轮内共采集 2 次（首轮 + 第4轮）
        Mockito.verify(phyMapper, Mockito.times(2)).count(any(CountDSLCompleter.class));
        Mockito.verify(logicMapper, Mockito.times(2)).count(any(CountDSLCompleter.class));
    }

    @Test
    public void testDdlHistoryCount_WhenEnabledWithInterval1_CollectsEveryRound() throws Exception {
        mockedConfig.when(() -> DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_COLLECT_DDL_HISTORY_COUNT_ENABLED))
            .thenReturn(true);
        mockedConfig.when(
                () -> DynamicApplicationConfig.getInt(ConfigKeys.TASK_COLLECT_DDL_HISTORY_COUNT_INTERVAL_ROUND))
            .thenReturn(1);

        MetricsManager manager = new MetricsManager();
        manager.buildSnapshot();
        manager.buildSnapshot();
        manager.buildSnapshot();

        // interval=1，每轮都采集，3 轮共 3 次
        Mockito.verify(phyMapper, Mockito.times(3)).count(any(CountDSLCompleter.class));
    }

    @Test
    public void testDdlHistoryCount_CacheRetainsBetweenRounds() throws Exception {
        mockedConfig.when(() -> DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_COLLECT_DDL_HISTORY_COUNT_ENABLED))
            .thenReturn(true);
        mockedConfig.when(
                () -> DynamicApplicationConfig.getInt(ConfigKeys.TASK_COLLECT_DDL_HISTORY_COUNT_INTERVAL_ROUND))
            .thenReturn(2);
        // 两次实际采集分别返回 100、200，用于区分是否重新查询
        when(phyMapper.count(any(CountDSLCompleter.class))).thenReturn(100L, 200L);

        MetricsManager manager = new MetricsManager();

        // 第 1 轮 round0：0%2==0 采集 → 100
        Object s1 = manager.buildSnapshot();
        Assert.assertEquals(100, getCoreMetricsField(s1, "phyDdlHistoryCount"));

        // 第 2 轮 round1：不采集，返回上次缓存 100
        Object s2 = manager.buildSnapshot();
        Assert.assertEquals(100, getCoreMetricsField(s2, "phyDdlHistoryCount"));

        // 第 3 轮 round2：2%2==0 采集 → 200
        Object s3 = manager.buildSnapshot();
        Assert.assertEquals(200, getCoreMetricsField(s3, "phyDdlHistoryCount"));
    }

    private long getCoreMetricsField(Object snapshot, String fieldName) throws Exception {
        Field coreField = snapshot.getClass().getDeclaredField("aggregateCoreMetrics");
        coreField.setAccessible(true);
        Object coreMetrics = coreField.get(snapshot);
        Field targetField = coreMetrics.getClass().getDeclaredField(fieldName);
        targetField.setAccessible(true);
        return (long) targetField.get(coreMetrics);
    }
}
