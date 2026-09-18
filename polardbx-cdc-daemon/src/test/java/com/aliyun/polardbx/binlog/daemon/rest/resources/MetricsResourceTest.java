/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.daemon.rest.resources.MetricsResource.AggregationType;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.taskmeta.TaskStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.eq;

/**
 * MetricsResource 聚合查询接口单元测试
 */
@RunWith(MockitoJUnitRunner.class)
public class MetricsResourceTest extends BaseTest {

    private MetricsResource metricsResource;

    @Before
    public void setUp() {
        metricsResource = new MetricsResource();
        metricsResource.invalidateAll();
        mockConfig(ConfigKeys.INST_IP, "127.0.0.1");
        mockConfig(ConfigKeys.DAEMON_PORT, "3007");
        mockConfig(ConfigKeys.CLUSTER_ID, "test_cluster");
        mockConfig(ConfigKeys.CLUSTER_TYPE, "REPLICA_INC");
    }

    /**
     * 构造 RplTask 对象
     */
    private RplTask buildTask(long id, String type) {
        RplTask task = new RplTask();
        task.setId(id);
        task.setType(type);
        task.setStatus(TaskStatus.RUNNING.name());
        task.setWorker("127.0.0.1:3007");
        task.setClusterId("test_cluster");
        return task;
    }

    /**
     * 向 CACHE 中填入指标（通过调用 reports 接口）
     */
    private void putMetricsToCache(String key, double value) {
        CommonMetrics metrics = CommonMetrics.builder()
            .key(key)
            .type(1)
            .value(value)
            .build();
        metricsResource.reports(Collections.singletonList(metrics));
    }

    /**
     * 测试单 type 多任务全部上报 - 应返回该 type 的聚合结果，取 max
     */
    @Test
    public void testAggregatedReplicaMetrics_SingleTypeAllPresent() {
        List<RplTask> tasks = Arrays.asList(
            buildTask(1L, ServiceType.REPLICA_INC.name()),
            buildTask(2L, ServiceType.REPLICA_INC.name()),
            buildTask(3L, ServiceType.REPLICA_INC.name())
        );

        // 填入所有任务的 trueDelayMills 到 CACHE
        putMetricsToCache("replica_REPLICA_INC_1_trueDelayMills", 100.0);
        putMetricsToCache("replica_REPLICA_INC_2_trueDelayMills", 300.0);
        putMetricsToCache("replica_REPLICA_INC_3_trueDelayMills", 200.0);

        try (MockedStatic<RuntimeLeaderElector> leaderMock = Mockito.mockStatic(RuntimeLeaderElector.class);
            MockedStatic<DbTaskMetaManager> dbMock = Mockito.mockStatic(DbTaskMetaManager.class)) {
            leaderMock.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(true);
            dbMock.when(() -> DbTaskMetaManager.listClusterTask(
                    eq(TaskStatus.RUNNING), eq("test_cluster")))
                .thenReturn(tasks);
            dbMock.when(() -> DbTaskMetaManager.listClusterTask(
                    eq(TaskStatus.READY), eq("test_cluster")))
                .thenReturn(new ArrayList<>());

            List<CommonMetrics> result = metricsResource.aggregatedReplicaMetrics();

            Assert.assertEquals(1, result.size());
            Assert.assertEquals("replica_REPLICA_INC_trueDelayMills", result.get(0).getKey());
            Assert.assertEquals(300.0, result.get(0).getValue(), 0.001);
        }
    }

    /**
     * 测试单 type 部分任务缺失 - 应返回空列表
     */
    @Test
    public void testAggregatedReplicaMetrics_SingleTypePartialMissing() {
        List<RplTask> tasks = Arrays.asList(
            buildTask(10L, ServiceType.REPLICA_INC.name()),
            buildTask(11L, ServiceType.REPLICA_INC.name()),
            buildTask(12L, ServiceType.REPLICA_INC.name())
        );

        // 只填入 2 个任务的指标，第 3 个缺失
        putMetricsToCache("replica_REPLICA_INC_10_trueDelayMills", 100.0);
        putMetricsToCache("replica_REPLICA_INC_11_trueDelayMills", 200.0);
        // task 12 缺失

        try (MockedStatic<RuntimeLeaderElector> leaderMock = Mockito.mockStatic(RuntimeLeaderElector.class);
            MockedStatic<DbTaskMetaManager> dbMock = Mockito.mockStatic(DbTaskMetaManager.class)) {
            leaderMock.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(true);
            dbMock.when(() -> DbTaskMetaManager.listClusterTask(
                    eq(TaskStatus.RUNNING), eq("test_cluster")))
                .thenReturn(tasks);
            dbMock.when(() -> DbTaskMetaManager.listClusterTask(
                    eq(TaskStatus.READY), eq("test_cluster")))
                .thenReturn(new ArrayList<>());

            List<CommonMetrics> result = metricsResource.aggregatedReplicaMetrics();

            Assert.assertTrue(result.isEmpty());
        }
    }

