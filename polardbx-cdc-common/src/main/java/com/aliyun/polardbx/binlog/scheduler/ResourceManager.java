/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.scheduler;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.domain.po.RelayFinalTaskInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.Resource;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES_KEY;

/**
 * Created by ShuGuang
 */
@Slf4j
public class ResourceManager {

    private final String clusterId;
    private final NodeInfoMapper nodeInfoMapper;
    private final RelayFinalTaskInfoMapper taskInfoMapper;
    private final DumperInfoMapper dumperInfoMapper;
    private final BinlogTaskConfigMapper taskConfigMapper;
    private Set<String> topologyExcludeNodes;

    public ResourceManager(String clusterId) {
        this.clusterId = clusterId;
        this.nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        this.taskInfoMapper = SpringContextHolder.getObject(RelayFinalTaskInfoMapper.class);
        this.dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
        this.taskConfigMapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
    }

    /**
     * 所有可参与拓扑计算的容器的列表
     */
    public Set<String> allContainers() {
        return nodeInfoMapper.select(s ->
            s.where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(NodeInfoDynamicSqlSupport.status, SqlBuilder.isEqualTo(0))
                .and(NodeInfoDynamicSqlSupport.containerId, SqlBuilder.isNotInWhenPresent(getTopologyExcludeNodes())))
            .stream().map(NodeInfo::getContainerId).collect(Collectors.toSet());
    }

    /**
     * 不在线的容器列表
     */
    public Set<String> allOfflineContainers() {
        return getDeadNodes().stream().map(NodeInfo::getContainerId).collect(Collectors.toSet());
    }

    /**
     * 在线的容器列表
     */
    public Set<String> allOnlineContainers() {
        return getAliveNodes().stream().map(NodeInfo::getContainerId).collect(Collectors.toSet());
    }

    public boolean isContainersOk(Set<String> containers) {
        Set<String> alive = getAliveNodes().stream().map(NodeInfo::getContainerId).collect(Collectors.toSet());
        return alive.containsAll(containers);
    }

    /**
     * 所有可参与拓扑计算的、且可用的容器列表
     * 集群资源容量（cpu进一步虚拟化，当前机器的cpu*4）
     */
    public List<Container> availableContainers() {
        List<NodeInfo> nodeInfoList = getAliveNodes();
        List<Container> result = Lists.newArrayListWithCapacity(nodeInfoList.size());
        for (final NodeInfo nodeInfo : nodeInfoList) {
            List<Integer> portList = Lists.newArrayList(nodeInfo.getAvailablePorts().split(",")).stream().map(
                Integer::valueOf).collect(Collectors.toList());
            LinkedList<Integer> ports = Lists.newLinkedList(portList);

            Container container = Container.builder().containerId(nodeInfo.getContainerId())
                .nodeHttpAddress(nodeInfo.getIp()).daemonPort(nodeInfo.getDaemonPort()).availablePorts(ports)
                .capability(
                    Resource.builder().cpu(nodeInfo.getCore().intValue())
                        .memory_mb(nodeInfo.getMem().intValue()).build())
                .build();
            result.add(container);
        }
        return result;
    }

    public ExecutionSnapshot getExecutionSnapshot() {
        ExecutionSnapshot snapshot = new ExecutionSnapshot();

        List<BinlogTaskConfig> allConfig = taskConfigMapper
            .select(s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));

