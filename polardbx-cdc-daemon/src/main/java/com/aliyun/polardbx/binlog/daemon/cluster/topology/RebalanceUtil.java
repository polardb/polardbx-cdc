/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_ENABLE_LIGHT_REBALANCE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class RebalanceUtil {

    public static long buildTaskNameIdentifier(String containerId, String ip) {
        return NumberUtils.isCreatable(containerId) ?
            Long.parseLong(containerId) : (containerId + ip).hashCode();
    }

    public static boolean isLightRebalance(String clusterId, String preDumperMasterNode, String postDumperMasterNode) {
        // 前后拓扑的dumperMaster节点不同，则进行full rebalance
        boolean enableLightRebalance = getBoolean(TOPOLOGY_ENABLE_LIGHT_REBALANCE);
        boolean allResourceEnable = allResourceEnableLightRebalance(clusterId);
        return enableLightRebalance && allResourceEnable &&
            StringUtils.equals(preDumperMasterNode, postDumperMasterNode);
    }

    public static boolean isLightRebalance(String clusterId) {
        boolean enableSwitch = getBoolean(TOPOLOGY_ENABLE_LIGHT_REBALANCE);
        boolean allResourceEnable = allResourceEnableLightRebalance(clusterId);
        return enableSwitch && allResourceEnable;
    }

    private static boolean allResourceEnableLightRebalance(String clusterId) {
        return allNodeEnableLightRebalance(clusterId)
            && allDumperEnableLightRebalance(clusterId)
            && allTaskEnableLightRebalance(clusterId);
    }

    private static boolean allNodeEnableLightRebalance(String clusterId) {
        NodeInfoMapper nodeInfoMapper = getObject(NodeInfoMapper.class);
        return nodeInfoMapper.select(
                s -> s.where(NodeInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId))).stream()
            .allMatch(nodeInfo -> nodeInfo.getEnableLightRebalance().equals(true));
    }

    private static boolean allDumperEnableLightRebalance(String clusterId) {
        DumperInfoMapper dumperInfoMapper = getObject(DumperInfoMapper.class);
        return dumperInfoMapper.select(
                s -> s.where(DumperInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId))).stream()
            .allMatch(dumperInfo -> dumperInfo.getEnableLightRebalance().equals(true));
    }

    private static boolean allTaskEnableLightRebalance(String clusterId) {
        BinlogTaskInfoMapper taskInfoMapper = getObject(BinlogTaskInfoMapper.class);
        return taskInfoMapper.select(
                s -> s.where(BinlogTaskInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId))).stream()
            .allMatch(taskInfo -> taskInfo.getEnableLightRebalance().equals(true));
    }
}