    /**
     * 测试多 type 混合 - REPLICA_INC 全齐，INC_COPY 缺失
     * 应只返回 REPLICA_INC 的聚合结果
     */
    @Test
    public void testAggregatedReplicaMetrics_MultiTypeMixed() {
        List<RplTask> tasks = Arrays.asList(
            buildTask(20L, ServiceType.REPLICA_INC.name()),
            buildTask(21L, ServiceType.REPLICA_INC.name()),
            buildTask(30L, ServiceType.INC_COPY.name()),
            buildTask(31L, ServiceType.INC_COPY.name())
        );

        // REPLICA_INC 全齐
        putMetricsToCache("replica_REPLICA_INC_20_trueDelayMills", 500.0);
        putMetricsToCache("replica_REPLICA_INC_21_trueDelayMills", 150.0);
        // INC_COPY 只有一个
        putMetricsToCache("replica_INC_COPY_30_trueDelayMills", 80.0);
        // task 31 缺失

        try (MockedStatic<RuntimeLeaderElector> leaderMock = Mockito.mockStatic(RuntimeLeaderElector.class);
            MockedStatic<DbTaskMetaManager> dbMock = Mockito.mockStatic(DbTaskMetaManager.class)) {
            leaderMock.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(true);
            dbMock.when(() -> DbTaskMetaManager.listClusterTask(
                    eq(TaskStatus.RUNNING), eq("test_cluster")))
                .thenReturn(tasks);
            dbMock.when(() -> DbTaskMetaManager.listClusterTask(
                    eq(TaskStatus.READY), eq("test_cluster")))
                .thenReturn(new ArrayList<>());

            List<CommonMetrics> result = metricsResource.aggregatedReplicaMetrics();

            Assert.assertEquals(1, result.size());
            Assert.assertEquals("replica_REPLICA_INC_trueDelayMills", result.get(0).getKey());
            Assert.assertEquals(500.0, result.get(0).getValue(), 0.001);
        }
    }

    /**
     * 测试无 RUNNING 任务 - 应返回空列表
     */
    @Test
    public void testAggregatedReplicaMetrics_NoRunningTasks() {
        try (MockedStatic<RuntimeLeaderElector> leaderMock = Mockito.mockStatic(RuntimeLeaderElector.class);
            MockedStatic<DbTaskMetaManager> dbMock = Mockito.mockStatic(DbTaskMetaManager.class)) {
            leaderMock.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(true);
            dbMock.when(() -> DbTaskMetaManager.listClusterTask(
                    eq(TaskStatus.RUNNING), eq("test_cluster")))
                .thenReturn(new ArrayList<>());
            dbMock.when(() -> DbTaskMetaManager.listClusterTask(
                    eq(TaskStatus.READY), eq("test_cluster")))
                .thenReturn(new ArrayList<>());

            List<CommonMetrics> result = metricsResource.aggregatedReplicaMetrics();

            Assert.assertTrue(result.isEmpty());
        }
    }

    /**
     * 测试多 type 全部齐全 - 应返回所有 type 的聚合结果
     */
    @Test
    public void testAggregatedReplicaMetrics_MultiTypeAllPresent() {
        List<RplTask> tasks = Arrays.asList(
            buildTask(40L, ServiceType.REPLICA_INC.name()),
            buildTask(41L, ServiceType.REPLICA_INC.name()),
            buildTask(50L, ServiceType.INC_COPY.name()),
            buildTask(51L, ServiceType.INC_COPY.name())
        );

        // 全部填入
        putMetricsToCache("replica_REPLICA_INC_40_trueDelayMills", 1000.0);
        putMetricsToCache("replica_REPLICA_INC_41_trueDelayMills", 800.0);
        putMetricsToCache("replica_INC_COPY_50_trueDelayMills", 200.0);
        putMetricsToCache("replica_INC_COPY_51_trueDelayMills", 600.0);

        try (MockedStatic<RuntimeLeaderElector> leaderMock = Mockito.mockStatic(RuntimeLeaderElector.class);
            MockedStatic<DbTaskMetaManager> dbMock = Mockito.mockStatic(DbTaskMetaManager.class)) {
            leaderMock.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(true);
            dbMock.when(() -> DbTaskMetaManager.listClusterTask(
                    eq(TaskStatus.RUNNING), eq("test_cluster")))
                .thenReturn(tasks);
            dbMock.when(() -> DbTaskMetaManager.listClusterTask(
                    eq(TaskStatus.READY), eq("test_cluster")))
                .thenReturn(new ArrayList<>());

            List<CommonMetrics> result = metricsResource.aggregatedReplicaMetrics();

            Assert.assertEquals(1, result.size());

            Map<String, Double> resultMap = result.stream()
                .collect(Collectors.toMap(CommonMetrics::getKey, CommonMetrics::getValue));

            Assert.assertEquals(1000.0, resultMap.get("replica_REPLICA_INC_trueDelayMills"), 0.001);
        }
    }

