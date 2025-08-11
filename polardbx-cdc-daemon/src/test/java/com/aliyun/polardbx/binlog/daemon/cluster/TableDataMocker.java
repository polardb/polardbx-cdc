/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.NodeRole;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.enums.ClusterRole;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.google.common.collect.Lists;

import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.CPU_CORES;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_PORT;
import static com.aliyun.polardbx.binlog.ConfigKeys.MEM_SIZE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getValue;
import static org.mockito.Mockito.when;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-08-21 14:27
 **/
public class TableDataMocker {

    public static List<NodeInfo> mockNodeInfoList() {
        when(getValue(CLUSTER_ID)).thenReturn("cluster_id_test");
        when(getValue(DAEMON_PORT)).thenReturn("3006");
        when(getValue(CPU_CORES)).thenReturn("16");
        when(getValue(MEM_SIZE)).thenReturn("32000");

        NodeInfo nodeInfo1 = new NodeInfo();
        nodeInfo1.setRole(NodeRole.MASTER.getName());
        nodeInfo1.setClusterId("cluster_id_test");
        nodeInfo1.setClusterType(ClusterType.BINLOG.name());
        nodeInfo1.setContainerId("1001");
        nodeInfo1.setIp(DynamicApplicationConfig.getString(ConfigKeys.INST_IP));
        nodeInfo1.setDaemonPort(DynamicApplicationConfig.getInt(DAEMON_PORT));
        nodeInfo1.setAvailablePorts("3038,3040,3039,3043,3042,3041");
        nodeInfo1.setCore(DynamicApplicationConfig.getLong(CPU_CORES));
        nodeInfo1.setMem(DynamicApplicationConfig.getLong(MEM_SIZE));
        nodeInfo1.setStatus(0);
        nodeInfo1.setPolarxInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
        nodeInfo1.setClusterRole(ClusterRole.master.name());
        nodeInfo1.setGroupName(CommonConstants.GROUP_NAME_GLOBAL);

        NodeInfo nodeInfo2 = new NodeInfo();
        nodeInfo2.setRole(NodeRole.MASTER.getName());
        nodeInfo2.setClusterId("cluster_id_test");
        nodeInfo2.setClusterType(ClusterType.BINLOG.name());
        nodeInfo2.setContainerId("1002");
        nodeInfo2.setIp(DynamicApplicationConfig.getString(ConfigKeys.INST_IP));
        nodeInfo2.setDaemonPort(DynamicApplicationConfig.getInt(DAEMON_PORT));
        nodeInfo2.setAvailablePorts("3038,3040,3039,3043,3042,3041");
        nodeInfo2.setCore(DynamicApplicationConfig.getLong(CPU_CORES));
        nodeInfo2.setMem(DynamicApplicationConfig.getLong(MEM_SIZE));
        nodeInfo2.setStatus(0);
        nodeInfo2.setPolarxInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
        nodeInfo2.setClusterRole(ClusterRole.master.name());
        nodeInfo2.setGroupName(CommonConstants.GROUP_NAME_GLOBAL);

        return Lists.newArrayList(nodeInfo1, nodeInfo2);
    }
}