        boolean isOK = true;
        for (BinlogTaskConfig config : allConfig) {
            if (TaskType.Final.name().equals(config.getRole()) || TaskType.Relay.name().equals(config.getRole())) {
                RelayFinalTaskInfo taskInfo = getTaskInfo(config.getTaskName());
                boolean valid = checkValid(config.getGmtModified().getTime(),
                    taskInfo == null ? -1L : taskInfo.getGmtHeartbeat().getTime(), taskInfo == null);
                isOK &= valid;

                snapshot.getProcessMeta().put(config.getTaskName(), new ExecutionSnapshot.ProcessMeta(
                    valid ? ExecutionSnapshot.Status.OK : ExecutionSnapshot.Status.DOWN,
                    config.getContainerId(),
                    taskInfo == null ?
                        String.format("The task [%s] was not started at the specified time.", config.getTaskName()) :
                        String.format("The task [%s] heartbeat is timeout.", config.getTaskName())));
            } else if (TaskType.Dumper.name().equals(config.getRole())) {
                DumperInfo dumperInfo = getDumperInfo(config.getTaskName());
                boolean valid = checkValid(config.getGmtModified().getTime(),
                    dumperInfo == null ? -1L : dumperInfo.getGmtHeartbeat().getTime(), dumperInfo == null);
                isOK &= valid;

                snapshot.getProcessMeta().put(config.getTaskName(), new ExecutionSnapshot.ProcessMeta(
                    valid ? ExecutionSnapshot.Status.OK : ExecutionSnapshot.Status.DOWN,
                    config.getContainerId(),
                    dumperInfo == null ?
                        String.format("The dumper [%s] was not started at the specified time.", config.getTaskName()) :
                        String.format("The dumper [%s] heartbeat is timeout.", config.getTaskName())
                ));
            } else {
                log.warn("unknown task type " + config.getRole());
                throw new PolardbxException("unknown task type " + config.getRole());
            }
        }
        snapshot.setOK(isOK);
        return snapshot;
    }

    public DumperInfo getDumperInfo(String name) {
        List<DumperInfo> list = dumperInfoMapper
            .select(s -> s.where(DumperInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(name)));
        return list.isEmpty() ? null : list.get(0);
    }

    public RelayFinalTaskInfo getTaskInfo(String name) {
        List<RelayFinalTaskInfo> list = taskInfoMapper.select(
            s -> s.where(RelayFinalTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(RelayFinalTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(name)));
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean isHeartbeatTimeOut(long timeMills) {
        return System.currentTimeMillis() - timeMills > DynamicApplicationConfig
            .getInt(ConfigKeys.TOPOLOGY_HEARTBEAT_TIMEOUT_MS);
    }

    public boolean isStartTimeOut(long timeMills) {
        return System.currentTimeMillis() - timeMills > DynamicApplicationConfig
            .getInt(ConfigKeys.TOPOLOGY_START_TIMEOUT_MS);
    }

    private boolean checkValid(long configTimeMills, long heartbeatTimeMills, boolean isNull) {
        if (isNull) {
            return !isStartTimeOut(configTimeMills);
        } else {
            return !isHeartbeatTimeOut(heartbeatTimeMills);
        }
    }

    private List<NodeInfo> getAliveNodes() {
        int timeoutThresholdMs = DynamicApplicationConfig.getInt(ConfigKeys.TOPOLOGY_HEARTBEAT_TIMEOUT_MS);
        return nodeInfoMapper.select(s ->
            s.where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(NodeInfoDynamicSqlSupport.status, SqlBuilder.isEqualTo(0))
                .and(NodeInfoDynamicSqlSupport.containerId, SqlBuilder.isNotInWhenPresent(getTopologyExcludeNodes()))
                .and(NodeInfoDynamicSqlSupport.gmtHeartbeat,
                    SqlBuilder.isGreaterThan(DateTime.now().minusMillis(timeoutThresholdMs).toDate()))
                .orderBy(NodeInfoDynamicSqlSupport.id));
    }

    private List<NodeInfo> getDeadNodes() {
        int timeoutThresholdMs = DynamicApplicationConfig.getInt(ConfigKeys.TOPOLOGY_HEARTBEAT_TIMEOUT_MS);
        return nodeInfoMapper.select(s ->
            s.where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(NodeInfoDynamicSqlSupport.status, SqlBuilder.isEqualTo(0))
                .and(NodeInfoDynamicSqlSupport.containerId, SqlBuilder.isNotInWhenPresent(getTopologyExcludeNodes()))
                .and(NodeInfoDynamicSqlSupport.gmtHeartbeat,
                    SqlBuilder.isLessThanOrEqualTo(DateTime.now().minusMillis(timeoutThresholdMs).toDate()))
                .orderBy(NodeInfoDynamicSqlSupport.id));
    }

    private Set<String> getTopologyExcludeNodes() {
        if (topologyExcludeNodes == null) {
            String config = SystemDbConfig.getSystemDbConfig(CLUSTER_TOPOLOGY_EXCLUDE_NODES_KEY);
            if (StringUtils.isNotBlank(config)) {
                topologyExcludeNodes = new HashSet<>(JSONObject.parseArray(config, String.class));
            } else {
                topologyExcludeNodes = new HashSet<>();
            }
        }
        return topologyExcludeNodes;
    }
}
