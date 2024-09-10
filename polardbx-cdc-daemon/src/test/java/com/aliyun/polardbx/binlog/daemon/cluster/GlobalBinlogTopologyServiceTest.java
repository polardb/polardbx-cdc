/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.schedule.TopologyWatcher;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.enums.ClusterRole;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SUSPEND_TOPOLOGY_REBUILDING;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

public class GlobalBinlogTopologyServiceTest extends BaseTestWithGmsTables {
    static MockedStatic<RuntimeLeaderElector> runtimeLeaderElector;
    static MockedStatic<ServerConfigUtil> serverConfigUtil;

    private StorageInfo storageInfo1 = new StorageInfo();
    private StorageInfo storageInfo2 = new StorageInfo();

    private NodeInfo nodeInfo1 = new NodeInfo();
    private NodeInfo nodeInfo2 = new NodeInfo();

    private void mockNodeInfo() {
        NodeInfo nodeInfo = nodeInfo1;
        nodeInfo.setRole("master");
        nodeInfo.setClusterId(getString(ConfigKeys.CLUSTER_ID));
        nodeInfo.setClusterType("BINLOG");
        nodeInfo.setContainerId("1001");
        nodeInfo.setIp("127.0.0.1");
        nodeInfo.setDaemonPort(DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT));
        nodeInfo.setAvailablePorts("3006,3007,3008,3009");
        nodeInfo.setCore(DynamicApplicationConfig.getLong(ConfigKeys.CPU_CORES));
        nodeInfo.setMem(DynamicApplicationConfig.getLong(ConfigKeys.MEM_SIZE));
        nodeInfo.setStatus(0);
        nodeInfo.setPolarxInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
        nodeInfo.setClusterRole(ClusterRole.master.name());
        nodeInfo.setGroupName(CommonConstants.GROUP_NAME_GLOBAL);

        NodeInfoMapper binlogNodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        binlogNodeInfoMapper.insertSelective(nodeInfo);

        nodeInfo = nodeInfo2;
        nodeInfo.setRole("master");
        nodeInfo.setClusterId(getString(ConfigKeys.CLUSTER_ID));
        nodeInfo.setClusterType("BINLOG");
        nodeInfo.setContainerId("1002");
        nodeInfo.setIp("127.0.0.2");
        nodeInfo.setDaemonPort(DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT));
        nodeInfo.setAvailablePorts("3006,3007,3008,3009");
        nodeInfo.setCore(DynamicApplicationConfig.getLong(ConfigKeys.CPU_CORES));
        nodeInfo.setMem(DynamicApplicationConfig.getLong(ConfigKeys.MEM_SIZE));
        nodeInfo.setStatus(0);
        nodeInfo.setPolarxInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
        nodeInfo.setClusterRole(ClusterRole.master.name());
        nodeInfo.setGroupName(CommonConstants.GROUP_NAME_GLOBAL);

        binlogNodeInfoMapper.insertSelective(nodeInfo);
    }

    private void mockStorageInfo() {
        StorageInfoMapper mapper = SpringContextHolder.getObject(StorageInfoMapper.class);

        storageInfo1.setInstId("mock-instId");
        storageInfo1.setInstKind(0);
        storageInfo1.setStatus(1);
        storageInfo1.setIsVip(0);
        storageInfo1.setMaxConn(11);
        storageInfo1.setStorageType(0);
        storageInfo1.setPasswdEnc("mmm");
        storageInfo1.setUser("mmm");
        storageInfo1.setPort(3306);
        storageInfo1.setIp("127.0.0.1");
        storageInfo1.setStorageMasterInstId("mock-instId");
        storageInfo1.setStorageInstId("mock-instId");
        mapper.insertSelective(storageInfo1);

        storageInfo2.setInstId("mock-instId");
        storageInfo2.setInstKind(0);
        storageInfo2.setStatus(1);
        storageInfo2.setIsVip(0);
        storageInfo2.setMaxConn(11);
        storageInfo2.setStorageType(0);
        storageInfo2.setPasswdEnc("mmm");
        storageInfo2.setUser("mmm");
        storageInfo2.setPort(3306);
        storageInfo2.setIp("127.0.0.2");
        storageInfo2.setStorageMasterInstId("mock-instId2");
        storageInfo2.setStorageInstId("mock-instId2");
        mapper.insertSelective(storageInfo2);
    }

    @Test
    public void testBuildTopology() throws Throwable {
        setConfig(ConfigKeys.TOPOLOGY_NODE_MINSIZE, "1");
        setConfig(ConfigKeys.DAEMON_WATCH_CLUSTER_HEARTBEAT_TIMEOUT_MS, "10000000");
        setConfig(ConfigKeys.CLUSTER_ID, "cluster-1");
        setConfig(ConfigKeys.INST_ID, "mock-inst");
        setConfig(ConfigKeys.DAEMON_PORT, "3007");
        setConfig(ConfigKeys.CPU_CORES, "32");
        setConfig(ConfigKeys.MEM_SIZE, "2048");
        setConfig(ConfigKeys.POLARX_INST_ID, "mock-inst");

        mockNodeInfo();
        mockStorageInfo();

        runtimeLeaderElector = Mockito.mockStatic(RuntimeLeaderElector.class);
        Mockito.when(RuntimeLeaderElector.isDaemonLeader()).thenReturn(true);

        serverConfigUtil = Mockito.mockStatic(ServerConfigUtil.class);
        Mockito.when(ServerConfigUtil.getGlobalNumberVarDirect(Mockito.anyString())).thenReturn(1L);

        TopologyWatcher topologyWatcher =
            new TopologyWatcher(getString(ConfigKeys.CLUSTER_ID), ClusterType.BINLOG.name(), "watcher-test", 10);
        // suspendTopologyRebuilding test
        setConfig(CLUSTER_SUSPEND_TOPOLOGY_REBUILDING, "true");
        topologyWatcher.exec();
        // suspend rebuild , will not build topology
        BinlogTaskConfigMapper binlogTaskConfigMapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        List<BinlogTaskConfig> taskConfigs = binlogTaskConfigMapper.select(s -> s);
        Assert.assertEquals(0, taskConfigs.size());

        setConfig(CLUSTER_SUSPEND_TOPOLOGY_REBUILDING, "false");
        topologyWatcher.exec();

        taskConfigs = binlogTaskConfigMapper.select(s -> s);
        // 2 dumper 1 task
        Assert.assertEquals(3, taskConfigs.size());

        StorageHistoryInfoMapper storageHistoryInfoMapper =
            SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
        List<StorageHistoryInfo> storageHistoryInfo = storageHistoryInfoMapper.select(s -> s);
        Assert.assertEquals(1, storageHistoryInfo.size());
        StorageHistoryInfo historyInfo = storageHistoryInfo.get(0);
        Assert.assertEquals(0, historyInfo.getStatus().intValue());
        Assert.assertEquals(ExecutionConfig.ORIGIN_TSO, historyInfo.getTso());
        Assert.assertEquals(getString(CLUSTER_ID), historyInfo.getClusterId());
        Assert.assertEquals(CommonConstants.GROUP_NAME_GLOBAL, historyInfo.getGroupName());

        String clusterSnapshotVersion = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
        ClusterSnapshot snapshot = JSON.parseObject(clusterSnapshotVersion, ClusterSnapshot.class);
        Assert.assertEquals(2, snapshot.getVersion());
    }
}