    /**
     * 测试非 leader 节点 - 应直接返回空列表
     */
    @Test
    public void testAggregatedReplicaMetrics_NonLeader() {
        try (MockedStatic<RuntimeLeaderElector> leaderMock = Mockito.mockStatic(RuntimeLeaderElector.class)) {
            leaderMock.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(false);

            List<CommonMetrics> result = metricsResource.aggregatedReplicaMetrics();

            Assert.assertTrue(result.isEmpty());
        }
    }

    /**
     * 测试 aggregateMetric 求和模式
     */
    @Test
    public void testAggregateMetric_Sum() {
        List<RplTask> tasks = Arrays.asList(
            buildTask(60L, ServiceType.REPLICA_INC.name()),
            buildTask(61L, ServiceType.REPLICA_INC.name()),
            buildTask(62L, ServiceType.REPLICA_INC.name())
        );

        putMetricsToCache("replica_REPLICA_INC_60_outRps", 100.0);
        putMetricsToCache("replica_REPLICA_INC_61_outRps", 250.0);
        putMetricsToCache("replica_REPLICA_INC_62_outRps", 150.0);

        CommonMetrics result = MetricsResource.aggregateMetric(
            tasks, ServiceType.REPLICA_INC.name(), AggregationType.SUM, "outRps");

        Assert.assertNotNull(result);
        Assert.assertEquals("replica_REPLICA_INC_outRps", result.getKey());
        Assert.assertEquals(500.0, result.getValue(), 0.001);

        // 验证聚合结果已写回 CACHE
        CommonMetrics cached = MetricsResource.getMetricsByKey("replica_REPLICA_INC_outRps");
        Assert.assertNotNull(cached);
        Assert.assertEquals(500.0, cached.getValue(), 0.001);
    }

    /**
     * 测试 aggregateMetric 求和模式部分缺失 - 应返回 null 并且 invalid 原先的值
     */
    @Test
    public void testAggregateMetric_SumPartialMissing() throws IOException {
        List<RplTask> tasks = Arrays.asList(
            buildTask(70L, ServiceType.REPLICA_INC.name()),
            buildTask(71L, ServiceType.REPLICA_INC.name())
        );

        putMetricsToCache("replica_REPLICA_INC_70_outRps", 100.0);
        putMetricsToCache("replica_REPLICA_INC_outRps", 100.0);
        // task 71 缺失

        CommonMetrics result = MetricsResource.aggregateMetric(
            tasks, ServiceType.REPLICA_INC.name(), AggregationType.SUM, "outRps");

        Assert.assertNull(result);
        Assert.assertFalse(metricsResource.data().contains("replica_REPLICA_INC_outRps"));
    }

    /**
     * 测试 aggregateMetric 传入空任务列表 - 应返回 null
     */
    @Test
    public void testAggregateMetric_EmptyTasks() {
        CommonMetrics result = MetricsResource.aggregateMetric(
            new ArrayList<>(), ServiceType.REPLICA_INC.name(), AggregationType.MAX, "trueDelayMills");
        Assert.assertNull(result);

        CommonMetrics resultNull = MetricsResource.aggregateMetric(
            null, ServiceType.REPLICA_INC.name(), AggregationType.SUM, "outRps");
        Assert.assertNull(resultNull);
    }

    /**
     * 多流指标汇聚到 leader 后，指标数量会超过旧的 1024 条上限，关键指标不能因此被 LRU 淘汰。
     */
    @Test
    public void testMetricsCacheSupportsMoreThanLegacyLimit() {
        int metricCount = 2 * 1024;
        for (int i = 0; i < metricCount; i++) {
            putMetricsToCache("replica_REPLICA_INC_capacity_test_" + i, i);
        }

        Assert.assertNotNull(MetricsResource.getMetricsByKey("replica_REPLICA_INC_capacity_test_0"));
        Assert.assertNotNull(
            MetricsResource.getMetricsByKey("replica_REPLICA_INC_capacity_test_" + (metricCount - 1)));
    }
}
