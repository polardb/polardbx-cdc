/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.service;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.google.common.collect.Sets;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

public class NodeInfoServiceTest extends BaseTestWithGmsTables {

    @Test
    public void testGetOneNode() {
        NodeInfoService nodeInfoService = SpringContextHolder.getObject(NodeInfoService.class);
        insertNodeInfo();

        NodeInfo nodeInfo = nodeInfoService.getOneNode("cluster1", "1");
        Assert.assertNotNull(nodeInfo);
        Assert.assertEquals("cluster1", nodeInfo.getClusterId());
        Assert.assertEquals("1", nodeInfo.getContainerId());

        nodeInfo = nodeInfoService.getOneNode("cluster2", "1");
        Assert.assertNull(nodeInfo);

        nodeInfo = nodeInfoService.getOneNode("cluster1", "3");
        Assert.assertNull(nodeInfo);
    }

    @Test
    public void testGetAllNodes() {
        insertNodeInfo();
        NodeInfoService nodeInfoService = SpringContextHolder.getObject(NodeInfoService.class);

        setConfig(ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES, "[]");
        List<NodeInfo> nodeInfoList = nodeInfoService.getAllNodes("cluster1");
        Assert.assertEquals(Sets.newHashSet("1", "2"),
            nodeInfoList.stream().map(NodeInfo::getContainerId).collect(Collectors.toSet()));

        setConfig(ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES, "[1]");
        nodeInfoList = nodeInfoService.getAllNodes("cluster1");
        Assert.assertEquals(Sets.newHashSet("2"),
            nodeInfoList.stream().map(NodeInfo::getContainerId).collect(Collectors.toSet()));
    }

    @Test
    public void testGetAliveNodes() {
        insertNodeInfo();
        NodeInfoService nodeInfoService = SpringContextHolder.getObject(NodeInfoService.class);

        setConfig(ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES, "[]");
        setConfig(ConfigKeys.INST_ID, "2");
        List<NodeInfo> nodeInfoList = nodeInfoService.getAliveNodes("cluster1");
        Assert.assertEquals(Sets.newHashSet("2"),
            nodeInfoList.stream().map(NodeInfo::getContainerId).collect(Collectors.toSet()));

        setConfig(ConfigKeys.INST_ID, "1");
        nodeInfoList = nodeInfoService.getAliveNodes("cluster1");
        Assert.assertEquals(Sets.newHashSet("1", "2"),
            nodeInfoList.stream().map(NodeInfo::getContainerId).collect(Collectors.toSet()));

        setConfig(ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES, "[2]");
        nodeInfoList = nodeInfoService.getAliveNodes("cluster1");
        Assert.assertEquals(Sets.newHashSet("1"),
            nodeInfoList.stream().map(NodeInfo::getContainerId).collect(Collectors.toSet()));
    }

    @Test
    public void testGetDeadNodes() {
        insertNodeInfo();
        NodeInfoService nodeInfoService = SpringContextHolder.getObject(NodeInfoService.class);

        setConfig(ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES, "[]");
        setConfig(ConfigKeys.INST_ID, "2");
        List<NodeInfo> nodeInfoList = nodeInfoService.getDeadNodes("cluster1");
        Assert.assertEquals(Sets.newHashSet("1"),
            nodeInfoList.stream().map(NodeInfo::getContainerId).collect(Collectors.toSet()));

        setConfig(ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES, "[1]");
        nodeInfoList = nodeInfoService.getDeadNodes("cluster1");
        Assert.assertEquals(Sets.newHashSet(),
            nodeInfoList.stream().map(NodeInfo::getContainerId).collect(Collectors.toSet()));

        setConfig(ConfigKeys.INST_ID, "1");
        nodeInfoList = nodeInfoService.getDeadNodes("cluster1");
        Assert.assertEquals(Sets.newHashSet(),
            nodeInfoList.stream().map(NodeInfo::getContainerId).collect(Collectors.toSet()));

    }

    private void insertNodeInfo() {
        NodeInfo node1 = new NodeInfo();
        node1.setClusterId("cluster1");
        node1.setContainerId("1");
        node1.setStatus(0);
        node1.setIp("127.1");
        node1.setDaemonPort(1111);
        node1.setAvailablePorts("1111");
        node1.setGmtHeartbeat(DateTime.now().minusMinutes(2).toDate());

        NodeInfo node2 = new NodeInfo();
        node2.setClusterId("cluster1");
        node2.setContainerId("2");
        node2.setStatus(0);
        node2.setIp("127.1");
        node2.setDaemonPort(1111);
        node2.setAvailablePorts("1111");
        node2.setGmtHeartbeat(new Date());

        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        nodeInfoMapper.insertSelective(node1);
        nodeInfoMapper.insertSelective(node2);
    }
}
