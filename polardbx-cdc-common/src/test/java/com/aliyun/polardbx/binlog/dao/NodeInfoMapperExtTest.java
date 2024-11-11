/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * @author yudong
 * @since 2023/7/3 11:56
 **/
public class NodeInfoMapperExtTest extends BaseTestWithGmsTables {

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
}
