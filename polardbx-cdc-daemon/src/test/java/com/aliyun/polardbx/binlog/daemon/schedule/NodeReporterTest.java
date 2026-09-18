/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapperExt;
import com.aliyun.polardbx.binlog.domain.NodeRole;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class NodeReporterTest extends BaseTest {

    private NodeReporter nodeReporter;
    private AutoCloseable closeable;

    @Mock
    private NodeInfoMapper nodeInfoMapper;

    @Mock
    private NodeInfoMapperExt nodeInfoMapperExt;

    @Mock
    private NodeInfo nodeInfo;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        // Mock Spring context
        registerSpringObject("nodeInfoMapper", nodeInfoMapper);
        registerSpringObject("nodeInfoMapperExt", nodeInfoMapperExt);

        // Mock configuration
        mockConfig(ConfigKeys.INST_ID, "test-inst-id");
        mockConfig(ConfigKeys.INST_IP, "127.0.0.1");
        mockConfig(ConfigKeys.DAEMON_PORT, "8080");
        mockConfig(ConfigKeys.CPU_CORES, "4");
        mockConfig(ConfigKeys.MEM_SIZE, "8192");
        mockConfig(ConfigKeys.POLARX_INST_ID, "polarx-inst-id");
        mockConfig(ConfigKeys.COMMON_PORTS, "{\"cdc1\":\"18080\",\"cdc2\":\"18081\"}");
        mockConfig(ConfigKeys.BINLOGX_STREAM_GROUP_NAME, "test-group-name");
        mockConfig(ConfigKeys.CLUSTER_ROLE, "master");
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    public void testExecWithExistingNode_UpdateNode() {
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class)) {
            // Given
            nodeReporter = new NodeReporter("test-cluster", ClusterType.BINLOG.name(), "test-node", 1000);
            when(nodeInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.of(nodeInfo));
            when(nodeInfo.getId()).thenReturn(1L);
            when(RuntimeLeaderElector.isDaemonLeader()).thenReturn(true);

            // When
            nodeReporter.exec();

            // Then
            verify(nodeInfoMapperExt).updateNodeHeartbeat(eq(1L), eq(NodeRole.MASTER.getName()),
                eq(ClusterType.BINLOG.name()), anyString(), eq(true));
            verify(nodeInfoMapper, never()).insertSelective(any());
        }
    }

    @Test
    public void testExecWithExistingNodeAsSlave_UpdateNode() {
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class)) {
            // Given
            nodeReporter = new NodeReporter("test-cluster", ClusterType.BINLOG.name(), "test-node", 1000);
            when(nodeInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.of(nodeInfo));
            when(nodeInfo.getId()).thenReturn(1L);
            when(RuntimeLeaderElector.isDaemonLeader()).thenReturn(false);

            // When
            nodeReporter.exec();

            // Then
            verify(nodeInfoMapperExt).updateNodeHeartbeat(eq(1L), eq(NodeRole.SLAVE.getName()),
                eq(ClusterType.BINLOG.name()), anyString(), eq(true));
            verify(nodeInfoMapper, never()).insertSelective(any());
        }
    }

    @Test
    public void testExecWithoutExistingNode_InsertNode_BINLOG() {
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class)) {
            // Given
            nodeReporter = new NodeReporter("test-cluster", ClusterType.BINLOG.name(), "test-node", 1000);
            when(nodeInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());
            when(RuntimeLeaderElector.isDaemonLeader()).thenReturn(true);

            // When
            nodeReporter.exec();

            // Then
            verify(nodeInfoMapperExt, never()).updateNodeHeartbeat(anyLong(), anyString(), anyString(), anyString(),
                anyBoolean());
            verify(nodeInfoMapper).insertSelective(any(NodeInfo.class));
        }
    }

    @Test
    public void testExecWithoutExistingNode_InsertNode_BINLOG_X() {
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class)) {
            // Given
            nodeReporter = new NodeReporter("test-cluster", ClusterType.BINLOG_X.name(), "test-node", 1000);
            when(nodeInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());
            when(RuntimeLeaderElector.isDaemonLeader()).thenReturn(false);

            // When
            nodeReporter.exec();

            // Then
            verify(nodeInfoMapperExt, never()).updateNodeHeartbeat(anyLong(), anyString(), anyString(), anyString(),
                anyBoolean());
            verify(nodeInfoMapper).insertSelective(any(NodeInfo.class));
        }
    }

    @Test
    public void testBuildPortStr() throws Exception {
        // Given
        nodeReporter = new NodeReporter("test-cluster", ClusterType.BINLOG.name(), "test-node", 1000);
        mockConfig(ConfigKeys.COMMON_PORTS, "{\"cdc1\":\"18080\",\"cdc2\":\"18081\",\"other\":\"20000\"}");

        // When
        // 使用反射调用私有方法
        java.lang.reflect.Method method = NodeReporter.class.getDeclaredMethod("buildPortStr");
        method.setAccessible(true);
        String result = (String) method.invoke(nodeReporter);

        // Then
        // 只应该包含以"cdc"开头的端口值
        assertEquals("18080,18081", result);
    }

    @Test
    public void testUpdateNode() throws Exception {
        // Given
        nodeReporter = new NodeReporter("test-cluster", ClusterType.BINLOG.name(), "test-node", 1000);
        when(nodeInfo.getId()).thenReturn(1L);
        when(nodeInfoMapperExt.updateNodeHeartbeat(anyLong(), anyString(), anyString(), anyString(), anyBoolean()))
            .thenReturn(1);

        // When
        java.lang.reflect.Method method =
            NodeReporter.class.getDeclaredMethod("updateNode", NodeInfo.class, String.class, String.class);
        method.setAccessible(true);
        method.invoke(nodeReporter, nodeInfo, NodeRole.MASTER.getName(), "cluster-role");

        // Then
        verify(nodeInfoMapperExt).updateNodeHeartbeat(eq(1L), eq(NodeRole.MASTER.getName()),
            eq(ClusterType.BINLOG.name()), eq("cluster-role"), eq(true));
    }

    @Test
    public void testInsetNode_BINLOG() throws Exception {
        // Given
        nodeReporter = new NodeReporter("test-cluster", ClusterType.BINLOG.name(), "test-node", 1000);

        // When
        java.lang.reflect.Method method = NodeReporter.class.getDeclaredMethod("insetNode", String.class, String.class);
        method.setAccessible(true);
        method.invoke(nodeReporter, NodeRole.MASTER.getName(), "cluster-role");

        // Then
        verify(nodeInfoMapper).insertSelective(any(NodeInfo.class));
    }

    @Test
    public void testInsetNode_BINLOG_X() throws Exception {
        // Given
        nodeReporter = new NodeReporter("test-cluster", ClusterType.BINLOG_X.name(), "test-node", 1000);

        // When
        java.lang.reflect.Method method = NodeReporter.class.getDeclaredMethod("insetNode", String.class, String.class);
        method.setAccessible(true);
        method.invoke(nodeReporter, NodeRole.MASTER.getName(), "cluster-role");

        // Then
        verify(nodeInfoMapper).insertSelective(any(NodeInfo.class));
    }
}
