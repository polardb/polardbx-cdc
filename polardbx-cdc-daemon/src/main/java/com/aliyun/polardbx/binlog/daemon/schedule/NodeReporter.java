/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapperExt;
import com.aliyun.polardbx.binlog.domain.NodeRole;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.COMMON_PORTS;

/**
 * Creatd by ziyang.lb
 */
@Slf4j
public class NodeReporter extends AbstractBinlogTimerTask {

    private final String instId;
    private final String portStr;

    public NodeReporter(String cluster, String clusterType, String name, int interval) {
        super(cluster, clusterType, name, interval);
        instId = DynamicApplicationConfig.getString(ConfigKeys.INST_ID);
        portStr = buildPortStr();
    }

    @Override
    public void exec() {
        boolean isLeader = RuntimeLeaderElector.isDaemonLeader();
        String role = isLeader ? NodeRole.MASTER.getName() : NodeRole.SLAVE.getName();
        String clusterRole = DynamicApplicationConfig.getClusterRole();

        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        NodeInfoMapperExt binlogNodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapperExt.class);
        NodeInfo nodeInfo = new NodeInfo();
        Optional<NodeInfo> node = nodeInfoMapper.selectOne(
            s -> s.where(NodeInfoDynamicSqlSupport.containerId,
                SqlBuilder.isEqualTo(instId)));

        if (node.isPresent()) {
            int result = binlogNodeInfoMapper.updateNodeHeartbeat(node.get().getId(),
                role,
                clusterType,
                clusterRole
            );
            if (result == 0) {
                log.warn("node info has removed from meta db.");
            }

            return;
        }

        ClusterType clusterType = ClusterType.valueOf(this.clusterType);
        nodeInfo.setRole(role);
        nodeInfo.setClusterId(clusterId);
        nodeInfo.setClusterType(clusterType.name());
        nodeInfo.setContainerId(DynamicApplicationConfig.getString(ConfigKeys.INST_ID));
        nodeInfo.setIp(DynamicApplicationConfig.getString(ConfigKeys.INST_IP));
        nodeInfo.setDaemonPort(DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT));
        nodeInfo.setAvailablePorts(portStr);
        nodeInfo.setCore(DynamicApplicationConfig.getLong(ConfigKeys.CPU_CORES));
        nodeInfo.setMem(DynamicApplicationConfig.getLong(ConfigKeys.MEM_SIZE));
        nodeInfo.setStatus(0);
        nodeInfo.setPolarxInstId(DynamicApplicationConfig.getString(ConfigKeys.POLARX_INST_ID));
        nodeInfo.setClusterRole(clusterRole);

        switch (clusterType) {
        case BINLOG:
            nodeInfo.setGroupName(CommonConstants.GROUP_NAME_GLOBAL);
            break;
        case BINLOG_X:
            nodeInfo.setGroupName(DynamicApplicationConfig.getString(BINLOGX_STREAM_GROUP_NAME));
            break;
        default:
            break;
        }

        log.info("insert into binlog_node_info: {}", nodeInfo);
        nodeInfoMapper.insertSelective(nodeInfo);
    }

    @SuppressWarnings("all")
    private String buildPortStr() {
        Map<String, String> portsConfig =
            JSONObject.parseObject(DynamicApplicationConfig.getString(COMMON_PORTS), Map.class);
        return Maps.filterKeys(portsConfig, s -> s.startsWith("cdc")).values().stream().collect(
            Collectors.joining(","));
    }
}
