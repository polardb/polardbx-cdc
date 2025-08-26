/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.DaemonBootStrap;
import com.aliyun.polardbx.binlog.daemon.schedule.TopologyWatcher;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.enums.ClusterRole;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES;
import static com.aliyun.polardbx.binlog.ConfigKeys.CPU_CORES;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_PORT;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_CLUSTER_HEARTBEAT_TIMEOUT_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_CLUSTER_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.INST_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.MEM_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.POLARX_INST_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_NODE_MINSIZE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class LocalSingleModeTest extends BaseTest {

    private MockedStatic<RuntimeLeaderElector> runtimeLeaderElector;
    private MockedStatic<ServerConfigUtil> serverConfigUtil;

    @Before
    public void before() {
        runtimeLeaderElector = Mockito.mockStatic(RuntimeLeaderElector.class);
        serverConfigUtil = Mockito.mockStatic(ServerConfigUtil.class);
    }

    @After
    public void after() {
        runtimeLeaderElector.close();
        serverConfigUtil.close();
    }

    private void mockNodeInfo() {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setRole("master");
        nodeInfo.setClusterId(getString(CLUSTER_ID));
        nodeInfo.setClusterType("BINLOG");
        nodeInfo.setContainerId(DynamicApplicationConfig.getString(INST_ID));
        nodeInfo.setIp("127.0.0.1");
        nodeInfo.setDaemonPort(DynamicApplicationConfig.getInt(DAEMON_PORT));
        nodeInfo.setAvailablePorts("3006,3007,3008,3009");
        nodeInfo.setCore(DynamicApplicationConfig.getLong(CPU_CORES));
        nodeInfo.setMem(DynamicApplicationConfig.getLong(MEM_SIZE));
        nodeInfo.setStatus(0);
        nodeInfo.setPolarxInstId(DynamicApplicationConfig.getString(POLARX_INST_ID));
        nodeInfo.setClusterRole(ClusterRole.master.name());
        nodeInfo.setGroupName(CommonConstants.GROUP_NAME_GLOBAL);
        NodeInfoMapper binlogNodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        binlogNodeInfoMapper.insertSelective(nodeInfo);
    }

    private void mockStorageInfo() {
        StorageInfoMapper mapper = SpringContextHolder.getObject(StorageInfoMapper.class);
        StorageInfo storageInfo = new StorageInfo();
        storageInfo.setInstId("mock-instId");
        storageInfo.setInstKind(0);
        storageInfo.setStatus(1);
        storageInfo.setIsVip(0);
        storageInfo.setMaxConn(11);
        storageInfo.setStorageType(0);
        storageInfo.setPasswdEnc("mmm");
        storageInfo.setUser("mmm");
        storageInfo.setPort(3306);
        storageInfo.setIp("127.0.0.1");
        storageInfo.setStorageMasterInstId("mock-instId");
        storageInfo.setStorageInstId("mock-instId");
        mapper.insertSelective(storageInfo);
    }

    @Test
    public void testSingleModeFirstStart() throws InterruptedException {
        when(getValue(TOPOLOGY_NODE_MINSIZE)).thenReturn("1");
        when(getValue(DAEMON_WATCH_CLUSTER_HEARTBEAT_TIMEOUT_MS)).thenReturn("10000000");
        when(getValue(CLUSTER_ID)).thenReturn("cluster-1");
        when(getValue(INST_ID)).thenReturn("mock-inst");
        when(getValue(DAEMON_PORT)).thenReturn("3007");
        when(getValue(CPU_CORES)).thenReturn("32");
        when(getValue(MEM_SIZE)).thenReturn("2048");
        when(getValue(POLARX_INST_ID)).thenReturn("mock-inst");
        when(getValue(CLUSTER_TOPOLOGY_EXCLUDE_NODES)).thenReturn("[]");

        mockNodeInfo();
        mockStorageInfo();

        when(RuntimeLeaderElector.isDaemonLeader()).thenReturn(true);
        when(ServerConfigUtil.getGlobalNumberVarDirect(anyString())).thenReturn(1L);

        TopologyWatcher topologyWatcher =
            new TopologyWatcher(getString(CLUSTER_ID), ClusterType.BINLOG.name(), "TopologyWatcher",
                getInt(DAEMON_WATCH_CLUSTER_INTERVAL_MS));
        BinlogTaskConfigMapper binlogTaskConfigMapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        Assert.assertEquals(0, binlogTaskConfigMapper.select(s -> s).size());
        topologyWatcher.exec();
        DaemonBootStrap.waitForTopologyReady();
        Assert.assertEquals(2, binlogTaskConfigMapper.select(s -> s).size());
    }
}
