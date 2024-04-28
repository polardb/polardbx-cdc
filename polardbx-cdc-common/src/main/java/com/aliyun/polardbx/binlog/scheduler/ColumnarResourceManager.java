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
package com.aliyun.polardbx.binlog.scheduler;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ColumnarNodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ColumnarNodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.ColumnarNodeInfo;
import com.aliyun.polardbx.binlog.domain.po.ColumnarTask;
import com.aliyun.polardbx.binlog.domain.po.ColumnarTaskConfig;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.Resource;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TOPOLOGY_EXCLUDE_NODES_KEY;

/**
 * @author wenki
 */
@Slf4j
public class ColumnarResourceManager {
    private final String clusterId;
    private final ColumnarNodeInfoMapper columnarNodeInfoMapper;
    private final ColumnarTaskConfigMapper columnarTaskConfigMapper;
    private final ColumnarTaskMapper columnarTaskMapper;
    private Set<String> topologyExcludeNodes;

    public ColumnarResourceManager(String clusterId) {
        this.clusterId = clusterId;
        this.columnarNodeInfoMapper = SpringContextHolder.getObject(ColumnarNodeInfoMapper.class);
        this.columnarTaskConfigMapper = SpringContextHolder.getObject(ColumnarTaskConfigMapper.class);
        this.columnarTaskMapper = SpringContextHolder.getObject(ColumnarTaskMapper.class);
    }

    public List<Container> availableContainers() {
        List<ColumnarNodeInfo> nodeInfoList = getAliveNodes();
        List<Container> result = Lists.newArrayListWithCapacity(nodeInfoList.size());
        for (final ColumnarNodeInfo nodeInfo : nodeInfoList) {
            List<Integer> portList = Lists.newArrayList(nodeInfo.getAvailablePorts().split(",")).stream().map(
                Integer::valueOf).collect(Collectors.toList());
            LinkedList<Integer> ports = Lists.newLinkedList(portList);

            Container container = Container.builder().containerId(nodeInfo.getContainerId())
                .ip(nodeInfo.getIp()).daemonPort(nodeInfo.getDaemonPort()).availablePorts(ports)
                .capability(Resource.builder().cpu(nodeInfo.getCore().intValue())
                    .memory_mb(nodeInfo.getMem().intValue()).build()).build();
            result.add(container);
        }
        return result;
    }

    /**
     * 所有可参与拓扑计算的容器的列表
     */
    public Set<String> allContainers() {
        return columnarNodeInfoMapper.select(s ->
                s.where(ColumnarNodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                    .and(ColumnarNodeInfoDynamicSqlSupport.status, SqlBuilder.isEqualTo(0)))
            .stream().map(ColumnarNodeInfo::getContainerId).collect(Collectors.toSet());
    }

    /**
     * 不在线的容器列表
     */
    public Set<String> allOfflineContainers() {
        return getDeadNodes().stream().map(ColumnarNodeInfo::getContainerId).collect(Collectors.toSet());
    }

    /**
     * 在线的容器列表
     */
    public Set<String> allOnlineContainers() {
        return getAliveNodes().stream().map(ColumnarNodeInfo::getContainerId).collect(Collectors.toSet());
    }

    public boolean isAllContainerExist(Set<String> containers) {
        return allContainers().containsAll(containers);
    }

    private List<ColumnarNodeInfo> getAliveNodes() {
        int timeoutThresholdMs = DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_WATCH_CLUSTER_HEARTBEAT_TIMEOUT_MS);
        Set<String> excludeNodes = getTopologyExcludeNodes();
        List<ColumnarNodeInfo> aliveNodes = columnarNodeInfoMapper.getAliveNodes(clusterId, timeoutThresholdMs);
        return aliveNodes.stream().filter(r -> !excludeNodes.contains(r.getContainerId())).collect(Collectors.toList());
    }

    private List<ColumnarNodeInfo> getDeadNodes() {
        int timeoutThresholdMs = DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_WATCH_CLUSTER_HEARTBEAT_TIMEOUT_MS);
        Set<String> excludeNodes = getTopologyExcludeNodes();
        List<ColumnarNodeInfo> deadNodes = columnarNodeInfoMapper.getDeadNodes(clusterId, timeoutThresholdMs);
        return deadNodes.stream().filter(r -> !excludeNodes.contains(r.getContainerId())).collect(Collectors.toList());
    }

    public ExecutionSnapshot getExecutionSnapshot() {
        ExecutionSnapshot snapshot = new ExecutionSnapshot();

        List<ColumnarTaskConfig> allConfig = columnarTaskConfigMapper
            .select(s -> s.where(ColumnarTaskConfigDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));

        boolean isOk = true;
        for (ColumnarTaskConfig config : allConfig) {
            ColumnarTask columnartask = getColumnarTask(config.getTaskName());
            boolean valid = checkValid(config.getGmtModified().getTime(),
                columnartask == null ? -1L : columnartask.getGmtHeartbeat().getTime(), columnartask == null);
            isOk &= valid;

            snapshot.getProcessMeta().put(config.getTaskName(), new ExecutionSnapshot.ProcessMeta(
                valid ? ExecutionSnapshot.Status.OK : ExecutionSnapshot.Status.DOWN,
                config.getContainerId(),
                columnartask == null ?
                    String.format("The columnar [%s] was not started at the specified time.", config.getTaskName()) :
                    String.format("The columnar [%s] heartbeat is timeout.", config.getTaskName())
            ));
        }
        snapshot.setAllRunningOk(isOk);
        return snapshot;
    }

    public ColumnarTask getColumnarTask(String name) {
        List<ColumnarTask> list = columnarTaskMapper.select(
            s -> s.where(BinlogTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(BinlogTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(name)));
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean isHeartbeatTimeOut(long timeMills) {
        return System.currentTimeMillis() - timeMills > DynamicApplicationConfig
            .getInt(ConfigKeys.DAEMON_WATCH_CLUSTER_HEARTBEAT_TIMEOUT_MS);
    }

    public boolean isStartTimeOut(long timeMills) {
        return System.currentTimeMillis() - timeMills > DynamicApplicationConfig
            .getInt(ConfigKeys.DAEMON_WATCH_CLUSTER_WAIT_START_TIMEOUT_MS);
    }

    private boolean checkValid(long configTimeMills, long heartbeatTimeMills, boolean isNull) {
        if (isNull) {
            return !isStartTimeOut(configTimeMills);
        } else {
            return !isHeartbeatTimeOut(heartbeatTimeMills);
        }
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
