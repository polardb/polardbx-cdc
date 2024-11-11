/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.NodeRole;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.enums.ClusterRole;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.testing.BaseTest;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-08-21 14:27
 **/
public class TableDataInitUtil {

    public static void initNodeInfo(BaseTest.ConfigProvider configProvider) {
        configProvider.setValue(ConfigKeys.CLUSTER_ID, "cluster_id_test");
        configProvider.setValue(ConfigKeys.DAEMON_PORT, "3006");
        configProvider.setValue(ConfigKeys.CPU_CORES, "16");
        configProvider.setValue(ConfigKeys.MEM_SIZE, "32000");

        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);

        NodeInfo nodeInfo1 = new NodeInfo();
        nodeInfo1.setRole(NodeRole.MASTER.getName());
        nodeInfo1.setClusterId("cluster_id_test");
        nodeInfo1.setClusterType(ClusterType.BINLOG.name());
        nodeInfo1.setContainerId("1001");
        nodeInfo1.setIp(DynamicApplicationConfig.getString(ConfigKeys.INST_IP));
        nodeInfo1.setDaemonPort(DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT));
        nodeInfo1.setAvailablePorts("3038,3040,3039,3043,3042,3041");
        nodeInfo1.setCore(DynamicApplicationConfig.getLong(ConfigKeys.CPU_CORES));
        nodeInfo1.setMem(DynamicApplicationConfig.getLong(ConfigKeys.MEM_SIZE));
        nodeInfo1.setStatus(0);
        nodeInfo1.setPolarxInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
        nodeInfo1.setClusterRole(ClusterRole.master.name());
        nodeInfo1.setGroupName(CommonConstants.GROUP_NAME_GLOBAL);
        nodeInfoMapper.insertSelective(nodeInfo1);

        NodeInfo nodeInfo2 = new NodeInfo();
        nodeInfo2.setRole(NodeRole.MASTER.getName());
        nodeInfo2.setClusterId("cluster_id_test");
        nodeInfo2.setClusterType(ClusterType.BINLOG.name());
        nodeInfo2.setContainerId("1002");
        nodeInfo2.setIp(DynamicApplicationConfig.getString(ConfigKeys.INST_IP));
        nodeInfo2.setDaemonPort(DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT));
        nodeInfo2.setAvailablePorts("3038,3040,3039,3043,3042,3041");
        nodeInfo2.setCore(DynamicApplicationConfig.getLong(ConfigKeys.CPU_CORES));
        nodeInfo2.setMem(DynamicApplicationConfig.getLong(ConfigKeys.MEM_SIZE));
        nodeInfo2.setStatus(0);
        nodeInfo2.setPolarxInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
        nodeInfo2.setClusterRole(ClusterRole.master.name());
        nodeInfo2.setGroupName(CommonConstants.GROUP_NAME_GLOBAL);
        nodeInfoMapper.insertSelective(nodeInfo2);
    }
}
