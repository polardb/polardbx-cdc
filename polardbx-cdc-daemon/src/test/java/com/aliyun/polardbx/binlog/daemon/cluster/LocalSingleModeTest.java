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

import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.ConfigKeys;
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
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_CLUSTER_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

public class LocalSingleModeTest extends BaseTestWithGmsTables {

    static MockedStatic<RuntimeLeaderElector> runtimeLeaderElector;
    static MockedStatic<ServerConfigUtil> serverConfigUtil;

    private void mockNodeInfo() {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setRole("master");
        nodeInfo.setClusterId(getString(ConfigKeys.CLUSTER_ID));
        nodeInfo.setClusterType("BINLOG");
        nodeInfo.setContainerId(DynamicApplicationConfig.getString(ConfigKeys.INST_ID));
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
            new TopologyWatcher(getString(ConfigKeys.CLUSTER_ID), ClusterType.BINLOG.name(), "TopologyWatcher",
                getInt(DAEMON_WATCH_CLUSTER_INTERVAL_MS));
        BinlogTaskConfigMapper binlogTaskConfigMapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        Assert.assertEquals(0, binlogTaskConfigMapper.select(s -> s).size());
        topologyWatcher.exec();
        DaemonBootStrap.waitForTopologyReady();
        Assert.assertEquals(2, binlogTaskConfigMapper.select(s -> s).size());

    }
}
