/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.Resource;
import com.aliyun.polardbx.binlog.service.NodeInfoService;
import com.aliyun.polardbx.binlog.util.GmsTimeUtil;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by ShuGuang
 */
@Slf4j
public class ResourceManager {

    private final String clusterId;
    private final BinlogTaskInfoMapper taskInfoMapper;
    private final DumperInfoMapper dumperInfoMapper;
    private final BinlogTaskConfigMapper taskConfigMapper;
    private final NodeInfoService nodeInfoService;

    public ResourceManager(String clusterId) {
        this.clusterId = clusterId;
        this.taskInfoMapper = SpringContextHolder.getObject(BinlogTaskInfoMapper.class);
        this.dumperInfoMapper = SpringContextHolder.getObject(DumperInfoMapper.class);
        this.taskConfigMapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        this.nodeInfoService = SpringContextHolder.getObject(NodeInfoService.class);
    }

    /**
     * 所有可参与拓扑计算的容器的列表
     */
    public Set<String> allContainers() {
        return nodeInfoService.getAllNodes(clusterId)
            .stream().map(NodeInfo::getContainerId).collect(Collectors.toSet());
    }

    /**
     * 不在线的容器列表
     */
    public Set<String> allOfflineContainers() {
        return nodeInfoService.getDeadNodes(clusterId)
            .stream().map(NodeInfo::getContainerId)
            .collect(Collectors.toSet());
    }

    /**
     * 在线的容器列表
     */
    public Set<String> allOnlineContainers() {
        return nodeInfoService.getAliveNodes(clusterId)
            .stream().map(NodeInfo::getContainerId).collect(Collectors.toSet());
    }

    public boolean isAllContainerExist(Set<String> containers) {
        return allContainers().containsAll(containers);
    }

    /**
     * 所有可参与拓扑计算的、且可用的容器列表
     * 集群资源容量（cpu进一步虚拟化，当前机器的cpu*4）
     */
    public List<Container> availableContainers() {
        List<NodeInfo> nodeInfoList = nodeInfoService.getAliveNodes(clusterId);
        List<Container> result = Lists.newArrayListWithCapacity(nodeInfoList.size());
        for (final NodeInfo nodeInfo : nodeInfoList) {
            List<Integer> portList = Lists.newArrayList(nodeInfo.getAvailablePorts().split(",")).stream().map(
                Integer::valueOf).collect(Collectors.toList());
            LinkedList<Integer> ports = Lists.newLinkedList(portList);

            Container container = Container.builder().containerId(nodeInfo.getContainerId())
                .ip(nodeInfo.getIp()).daemonPort(nodeInfo.getDaemonPort()).availablePorts(ports)
                .capability(Resource.builder().cpu(nodeInfo.getCore().intValue())
                    .memory_mb(nodeInfo.getMem().intValue()).build())
                .hostString(nodeInfo.getIp() + ":" + nodeInfo.getDaemonPort())
                .build();
            result.add(container);
        }
        return result;
    }

    public ExecutionSnapshot getExecutionSnapshot() {
        ExecutionSnapshot snapshot = new ExecutionSnapshot();

        List<BinlogTaskConfig> allConfig = taskConfigMapper
            .select(s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));

        boolean isOk = true;
        for (BinlogTaskConfig config : allConfig) {
            if (TaskType.isTask(config.getRole())) {
                BinlogTaskInfo taskInfo = getTaskInfo(config.getTaskName());
                boolean valid = checkValid(config.getGmtModified().getTime(),
                    taskInfo == null ? -1L : taskInfo.getGmtHeartbeat().getTime(), taskInfo == null);
                isOk &= valid;

                snapshot.getProcessMeta().put(config.getTaskName(), new ExecutionSnapshot.ProcessMeta(
                    valid ? ExecutionSnapshot.Status.OK : ExecutionSnapshot.Status.DOWN,
                    config.getContainerId(),
                    taskInfo == null ?
                        String.format("The task [%s] was not started at the specified time.", config.getTaskName()) :
                        String.format("The task [%s] heartbeat is timeout.", config.getTaskName())));
            } else if (TaskType.isDumper(config.getRole())) {
                DumperInfo dumperInfo = getDumperInfo(config.getTaskName());
                boolean valid = checkValid(config.getGmtModified().getTime(),
                    dumperInfo == null ? -1L : dumperInfo.getGmtHeartbeat().getTime(), dumperInfo == null);
                isOk &= valid;

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
        snapshot.setAllRunningOk(isOk);
        return snapshot;
    }

    public DumperInfo getDumperInfo(String name) {
        List<DumperInfo> list = dumperInfoMapper
            .select(s -> s.where(DumperInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(name)));
        return list.isEmpty() ? null : list.get(0);
    }

    public BinlogTaskInfo getTaskInfo(String name) {
        List<BinlogTaskInfo> list = taskInfoMapper.select(
            s -> s.where(BinlogTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(BinlogTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(name)));
        return list.isEmpty() ? null : list.get(0);
    }

    public boolean isHeartbeatTimeOut(long timeMills) {
        return GmsTimeUtil.getCurrentTimeMillis() - timeMills > DynamicApplicationConfig
            .getInt(ConfigKeys.DAEMON_WATCH_CLUSTER_HEARTBEAT_TIMEOUT_MS);
    }

    public boolean isStartTimeOut(long timeMills) {
        return GmsTimeUtil.getCurrentTimeMillis() - timeMills > DynamicApplicationConfig
            .getInt(ConfigKeys.DAEMON_WATCH_CLUSTER_WAIT_START_TIMEOUT_MS);
    }

    private boolean checkValid(long configTimeMills, long heartbeatTimeMills, boolean isNull) {
        if (isNull) {
            return !isStartTimeOut(configTimeMills);
        } else {
            return !isHeartbeatTimeOut(heartbeatTimeMills);
        }
    }
}
