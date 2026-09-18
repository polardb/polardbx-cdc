/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_ENABLE_LIGHT_REBALANCE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RebalanceUtilTest extends BaseTest {

    private NodeInfoMapper nodeInfoMapper;
    private DumperInfoMapper dumperInfoMapper;
    private BinlogTaskInfoMapper taskInfoMapper;

    @Before
    public void setUp() {
        nodeInfoMapper = mock(NodeInfoMapper.class);
        dumperInfoMapper = mock(DumperInfoMapper.class);
        taskInfoMapper = mock(BinlogTaskInfoMapper.class);

        registerSpringObject(NodeInfoMapper.class, nodeInfoMapper);
        registerSpringObject(DumperInfoMapper.class, dumperInfoMapper);
        registerSpringObject(BinlogTaskInfoMapper.class, taskInfoMapper);
    }

    @Test
    public void testBuildTaskNameIdentifier_WithNumericContainerId() {
        long identifier = RebalanceUtil.buildTaskNameIdentifier("12345", "192.168.1.1");
        assertEquals(12345L, identifier);
    }

    @Test
    public void testBuildTaskNameIdentifier_WithNonNumericContainerId() {
        String containerId = "container-abc";
        String ip = "192.168.1.1";
        long expected = (containerId + ip).hashCode();
        long identifier = RebalanceUtil.buildTaskNameIdentifier(containerId, ip);
        assertEquals(expected, identifier);
    }

    @Test
    public void testIsLightRebalance_WithMasterNodeCheck_ReturnTrue() {
        String clusterId = "test-cluster";
        String masterNode = "master-node";

        // 设置配置项
        mockConfig(TOPOLOGY_ENABLE_LIGHT_REBALANCE, "true");

        // Mock all resources to enable light rebalance
        List<NodeInfo> nodeList = Arrays.asList(createNodeInfo(true), createNodeInfo(true));
        List<DumperInfo> dumperList = Arrays.asList(createDumperInfo(true), createDumperInfo(true));
        List<BinlogTaskInfo> taskList = Arrays.asList(createTaskInfo(true), createTaskInfo(true));

        when(nodeInfoMapper.select(any())).thenReturn(nodeList);
        when(dumperInfoMapper.select(any())).thenReturn(dumperList);
        when(taskInfoMapper.select(any())).thenReturn(taskList);

        boolean result = RebalanceUtil.isLightRebalance(clusterId, masterNode, masterNode);
        assertTrue(result);
    }

    @Test
    public void testIsLightRebalance_WithMasterNodeCheck_ReturnFalse_DifferentMasterNodes() {
        String clusterId = "test-cluster";
        String preMasterNode = "master-node-1";
        String postMasterNode = "master-node-2";

        // 设置配置项
        mockConfig(TOPOLOGY_ENABLE_LIGHT_REBALANCE, "true");

        // Mock all resources to enable light rebalance
        List<NodeInfo> nodeList = Arrays.asList(createNodeInfo(true), createNodeInfo(true));
        List<DumperInfo> dumperList = Arrays.asList(createDumperInfo(true), createDumperInfo(true));
        List<BinlogTaskInfo> taskList = Arrays.asList(createTaskInfo(true), createTaskInfo(true));

        when(nodeInfoMapper.select(any())).thenReturn(nodeList);
        when(dumperInfoMapper.select(any())).thenReturn(dumperList);
        when(taskInfoMapper.select(any())).thenReturn(taskList);

        boolean result = RebalanceUtil.isLightRebalance(clusterId, preMasterNode, postMasterNode);
        assertFalse(result);
    }

    @Test
    public void testIsLightRebalance_WithMasterNodeCheck_ReturnFalse_LightRebalanceDisabled() {
        String clusterId = "test-cluster";
        String masterNode = "master-node";

        // 设置配置项
        mockConfig(TOPOLOGY_ENABLE_LIGHT_REBALANCE, "false");

        boolean result = RebalanceUtil.isLightRebalance(clusterId, masterNode, masterNode);
        assertFalse(result);
    }

    @Test
    public void testIsLightRebalance_WithoutMasterNodeCheck_ReturnTrue() {
        String clusterId = "test-cluster";

        // 设置配置项
        mockConfig(TOPOLOGY_ENABLE_LIGHT_REBALANCE, "true");

        // Mock all resources to enable light rebalance
        List<NodeInfo> nodeList = Arrays.asList(createNodeInfo(true), createNodeInfo(true));
        List<DumperInfo> dumperList = Arrays.asList(createDumperInfo(true), createDumperInfo(true));
        List<BinlogTaskInfo> taskList = Arrays.asList(createTaskInfo(true), createTaskInfo(true));

        when(nodeInfoMapper.select(any())).thenReturn(nodeList);
        when(dumperInfoMapper.select(any())).thenReturn(dumperList);
        when(taskInfoMapper.select(any())).thenReturn(taskList);

        boolean result = RebalanceUtil.isLightRebalance(clusterId);
        assertTrue(result);
    }

    @Test
    public void testIsLightRebalance_WithoutMasterNodeCheck_ReturnFalse_LightRebalanceDisabled() {
        String clusterId = "test-cluster";

        // 设置配置项
        mockConfig(TOPOLOGY_ENABLE_LIGHT_REBALANCE, "false");

        boolean result = RebalanceUtil.isLightRebalance(clusterId);
        assertFalse(result);
    }

    @Test
    public void testIsLightRebalance_ReturnFalse_NodeNotEnable() {
        String clusterId = "test-cluster";

        // 设置配置项
        mockConfig(TOPOLOGY_ENABLE_LIGHT_REBALANCE, "true");

        // Mock one node not enable light rebalance
        List<NodeInfo> nodeList = Arrays.asList(createNodeInfo(false), createNodeInfo(true));
        List<DumperInfo> dumperList = Arrays.asList(createDumperInfo(true), createDumperInfo(true));
        List<BinlogTaskInfo> taskList = Arrays.asList(createTaskInfo(true), createTaskInfo(true));

        when(nodeInfoMapper.select(any())).thenReturn(nodeList);
        when(dumperInfoMapper.select(any())).thenReturn(dumperList);
        when(taskInfoMapper.select(any())).thenReturn(taskList);

        boolean result = RebalanceUtil.isLightRebalance(clusterId);
        assertFalse(result);
    }

    @Test
    public void testIsLightRebalance_ReturnFalse_DumperNotEnable() {
        String clusterId = "test-cluster";

        // 设置配置项
        mockConfig(TOPOLOGY_ENABLE_LIGHT_REBALANCE, "true");

        // Mock one dumper not enable light rebalance
        List<NodeInfo> nodeList = Arrays.asList(createNodeInfo(true), createNodeInfo(true));
        List<DumperInfo> dumperList = Arrays.asList(createDumperInfo(false), createDumperInfo(true));
        List<BinlogTaskInfo> taskList = Arrays.asList(createTaskInfo(true), createTaskInfo(true));

        when(nodeInfoMapper.select(any())).thenReturn(nodeList);
        when(dumperInfoMapper.select(any())).thenReturn(dumperList);
        when(taskInfoMapper.select(any())).thenReturn(taskList);

        boolean result = RebalanceUtil.isLightRebalance(clusterId);
        assertFalse(result);
    }

    @Test
    public void testIsLightRebalance_ReturnFalse_TaskNotEnable() {
        String clusterId = "test-cluster";

        // 设置配置项
        mockConfig(TOPOLOGY_ENABLE_LIGHT_REBALANCE, "true");

        // Mock one task not enable light rebalance
        List<NodeInfo> nodeList = Arrays.asList(createNodeInfo(true), createNodeInfo(true));
        List<DumperInfo> dumperList = Arrays.asList(createDumperInfo(true), createDumperInfo(true));
        List<BinlogTaskInfo> taskList = Arrays.asList(createTaskInfo(false), createTaskInfo(true));

        when(nodeInfoMapper.select(any())).thenReturn(nodeList);
        when(dumperInfoMapper.select(any())).thenReturn(dumperList);
        when(taskInfoMapper.select(any())).thenReturn(taskList);

        boolean result = RebalanceUtil.isLightRebalance(clusterId);
        assertFalse(result);
    }

    private NodeInfo createNodeInfo(boolean enableLightRebalance) {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setEnableLightRebalance(enableLightRebalance);
        return nodeInfo;
    }

    private DumperInfo createDumperInfo(boolean enableLightRebalance) {
        DumperInfo dumperInfo = new DumperInfo();
        dumperInfo.setEnableLightRebalance(enableLightRebalance);
        return dumperInfo;
    }

    private BinlogTaskInfo createTaskInfo(boolean enableLightRebalance) {
        BinlogTaskInfo taskInfo = new BinlogTaskInfo();
        taskInfo.setEnableLightRebalance(enableLightRebalance);
        return taskInfo;
    }
}
