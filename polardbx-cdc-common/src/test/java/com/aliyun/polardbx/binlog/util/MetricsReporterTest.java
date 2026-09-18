/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.CommonMetrics;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.NodeRole;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.http.entity.ContentType;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_PORT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * MetricsReporter 单元测试
 */
public class MetricsReporterTest extends BaseTest {

    /**
     * 测试 leaderReport：当 binlog_node_info 中没有 role='M' 的节点时，
     * 应跳过上报，不发起 HTTP 调用。
     */
    @Test
    public void testLeaderReport_emptyNodeList() throws Exception {
        mockConfig(CLUSTER_ID, "test_cluster_1");

        NodeInfoMapper mockMapper = mock(NodeInfoMapper.class);
        when(mockMapper.select(any(SelectDSLCompleter.class))).thenReturn(Collections.emptyList());
        registerSpringObject(NodeInfoMapper.class, mockMapper);

        try (MockedStatic<PooledHttpHelper> mockedHttp = Mockito.mockStatic(PooledHttpHelper.class)) {
            List<CommonMetrics> metricsList = buildMetricsList();
            MetricsReporter.leaderReport(metricsList);

            // 节点列表为空，不应发起 HTTP 请求
            mockedHttp.verify(() -> PooledHttpHelper.doPost(
                anyString(), any(ContentType.class), anyString(), anyInt()), never());
        }
    }

    /**
     * 测试 leaderReport：当存在 role='M' 的 MASTER 节点时，
     * 应拼接正确的 URL 并发起 HTTP POST。
     */
    @Test
    public void testLeaderReport_withMasterNode() throws Exception {
        mockConfig(CLUSTER_ID, "test_cluster_1");

        NodeInfo masterNode = new NodeInfo();
        masterNode.setIp("192.168.1.100");
        masterNode.setDaemonPort(3007);
        masterNode.setRole(NodeRole.MASTER.getName());
        masterNode.setClusterId("test_cluster_1");

        NodeInfoMapper mockMapper = mock(NodeInfoMapper.class);
        when(mockMapper.select(any(SelectDSLCompleter.class)))
            .thenReturn(Collections.singletonList(masterNode));
        registerSpringObject(NodeInfoMapper.class, mockMapper);

        try (MockedStatic<PooledHttpHelper> mockedHttp = Mockito.mockStatic(PooledHttpHelper.class)) {
            mockedHttp.when(() -> PooledHttpHelper.doPost(
                anyString(), any(ContentType.class), anyString(), anyInt())).thenReturn("ok");

            List<CommonMetrics> metricsList = buildMetricsList();
            MetricsReporter.leaderReport(metricsList);

            // 验证使用了正确的 URL（由 MASTER 节点的 IP + daemonPort 拼接）
            String expectedUrl = "http://192.168.1.100:3007/cdc/reports";
            mockedHttp.verify(() -> PooledHttpHelper.doPost(
                eq(expectedUrl), eq(ContentType.APPLICATION_JSON), anyString(), eq(1000)), times(1));
        }
    }

    /**
     * 测试 leaderReport：当存在多个 MASTER 节点时，
     * 应使用第一个节点的信息。
     */
    @Test
    public void testLeaderReport_multipleNodes_usesFirst() throws Exception {
        mockConfig(CLUSTER_ID, "test_cluster_1");

        NodeInfo firstNode = new NodeInfo();
        firstNode.setIp("10.0.0.1");
        firstNode.setDaemonPort(3007);
        firstNode.setRole(NodeRole.MASTER.getName());

        NodeInfo secondNode = new NodeInfo();
        secondNode.setIp("10.0.0.2");
        secondNode.setDaemonPort(3008);
        secondNode.setRole(NodeRole.MASTER.getName());

        List<NodeInfo> nodeList = new ArrayList<>();
        nodeList.add(firstNode);
        nodeList.add(secondNode);

        NodeInfoMapper mockMapper = mock(NodeInfoMapper.class);
        when(mockMapper.select(any(SelectDSLCompleter.class))).thenReturn(nodeList);
        registerSpringObject(NodeInfoMapper.class, mockMapper);

        try (MockedStatic<PooledHttpHelper> mockedHttp = Mockito.mockStatic(PooledHttpHelper.class)) {
            mockedHttp.when(() -> PooledHttpHelper.doPost(
                anyString(), any(ContentType.class), anyString(), anyInt())).thenReturn("ok");

            MetricsReporter.leaderReport(buildMetricsList());

            // 应该使用第一个节点的 IP 和端口
            String expectedUrl = "http://10.0.0.1:3007/cdc/reports";
            mockedHttp.verify(() -> PooledHttpHelper.doPost(
                eq(expectedUrl), eq(ContentType.APPLICATION_JSON), anyString(), eq(1000)), times(1));
        }
    }

