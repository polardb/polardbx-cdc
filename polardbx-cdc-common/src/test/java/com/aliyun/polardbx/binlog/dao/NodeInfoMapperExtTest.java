/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @author yudong
 * @since 2023/7/3 11:56
 **/
public class NodeInfoMapperExtTest extends BaseTest {

    @Test
    public void testGetAliveNodes() {
        NodeInfoMapperExt nodeInfoMapperExt = SpringContextHolder.getObject(NodeInfoMapperExt.class);
        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);

        NodeInfo node1 = new NodeInfo();
        node1.setClusterId("heartbeat-mapper-test");
        node1.setContainerId("1");
        node1.setStatus(0);
        node1.setIp("127.1");
        node1.setDaemonPort(1111);
        node1.setAvailablePorts("1111");

        NodeInfo node2 = new NodeInfo();
        node2.setClusterId("heartbeat-mapper-test");
        node2.setContainerId("2");
        node2.setStatus(0);
        node2.setIp("127.1");
        node2.setDaemonPort(2222);
        node2.setAvailablePorts("2222");

        nodeInfoMapper.delete(s -> s.where());

        nodeInfoMapper.insertSelective(node1);
        nodeInfoMapper.insertSelective(node2);
        List<NodeInfo> aliveNodes = nodeInfoMapperExt.getAliveNodes("heartbeat-mapper-test", 5000, "");
        Assert.assertEquals(2, aliveNodes.size());

        try {
            Thread.sleep(5000);
        } catch (Exception e) {

        }

        aliveNodes = nodeInfoMapperExt.getAliveNodes("heartbeat-mapper-test", 5000, "");
        Assert.assertEquals(0, aliveNodes.size());
    }

    @Test
    public void testGetDeadNodes() {
        NodeInfoMapperExt nodeInfoMapperExt = SpringContextHolder.getObject(NodeInfoMapperExt.class);
        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);

        NodeInfo node1 = new NodeInfo();
        node1.setClusterId("heartbeat-mapper-test");
        node1.setContainerId("1");
        node1.setStatus(0);
        node1.setIp("127.1");
        node1.setDaemonPort(1111);
        node1.setAvailablePorts("1111");

        NodeInfo node2 = new NodeInfo();
        node2.setClusterId("heartbeat-mapper-test");
        node2.setContainerId("2");
        node2.setStatus(0);
        node2.setIp("127.1");
        node2.setDaemonPort(2222);
        node2.setAvailablePorts("2222");

        nodeInfoMapper.delete(s -> s.where());

        nodeInfoMapper.insertSelective(node1);
        nodeInfoMapper.insertSelective(node2);
        List<NodeInfo> aliveNodes = nodeInfoMapperExt.getDeadNodes("heartbeat-mapper-test", 5000, "");
        Assert.assertEquals(0, aliveNodes.size());

        try {
            Thread.sleep(5000);
        } catch (Exception e) {

        }

        aliveNodes = nodeInfoMapperExt.getDeadNodes("heartbeat-mapper-test", 5000, "");
        Assert.assertEquals(2, aliveNodes.size());
    }

    /**
     * 探活的时间比较必须在DB侧完成，因此按cluster_type查询存活节点时，
     * 只有心跳未超时的节点才会被返回
     */
    @Test
    public void testGetAliveNodesByClusterType() {
        NodeInfoMapperExt nodeInfoMapperExt = SpringContextHolder.getObject(NodeInfoMapperExt.class);
        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        nodeInfoMapper.delete(s -> s.where());

        Date staleTime = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        // 心跳正常的REPLICA节点
        nodeInfoMapper.insertSelective(buildNode("replica-alive", "REPLICA", null));
        // 心跳超时的REPLICA节点
        nodeInfoMapper.insertSelective(buildNode("replica-stale", "REPLICA", staleTime));
        // 心跳正常但集群类型不匹配的节点
        nodeInfoMapper.insertSelective(buildNode("binlog-alive", "BINLOG", null));

        List<NodeInfo> nodes = nodeInfoMapperExt.getAliveNodesByClusterType("REPLICA", 2 * 60 * 1000);

        Assert.assertEquals(1, nodes.size());
        Assert.assertEquals("replica-alive", nodes.get(0).getContainerId());
        Assert.assertEquals("REPLICA", nodes.get(0).getClusterType());

        Assert.assertTrue(nodeInfoMapperExt.getAliveNodesByClusterType("NOT_EXIST", 2 * 60 * 1000).isEmpty());
    }

    private NodeInfo buildNode(String containerId, String clusterType, Date gmtHeartbeat) {
        NodeInfo node = new NodeInfo();
        node.setClusterId("cluster-type-mapper-test");
        node.setContainerId(containerId);
        node.setStatus(0);
        node.setIp("127.1");
        node.setDaemonPort(1111);
        node.setAvailablePorts("1111");
        node.setClusterType(clusterType);
        node.setGmtHeartbeat(gmtHeartbeat);
        return node;
    }
}
