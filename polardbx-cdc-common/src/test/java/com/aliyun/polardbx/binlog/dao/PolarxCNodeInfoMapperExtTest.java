/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.PolarxCNodeInfo;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 验证CN节点探活的时间比较在DB侧完成，不依赖JVM侧时间参数
 */
public class PolarxCNodeInfoMapperExtTest extends BaseTest {

    private static final int TIMEOUT_MS = 2 * 60 * 1000;

    private PolarxCNodeInfoMapperExt mapperExt;
    private PolarxCNodeInfoMapper mapper;

    @Before
    public void before() {
        mapperExt = SpringContextHolder.getObject(PolarxCNodeInfoMapperExt.class);
        mapper = SpringContextHolder.getObject(PolarxCNodeInfoMapper.class);
        mapper.delete(s -> s.where());
    }

    @Test
    public void testGetAliveNodes_OnlyReturnsFreshHeartbeatNodes() {
        // gmt_modified 由DB侧的 current_timestamp 填充，属于存活节点
        mapper.insertSelective(buildNode("alive-node", "127.0.0.1", 3306, null));
        // gmt_modified 为一小时之前，属于失活节点
        Date staleTime = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        mapper.insertSelective(buildNode("stale-node", "127.0.0.2", 3307, staleTime));

        List<PolarxCNodeInfo> aliveNodes = mapperExt.getAliveNodes(TIMEOUT_MS);

        Assert.assertEquals(1, aliveNodes.size());
        Assert.assertEquals("127.0.0.1", aliveNodes.get(0).getIp());
        Assert.assertEquals(Integer.valueOf(3306), aliveNodes.get(0).getPort());
        Assert.assertEquals("alive-node", aliveNodes.get(0).getInstId());
    }

    @Test
    public void testGetAliveNodes_AllStale_ReturnsEmpty() {
        Date staleTime = new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1));
        mapper.insertSelective(buildNode("stale-node-1", "127.0.0.1", 3306, staleTime));
        mapper.insertSelective(buildNode("stale-node-2", "127.0.0.2", 3307, staleTime));

        Assert.assertTrue(mapperExt.getAliveNodes(TIMEOUT_MS).isEmpty());
    }

    @Test
    public void testGetAliveNodes_NoNode_ReturnsEmpty() {
        Assert.assertTrue(mapperExt.getAliveNodes(TIMEOUT_MS).isEmpty());
    }

    private PolarxCNodeInfo buildNode(String instId, String ip, int port, Date gmtModified) {
        PolarxCNodeInfo node = new PolarxCNodeInfo();
        node.setCluster("test-cluster");
        node.setInstId(instId);
        node.setNodeid(instId);
        node.setIp(ip);
        node.setPort(port);
        node.setGmtModified(gmtModified);
        return node;
    }
}
