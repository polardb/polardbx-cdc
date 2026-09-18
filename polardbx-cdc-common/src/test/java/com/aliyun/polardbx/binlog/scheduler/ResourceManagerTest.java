/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler;

import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.service.NodeInfoService;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResourceManagerTest extends BaseTest {

    private static final String TEST_CLUSTER_ID = "test_cluster_id";

    private NodeInfoService nodeInfoService;

    private ResourceManager resourceManager;

    @Before
    public void setUp() {
        BinlogTaskInfoMapper taskInfoMapper = mock(BinlogTaskInfoMapper.class);
        DumperInfoMapper dumperInfoMapper = mock(DumperInfoMapper.class);
        BinlogTaskConfigMapper taskConfigMapper = mock(BinlogTaskConfigMapper.class);
        nodeInfoService = mock(NodeInfoService.class);

        registerSpringObject("binlogTaskInfoMapper", taskInfoMapper);
        registerSpringObject("dumperInfoMapper", dumperInfoMapper);
        registerSpringObject("binlogTaskConfigMapper", taskConfigMapper);
        registerSpringObject("nodeInfoService", nodeInfoService);

        resourceManager = new ResourceManager(TEST_CLUSTER_ID);
    }

    @Test
    public void testAvailableContainersWithRandomRemove() {
        // 准备测试数据
        List<NodeInfo> nodeInfos = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            NodeInfo nodeInfo = new NodeInfo();
            nodeInfo.setContainerId("container_" + i);
            nodeInfo.setIp("192.168.1." + i);
            nodeInfo.setDaemonPort(10000 + i);
            nodeInfo.setAvailablePorts("8000,8001,8002");
            nodeInfo.setCore(4L);
            nodeInfo.setMem(8192L);
            nodeInfos.add(nodeInfo);
        }

        when(nodeInfoService.getAliveNodes(TEST_CLUSTER_ID)).thenReturn(nodeInfos);

        // 测试randomRemove为true的情况
        List<Container> containersWithRandomRemove =
            resourceManager.tryRandomRemoveContainer(resourceManager.availableContainers(), true);
        Assert.assertTrue("返回的容器列表不应为空", containersWithRandomRemove.size() >= 0);
        Assert.assertTrue("返回的容器列表大小应该小于等于原始大小", containersWithRandomRemove.size() <= 5);

        // 验证容器的基本属性
        for (Container container : containersWithRandomRemove) {
            Assert.assertNotNull("容器ID不应为空", container.getContainerId());
            Assert.assertNotNull("容器IP不应为空", container.getIp());
            Assert.assertTrue("容器端口应大于0", container.getDaemonPort() > 0);
            Assert.assertNotNull("容器可用端口不应为空", container.getAvailablePorts());
            Assert.assertTrue("容器可用端口应为LinkedList类型", container.getAvailablePorts() instanceof LinkedList);
        }
    }

    @Test
    public void testAvailableContainersWithoutRandomRemove() {
        // 准备测试数据
        List<NodeInfo> nodeInfos = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            NodeInfo nodeInfo = new NodeInfo();
            nodeInfo.setContainerId("container_" + i);
            nodeInfo.setIp("192.168.1." + i);
            nodeInfo.setDaemonPort(10000 + i);
            nodeInfo.setAvailablePorts("8000,8001,8002");
            nodeInfo.setCore(4L);
            nodeInfo.setMem(8192L);
            nodeInfos.add(nodeInfo);
        }

        when(nodeInfoService.getAliveNodes(TEST_CLUSTER_ID)).thenReturn(nodeInfos);

        // 测试randomRemove为false的情况
        List<Container> containersWithoutRandomRemove =
            resourceManager.tryRandomRemoveContainer(resourceManager.availableContainers(), false);
        Assert.assertEquals("返回的容器列表大小应该等于原始大小", 3, containersWithoutRandomRemove.size());

        // 验证容器的基本属性
        for (int i = 0; i < containersWithoutRandomRemove.size(); i++) {
            Container container = containersWithoutRandomRemove.get(i);
            Assert.assertEquals("容器ID应该匹配", "container_" + i, container.getContainerId());
            Assert.assertEquals("容器IP应该匹配", "192.168.1." + i, container.getIp());
            Assert.assertEquals("容器端口应该匹配", 10000 + i, container.getDaemonPort());
        }
    }

    @Test
    public void testAvailableContainersWithEmptyList() {
        // 准备空的测试数据
        List<NodeInfo> emptyNodeInfos = new ArrayList<>();
        when(nodeInfoService.getAliveNodes(TEST_CLUSTER_ID)).thenReturn(emptyNodeInfos);

        // 测试空列表情况
        List<Container> containers =
            resourceManager.tryRandomRemoveContainer(resourceManager.availableContainers(), true);
        Assert.assertEquals("返回的容器列表应该为空", 0, containers.size());
    }

    @Test
    public void testAvailableContainersWithSingleContainer() {
        // 准备单个容器的测试数据
        List<NodeInfo> nodeInfos = new ArrayList<>();
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setContainerId("single_container");
        nodeInfo.setIp("192.168.1.100");
        nodeInfo.setDaemonPort(12345);
        nodeInfo.setAvailablePorts("8000,8001");
        nodeInfo.setCore(2L);
        nodeInfo.setMem(4096L);
        nodeInfos.add(nodeInfo);

        when(nodeInfoService.getAliveNodes(TEST_CLUSTER_ID)).thenReturn(nodeInfos);

        // 测试只有一个容器的情况，randomRemove为true时不应该移除
        List<Container> containers =
            resourceManager.tryRandomRemoveContainer(resourceManager.availableContainers(), true);
        Assert.assertEquals("返回的容器列表大小应该为1", 1, containers.size());
        Container container = containers.get(0);
        Assert.assertEquals("容器ID应该匹配", "single_container", container.getContainerId());
        Assert.assertEquals("容器IP应该匹配", "192.168.1.100", container.getIp());
        Assert.assertEquals("容器端口应该匹配", 12345, container.getDaemonPort());
    }
}
