/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.dao.BinlogScheduleHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryDetailInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.domain.StorageContent;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogScheduleHistory;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryDetailInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ExecutionSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.ScheduleHistoryContent;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.service.XStreamService;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SUSPEND_TOPOLOGY_REBUILDING;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.RebalanceUtil.isLightRebalance;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildExpectedStorageTso4BinlogX;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildStorageHistoryInfo;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildStorageInfos;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.checkContainerStatus;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.clearStaleMetaData;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.lockAndCheck;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.shouldRefreshTopology;
import static com.aliyun.polardbx.binlog.util.ServerConfigUtil.SERVER_ID;
import static com.aliyun.polardbx.binlog.util.SystemDbConfig.updateSystemDbConfig;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotIn;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BinlogXTopologyService implements TopologyService {

    private final String clusterId;
    private final String clusterType;
    private final BinlogXTopologyBuilder topologyBuilder;
    private final ResourceManager resourceManager;

    private final TransactionTemplate transactionTemplate =
        getObject("metaTransactionTemplate");
    private final BinlogTaskConfigMapper taskConfigMapper =
        getObject(BinlogTaskConfigMapper.class);
    private final DumperInfoMapper dumperInfoMapper =
        getObject(DumperInfoMapper.class);
    private final StorageHistoryInfoMapper storageHistoryMapper =
        getObject(StorageHistoryInfoMapper.class);
    private final StorageHistoryDetailInfoMapper storageHistDetailInfoMapper =
        getObject(StorageHistoryDetailInfoMapper.class);
    private final BinlogScheduleHistoryMapper scheduleHistoryMapper =
        getObject(BinlogScheduleHistoryMapper.class);
    private final BinlogTaskInfoMapper taskInfoMapper =
        getObject(BinlogTaskInfoMapper.class);
    private final NodeInfoMapper nodeInfoMapper =
        getObject(NodeInfoMapper.class);

    public BinlogXTopologyService(String clusterId,
                                  String clusterType,
                                  BinlogXTopologyBuilder topologyBuilder,
                                  ResourceManager resourceManager) {
        this.clusterId = clusterId;
        this.clusterType = clusterType;
        this.topologyBuilder = topologyBuilder;
        this.resourceManager = resourceManager;
    }

    @Override
    public void tryBuild() {
        String suspendTopologyRebuilding = SystemDbConfig.getSystemDbConfig(CLUSTER_SUSPEND_TOPOLOGY_REBUILDING);
        if (StringUtils.isNotBlank(suspendTopologyRebuilding) && CommonConstants.TRUE.equals(
            suspendTopologyRebuilding)) {
            log.info("current cluster is in suspend state , skip rebuilding cluster topology");
            return;
        }

        refreshTopology();
    }

    void refreshTopology() {
        log.info("current daemon is leader, do with the cluster's topology project!");
        checkContainerStatus(resourceManager);
        String preClusterConfigStr = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
        ClusterSnapshot preClusterSnapshot = JSONObject.parseObject(preClusterConfigStr, ClusterSnapshot.class);
        clearStaleMetaData(preClusterSnapshot.getVersion());
        String expectedStorageTso = buildExpectedStorageTso4BinlogX();
        ExecutionSnapshot executionSnapshot = resourceManager.getExecutionSnapshot();
        StorageHistoryInfo storageHistoryInfo = buildStorageHistoryInfo(expectedStorageTso);
        List<StorageInfo> storageInfos = buildStorageInfos(storageHistoryInfo);

        TopologyServiceHelper.CheckResult checkResult = shouldRefreshTopology(resourceManager, preClusterSnapshot,
            storageInfos, executionSnapshot, storageHistoryInfo);
        if (checkResult.needRebalance) {
            List<Container> containersBeforeRandomRemove = resourceManager.availableContainers();
            List<Container> containersAfterRandomRemove = resourceManager.tryRandomRemoveContainer(
                containersBeforeRandomRemove, checkResult.forceIntervalRebalance);
            long serverId = ServerConfigUtil.getGlobalNumberVarDirect(SERVER_ID);

            boolean isLightRebalance = !checkResult.fullRebalance && isLightRebalance(clusterId);
            long newVersion = isLightRebalance ? preClusterSnapshot.getVersion() : preClusterSnapshot.getVersion() + 1;
            long newSubVersion = isLightRebalance ? preClusterSnapshot.getSubVersion() + 1 : 1;

            TopologyEntity topology = topologyBuilder.buildTopology(containersAfterRandomRemove,
                storageInfos,
                preClusterSnapshot,
                buildExpectedStorageTso4BinlogX(),
                newVersion,
                newSubVersion,
                serverId,
                storageHistoryInfo == null ? "" : storageHistoryInfo.getInstructionId());

            ClusterSnapshot postClusterSnapshot = new ClusterSnapshot(newVersion,
                System.currentTimeMillis(),
                containersAfterRandomRemove.stream().map(Container::getContainerId).collect(Collectors.toSet()),
                storageInfos.stream().map(StorageInfo::getStorageInstId).collect(Collectors.toSet()),
                "",
                "",
                storageHistoryInfo == null ? ExecutionConfig.ORIGIN_TSO : storageHistoryInfo.getTso(),
                clusterType,
                topology.getServerId(),
                topology.getStreamStorageMap(),
                topology.getContainerStreamMap(),
                newSubVersion
            );
            postClusterSnapshot.setContainersBeforeRandomRemove(containersBeforeRandomRemove.stream()
                .map(Container::getContainerId).collect(Collectors.toSet()));

            persist(topology.getTaskConfigs(), storageInfos, preClusterSnapshot, postClusterSnapshot,
                storageHistoryInfo, executionSnapshot, isLightRebalance);
        }
    }

    void persist(List<BinlogTaskConfig> taskConfigs,
                 List<StorageInfo> storageInfos,
                 ClusterSnapshot preClusterSnapshot,
                 ClusterSnapshot postClusterSnapshot,
                 StorageHistoryInfo storageHistoryInfo,
                 ExecutionSnapshot executionSnapshot,
                 boolean lightRebalance) {
        // 持久化之前再次进行一下验证，如果已经不是Leader，则放弃持久化
        if (!RuntimeLeaderElector.isDaemonLeader()) {
            log.info("current daemon is not a leader, skip the topology persisting.!");
            return;
        }

        transactionTemplate.execute((o) -> {
            if (!lockAndCheck(preClusterSnapshot)) {
                return null;
            }

            updateBinlogTaskConfig(clusterId, taskConfigs);
            cleanStaleRunningInfo(taskConfigs, lightRebalance);
            initStorageHistory(storageHistoryInfo, storageInfos, clusterId);
            updateSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY, JSONObject.toJSONString(postClusterSnapshot));
            deleteNonAvailableNode(clusterId, postClusterSnapshot);
            recordScheduleHistory(executionSnapshot, postClusterSnapshot, taskConfigs);
            return null;
        });
    }

    void updateBinlogTaskConfig(final String clusterId, final List<BinlogTaskConfig> taskConfigList) {
        //执行拓扑保存
        for (BinlogTaskConfig taskConfig : taskConfigList) {
            Optional<BinlogTaskConfig> config = taskConfigMapper.selectOne(
                s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId, isEqualTo(clusterId))
                    .and(BinlogTaskConfigDynamicSqlSupport.taskName, isEqualTo(taskConfig.getTaskName())));
            if (config.isPresent()) {
                BinlogTaskConfig origin = config.get();
                taskConfig.setId(origin.getId());
                taskConfig.setStatus(null);
                taskConfigMapper.updateByPrimaryKeySelective(taskConfig);
            } else {
                taskConfigMapper.insert(taskConfig);
            }
        }
        Set<String> allTaskNames = taskConfigList.stream()
            .map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());
        taskConfigMapper.delete(
            s -> s.where(BinlogTaskConfigDynamicSqlSupport.taskName, SqlBuilder.isNotIn(allTaskNames))
                .and(BinlogTaskConfigDynamicSqlSupport.clusterId, isEqualTo(clusterId)));
    }

    void cleanStaleRunningInfo(final List<BinlogTaskConfig> taskConfigList, final boolean lightRebalance) {
        // prepare for different role
        Set<String> dumperxRoleConfigList = taskConfigList.stream()
            .filter(c -> c.getRole().equals(TaskType.DumperX.name()))
            .map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());
        Set<String> dispatcherRoleConfigList = taskConfigList.stream()
            .filter(c -> c.getRole().equals(TaskType.Dispatcher.name()))
            .map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());

        // 删除Dumper_info和Task_info
        if (lightRebalance) {
            dumperInfoMapper.delete(s -> s.where(
                    DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isNotIn(dumperxRoleConfigList))
                .and(DumperInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId)));
            taskInfoMapper.delete(s -> s.where(
                    BinlogTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isNotIn(dispatcherRoleConfigList))
                .and(BinlogTaskInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId)));
        } else {
            dumperInfoMapper.delete(s -> s.where(DumperInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId)));
            taskInfoMapper.delete(s -> s.where(BinlogTaskInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId)));

        }
    }

    void initStorageHistory(StorageHistoryInfo storageHistoryInfo, final List<StorageInfo> storageInfos,
                            final String clusterId) {
        //初始化storageHistory
        if (storageHistoryInfo == null) {
            StorageContent content = new StorageContent();
            content.setStorageInstIds(storageInfos.stream()
                .map(StorageInfo::getStorageInstId).collect(Collectors.toList()));

            StorageHistoryInfo info = new StorageHistoryInfo();
            info.setStatus(0);
            info.setTso(ExecutionConfig.ORIGIN_TSO);
            info.setStorageContent(JSONObject.toJSONString(content));
            info.setInstructionId("-1");
            info.setClusterId(clusterId);
            info.setGroupName(getString(BINLOGX_STREAM_GROUP_NAME));
            storageHistoryMapper.insert(info);

            List<XStream> streams = XStreamService.getXStreamsInCurrentCluster();
            for (XStream stream : streams) {
                StorageHistoryDetailInfo detailInfo = new StorageHistoryDetailInfo();
                detailInfo.setStreamName(stream.getStreamName());
                detailInfo.setInstructionId("-1");
                detailInfo.setTso(ExecutionConfig.ORIGIN_TSO);
                detailInfo.setClusterId(clusterId);
                detailInfo.setStatus(0);
                storageHistDetailInfoMapper.insert(detailInfo);
            }
        }
    }

    void deleteNonAvailableNode(final String clusterId, ClusterSnapshot postClusterSnapshot) {
        Set<String> containers = postClusterSnapshot.getContainers();
        if (!containers.isEmpty()) {
            nodeInfoMapper.delete(s -> s.where(NodeInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId))
                .and(NodeInfoDynamicSqlSupport.containerId, isNotIn(containers)));
        }
    }

    void recordScheduleHistory(ExecutionSnapshot executionSnapshot, ClusterSnapshot clusterSnapshot,
                               List<BinlogTaskConfig> taskConfigList) {
        //记录历史
        ScheduleHistoryContent content = new ScheduleHistoryContent(executionSnapshot,
            taskConfigList, clusterSnapshot);
        BinlogScheduleHistory history = new BinlogScheduleHistory();
        history.setVersion(clusterSnapshot.getVersion());
        history.setSubVersion(clusterSnapshot.getSubVersion());
        history.setClusterId(clusterId);
        history.setContent(JSONObject.toJSONString(content));
        scheduleHistoryMapper.insert(history);
    }
}