    /**
     * 测试 leaderReport：当 doPost 抛出 IOException 时，
     * 应捕获异常，不向外抛出。
     */
    @Test
    public void testLeaderReport_ioException() throws Exception {
        mockConfig(CLUSTER_ID, "test_cluster_1");

        NodeInfo masterNode = new NodeInfo();
        masterNode.setIp("192.168.1.100");
        masterNode.setDaemonPort(3007);
        masterNode.setRole(NodeRole.MASTER.getName());

        NodeInfoMapper mockMapper = mock(NodeInfoMapper.class);
        when(mockMapper.select(any(SelectDSLCompleter.class)))
            .thenReturn(Collections.singletonList(masterNode));
        registerSpringObject(NodeInfoMapper.class, mockMapper);

        try (MockedStatic<PooledHttpHelper> mockedHttp = Mockito.mockStatic(PooledHttpHelper.class)) {
            mockedHttp.when(() -> PooledHttpHelper.doPost(
                    anyString(), any(ContentType.class), anyString(), anyInt()))
                .thenThrow(new IOException("connection refused"));

            // 不应向外抛出异常
            MetricsReporter.leaderReport(buildMetricsList());
        }
    }

    /**
     * 测试 leaderReport：当 doPost 抛出 URISyntaxException 时，
     * 应捕获异常，不向外抛出。
     */
    @Test
    public void testLeaderReport_uriSyntaxException() throws Exception {
        mockConfig(CLUSTER_ID, "test_cluster_1");

        NodeInfo masterNode = new NodeInfo();
        masterNode.setIp("192.168.1.100");
        masterNode.setDaemonPort(3007);
        masterNode.setRole(NodeRole.MASTER.getName());

        NodeInfoMapper mockMapper = mock(NodeInfoMapper.class);
        when(mockMapper.select(any(SelectDSLCompleter.class)))
            .thenReturn(Collections.singletonList(masterNode));
        registerSpringObject(NodeInfoMapper.class, mockMapper);

        try (MockedStatic<PooledHttpHelper> mockedHttp = Mockito.mockStatic(PooledHttpHelper.class)) {
            mockedHttp.when(() -> PooledHttpHelper.doPost(
                    anyString(), any(ContentType.class), anyString(), anyInt()))
                .thenThrow(new URISyntaxException("bad uri", "invalid"));

            // 不应向外抛出异常
            MetricsReporter.leaderReport(buildMetricsList());
        }
    }

    /**
     * 测试 report：正常场景，应向本地 daemon 发送 HTTP POST。
     */
    @Test
    public void testReport_normalCase() throws Exception {
        mockConfig(DAEMON_PORT, "3007");

        try (MockedStatic<PooledHttpHelper> mockedHttp = Mockito.mockStatic(PooledHttpHelper.class)) {
            mockedHttp.when(() -> PooledHttpHelper.doPost(
                anyString(), any(ContentType.class), anyString(), anyInt())).thenReturn("ok");

            MetricsReporter.report(buildMetricsList());

            String expectedUrl = "http://127.0.0.1:3007/cdc/reports";
            mockedHttp.verify(() -> PooledHttpHelper.doPost(
                eq(expectedUrl), eq(ContentType.APPLICATION_JSON), anyString(), eq(1000)), times(1));
        }
    }

    /**
     * 测试 report：当 doPost 抛出异常时应捕获，不向外抛出。
     */
    @Test
    public void testReport_exception() throws Exception {
        mockConfig(DAEMON_PORT, "3007");

        try (MockedStatic<PooledHttpHelper> mockedHttp = Mockito.mockStatic(PooledHttpHelper.class)) {
            mockedHttp.when(() -> PooledHttpHelper.doPost(
                    anyString(), any(ContentType.class), anyString(), anyInt()))
                .thenThrow(new IOException("connection refused"));

            // 不应向外抛出异常
            MetricsReporter.report(buildMetricsList());
        }
    }

    /**
     * 构造测试用的 metrics 列表
     */
    private List<CommonMetrics> buildMetricsList() {
        List<CommonMetrics> list = new ArrayList<>();
        list.add(CommonMetrics.builder().key("test_metric_1").type(1).value(100).build());
        list.add(CommonMetrics.builder().key("test_metric_2").type(2).value(200).build());
        return list;
    }
}
