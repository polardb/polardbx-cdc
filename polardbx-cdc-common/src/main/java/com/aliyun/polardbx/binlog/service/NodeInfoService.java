/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.service;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapperExt;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.enums.NodeStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

@Service
@Slf4j
public class NodeInfoService {

    @Resource
    private NodeInfoMapper nodeInfoMapper;
    @Resource
    private NodeInfoMapperExt nodeInfoMapperExt;

    public NodeInfo getOneNode(String clusterId, String instId) {
        List<NodeInfo> nodeInfoList = nodeInfoMapper.select(s ->
            s.where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(NodeInfoDynamicSqlSupport.containerId, SqlBuilder.isEqualTo(instId)));
        return nodeInfoList.isEmpty() ? null : nodeInfoList.get(0);
    }

    public List<NodeInfo> getAllNodes(String clusterId) {
        return nodeInfoMapper.select(s ->
            s.where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(NodeInfoDynamicSqlSupport.status, SqlBuilder.isEqualTo(NodeStatus.AVAILABLE.getValue()))
                .and(NodeInfoDynamicSqlSupport.containerId, SqlBuilder.isNotInWhenPresent(getTopologyExcludeNodes())));
    }

    public List<NodeInfo> getAliveNodes(String clusterId) {
        int timeoutThresholdMs = getInt(ConfigKeys.DAEMON_WATCH_CLUSTER_HEARTBEAT_TIMEOUT_MS);
        String instId = getString(ConfigKeys.INST_ID);
        Set<String> excludeNodes = getTopologyExcludeNodes();
        List<NodeInfo> aliveNodes = nodeInfoMapperExt.getAliveNodes(clusterId, timeoutThresholdMs, instId);
        return aliveNodes.stream().filter(r -> !excludeNodes.contains(r.getContainerId())).collect(Collectors.toList());
    }

    public List<NodeInfo> getDeadNodes(String clusterId) {
        int timeoutThresholdMs = getInt(ConfigKeys.DAEMON_WATCH_CLUSTER_HEARTBEAT_TIMEOUT_MS);
        String instId = getString(ConfigKeys.INST_ID);
        Set<String> excludeNodes = getTopologyExcludeNodes();
        List<NodeInfo> deadNodes = nodeInfoMapperExt.getDeadNodes(clusterId, timeoutThresholdMs, instId);
        return deadNodes.stream().filter(r -> !excludeNodes.contains(r.getContainerId())).collect(Collectors.toList());
    }

    private Set<String> getTopologyExcludeNodes() {
        String config = getString(CLUSTER_TOPOLOGY_EXCLUDE_NODES);
        if (StringUtils.isNotBlank(config)) {
            return new HashSet<>(JSONObject.parseArray(config, String.class));
        } else {
            return new HashSet<>();
        }
    }
}
