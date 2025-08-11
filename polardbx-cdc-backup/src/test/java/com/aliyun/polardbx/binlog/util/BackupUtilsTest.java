/*
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Date;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

public class BackupUtilsTest extends BaseTest {
    private static final int DAEMON_PORT = 8080;
    private static final String DAEMON_HOST = "127.0.0.1";
    private static final String GROUP_NAME = "backup_group";

    @Before
    public void prepare() {
        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setGmtCreated(new Date(System.currentTimeMillis()));
        nodeInfo.setGmtModified(new Date(System.currentTimeMillis()));
        nodeInfo.setLastTsoHeartbeat(new Date(System.currentTimeMillis()));
        nodeInfo.setGmtHeartbeat(new Date(System.currentTimeMillis()));
        nodeInfo.setClusterRole("master");
        nodeInfo.setClusterId(getString(ConfigKeys.CLUSTER_ID));
        nodeInfo.setContainerId("test");
        nodeInfo.setIp(DAEMON_HOST);
        nodeInfo.setDaemonPort(DAEMON_PORT);
        nodeInfo.setGroupName(GROUP_NAME);
        nodeInfo.setAvailablePorts("");
        nodeInfo.setLatestCursor("");
        nodeInfo.setRole("M");
        nodeInfoMapper.insert(nodeInfo);
    }

    @Test
    public void testGetDaemonAddress() {
        String daemonHost = BackupUtils.getDaemonAddress(GROUP_NAME, getString(ConfigKeys.CLUSTER_ID)).getKey();
        Assert.assertEquals(DAEMON_HOST, daemonHost);
    }
}
