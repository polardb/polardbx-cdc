/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.dao.BinlogScheduleHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.domain.StorageContent;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogScheduleHistory;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ExecutionSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.ScheduleHistoryContent;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.alibaba.fastjson.JSON.toJSONString;
import static com.aliyun.polardbx.binlog.CommonConstants.GROUP_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SUSPEND_TOPOLOGY_REBUILDING;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.RebalanceUtil.isLightRebalance;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildExpectedStorageTso;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildStorageHistoryInfo;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildStorageInfos;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.checkContainerStatus;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.clearStaleMetaData;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.lockAndCheck;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.shouldRefreshTopology;
import static com.aliyun.polardbx.binlog.util.ServerConfigUtil.SERVER_ID;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotIn;

/**
 * Created by ShuGuang & ziyang.lb
 */
@Slf4j
public class GlobalBinlogTopologyService implements TopologyService {
    private final GlobalBinlogTopologyBuilder topologyBuilder;
    private final String clusterId;
    private final String clusterType;

    //mappers
    private final BinlogTaskConfigMapper taskConfigMapper = getObject(BinlogTaskConfigMapper.class);

    private final DumperInfoMapper dumperInfoMapper = getObject(DumperInfoMapper.class);

    private final StorageHistoryInfoMapper storageHistoryMapper = getObject(StorageHistoryInfoMapper.class);

    private final BinlogScheduleHistoryMapper scheduleHistoryMapper = getObject(BinlogScheduleHistoryMapper.class);

    private final BinlogTaskInfoMapper taskInfoMapper = getObject(BinlogTaskInfoMapper.class);

    private final TransactionTemplate transactionTemplate = getObject("metaTransactionTemplate");

    private final NodeInfoMapper nodeInfoMapper = getObject(NodeInfoMapper.class);

    public GlobalBinlogTopologyService(String clusterId, String clusterType) {
        this.clusterId = clusterId;
        this.clusterType = clusterType;
        this.topologyBuilder = new GlobalBinlogTopologyBuilder(clusterId);
    }

    protected void initDependencies() {

    }

    @Override
    public void tryBuild() throws Throwable {
        String suspendTopologyRebuilding = SystemDbConfig.getSystemDbConfig(CLUSTER_SUSPEND_TOPOLOGY_REBUILDING);
        if (StringUtils.isNotBlank(suspendTopologyRebuilding) && CommonConstants.TRUE.equals(
            suspendTopologyRebuilding)) {
            log.info("current cluster is in suspend state , skip rebuilding cluster topology");
            return;
        }

        // check and prepare parameter
        log.info("current daemon is leader, do with the cluster's topology project!");
        ResourceManager resourceManager = new ResourceManager(clusterId);
        ensureContainerMinSizeConstrain(resourceManager);
        checkContainerStatus(resourceManager);
        String preClusterSnapshotStr = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
        ClusterSnapshot preClusterSnapshot = JSONObject.parseObject(preClusterSnapshotStr, ClusterSnapshot.class);
        clearStaleMetaData(preClusterSnapshot.getVersion());

        ExecutionSnapshot executionSnapshot = resourceManager.getExecutionSnapshot();
        String expectedStorageTso = buildExpectedStorageTso();
        StorageHistoryInfo storageHistoryInfo = buildStorageHistoryInfo(expectedStorageTso);
        List<StorageInfo> storageInfos = buildStorageInfos(storageHistoryInfo);

        // 为集群计算一个新的运行拓扑
        TopologyServiceHelper.CheckResult checkResult = shouldRefreshTopology(resourceManager, preClusterSnapshot,
            storageInfos, executionSnapshot, storageHistoryInfo);
        if (checkResult.needRebalance) {
            List<Container> containersBeforeRandomRemove = resourceManager.availableContainers();
            List<Container> containersAfterRandomRemove = resourceManager.tryRandomRemoveContainer(
                containersBeforeRandomRemove, checkResult.forceIntervalRebalance);

            DumperMasterSelector dumperMasterSelector = new DumperMasterSelector(clusterId,
                getObject(NodeInfoMapper.class), getObject(DumperInfoMapper.class));
            String dumperMasterNode = dumperMasterSelector.selectDumperMasterNode(
                containersAfterRandomRemove.stream().map(Container::getContainerId).collect(Collectors.toSet()),
                preClusterSnapshot);

            boolean isLightRebalance = !checkResult.fullRebalance
                && isLightRebalance(clusterId, preClusterSnapshot.getDumperMasterNode(), dumperMasterNode);
            long newVersion = isLightRebalance ? preClusterSnapshot.getVersion() : preClusterSnapshot.getVersion() + 1;
            long newSubVersion = isLightRebalance ? preClusterSnapshot.getSubVersion() + 1 : 1;

            long serverId = ServerConfigUtil.getGlobalNumberVarDirect(SERVER_ID);
            TopologyEntity topologyEntity = topologyBuilder.buildTopology(containersAfterRandomRemove, storageInfos,
                expectedStorageTso, newVersion, newSubVersion, dumperMasterNode, serverId,
                preClusterSnapshot.getFinalTaskNode());

            ClusterSnapshot postClusterSnapshot = buildPostClusterSnapshot(topologyEntity,
                containersAfterRandomRemove, containersBeforeRandomRemove, storageInfos, newVersion,
                newSubVersion, dumperMasterNode, storageHistoryInfo);
            persist(clusterId, storageHistoryInfo, storageInfos, topologyEntity.getTaskConfigs(),
                preClusterSnapshot, postClusterSnapshot, executionSnapshot, isLightRebalance);
            log.info("Topology with version {}:{} is successfully build.", newVersion, newSubVersion);
        }
    }

    /**
     * 保证有足够的节点数，否则不进行拓扑计算
     */
    private void ensureContainerMinSizeConstrain(ResourceManager resourceManager) throws Throwable {
        //抛异常出去也是继续重试，所以尝试多等一些时间，1min
        RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(60)
            .fixedBackoff(1000)
            .retryOn(RetryableException.class)
            .build();

        // 未达到法定个数，不能进行拓扑计算，避免不必要的的Topology build
        // nodeCount是数据库中所有合法的node记录的个数，如果出现Node节点宕机，记录还在，所以不影响拓扑重建，即不影响HA
        retryTemplate.execute((RetryCallback<Integer, Throwable>) retryContext -> {
            int nodeCount = resourceManager.allContainers().size();
            int topologyNodeMinSize = DynamicApplicationConfig.getInt(ConfigKeys.TOPOLOGY_NODE_MINSIZE);
            if (nodeCount < topologyNodeMinSize) {
                log.warn("wait for container nodes ready(need " + topologyNodeMinSize
                    + " container at least)..., current node count is " + nodeCount);
                throw new RetryableException("cdc cluster is not ready, current node count is " + nodeCount);
            }
            return nodeCount;
        });
    }

    private ClusterSnapshot buildPostClusterSnapshot(TopologyEntity topologyEntity,
                                                     List<Container> containers,
                                                     List<Container> containersBeforeRandomRemove,
                                                     List<StorageInfo> storageInfos,
                                                     long newVersion,
                                                     long newSubVersion,
                                                     String dumperMasterNode,
                                                     StorageHistoryInfo storageHistoryInfo) {
        Optional<String> dumperMasterOptional = topologyEntity.getTaskConfigs().stream()
            .filter(c -> TaskType.Dumper.name().equals(c.getRole()) && c.getContainerId().equals(dumperMasterNode))
            .map(BinlogTaskConfig::getTaskName)
            .findFirst();
        String dumperMasterName;
        if (!dumperMasterOptional.isPresent()) {
            throw new PolardbxException("can not found dumper on container " + dumperMasterNode +
                " with topology configs :" + topologyEntity);
        } else {
            dumperMasterName = dumperMasterOptional.get();
        }

        ClusterSnapshot postClusterSnapshot = new ClusterSnapshot(newVersion,
            System.currentTimeMillis(),
            containers.stream().map(Container::getContainerId).collect(Collectors.toSet()),
            storageInfos.stream().map(StorageInfo::getStorageInstId).collect(Collectors.toSet()),
            dumperMasterNode,
            dumperMasterName,
            storageHistoryInfo == null ? ExecutionConfig.ORIGIN_TSO : storageHistoryInfo.getTso(),
            clusterType,
            topologyEntity.getServerId(),
            newSubVersion,
            topologyEntity.getFinalTaskNode());

        postClusterSnapshot.setContainersBeforeRandomRemove(
            containersBeforeRandomRemove.stream().map(Container::getContainerId).collect(Collectors.toSet()));
        return postClusterSnapshot;
    }

    private void persist(final String clusterId, final StorageHistoryInfo storageHistoryInfo,
                         final List<StorageInfo> storageInfos, final List<BinlogTaskConfig> taskConfigList,
                         final ClusterSnapshot preClusterSnapshot, final ClusterSnapshot postClusterSnapshot,
                         final ExecutionSnapshot executionSnapshot, final boolean lightRebalance) {
        // 持久化之前再次进行一下验证，如果已经不是Leader，则放弃持久化
        if (!RuntimeLeaderElector.isDaemonLeader()) {
            log.info("current daemon is not a leader, skip the topology persisting.!");
            return;
        }

        transactionTemplate.execute((o) -> {
            if (!lockAndCheck(preClusterSnapshot)) {
                return null;
            }

            updateBinlogTaskConfig(clusterId, taskConfigList);
            cleanStaleRunningInfo(taskConfigList, lightRebalance);
            initStorageHistoryInfo(storageHistoryInfo, storageInfos, clusterId);
            SystemDbConfig.updateSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY, toJSONString(postClusterSnapshot));
            deleteNonAvailableNode(clusterId, postClusterSnapshot);
            recordScheduleHistory(executionSnapshot, postClusterSnapshot, taskConfigList);
            return null;
        });
    }

    private void updateBinlogTaskConfig(final String clusterId, final List<BinlogTaskConfig> taskConfigList) {
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
                taskConfigMapper.insertSelective(taskConfig);
            }
        }

        Set<String> allRoleNameList = taskConfigList.stream()
            .map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());
        taskConfigMapper.delete(
            s -> s.where(BinlogTaskConfigDynamicSqlSupport.taskName, SqlBuilder.isNotIn(allRoleNameList))
                .and(BinlogTaskConfigDynamicSqlSupport.clusterId, isEqualTo(clusterId)));
    }

    private void cleanStaleRunningInfo(final List<BinlogTaskConfig> taskConfigList, final boolean lightRebalance) {
        // prepare for different role
        Set<String> dumperRoleConfigList = taskConfigList.stream()
            .filter(c -> c.getRole().equals(TaskType.Dumper.name()))
            .map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());
        Set<String> taskRoleConfigList = taskConfigList.stream()
            .filter(c -> c.getRole().equals(TaskType.Final.name()) || c.getRole().equals(TaskType.Relay.name()))
            .map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());

        if (lightRebalance) {
            dumperInfoMapper.delete(s -> s.where
                    (DumperInfoDynamicSqlSupport.taskName, SqlBuilder.isNotIn(dumperRoleConfigList))
                .and(DumperInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));
            taskInfoMapper.delete(s -> s.where(
                    BinlogTaskInfoDynamicSqlSupport.taskName, SqlBuilder.isNotIn(taskRoleConfigList))
                .and(BinlogTaskInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));
        } else {
            dumperInfoMapper.delete(s -> s.where(DumperInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId)));
            taskInfoMapper.delete(s -> s.where(BinlogTaskInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId)));
        }
    }

    private void initStorageHistoryInfo(StorageHistoryInfo storageHistoryInfo, final List<StorageInfo> storageInfos,
                                        final String clusterId) {
        //初始化storageHistory
        if (storageHistoryInfo == null) {
            StorageContent storageContent = new StorageContent();
            storageContent.setStorageInstIds(storageInfos.stream()
                .map(StorageInfo::getStorageInstId).collect(Collectors.toList()));

            StorageHistoryInfo info = new StorageHistoryInfo();
            info.setStatus(0);
            info.setTso(ExecutionConfig.ORIGIN_TSO);
            info.setStorageContent(toJSONString(storageContent));
            info.setInstructionId("-1");
            info.setClusterId(clusterId);
            info.setGroupName(GROUP_NAME_GLOBAL);
            storageHistoryMapper.insertSelective(info);
        }
    }

    private void recordScheduleHistory(ExecutionSnapshot executionSnapshot, ClusterSnapshot clusterSnapshot,
                                       List<BinlogTaskConfig> taskConfigList) {
        //记录历史
        BinlogScheduleHistory history = new BinlogScheduleHistory();
        history.setVersion(clusterSnapshot.getVersion());
        history.setSubVersion(clusterSnapshot.getSubVersion());
        history.setClusterId(clusterId);
        history.setContent(
            toJSONString(new ScheduleHistoryContent(executionSnapshot, taskConfigList, clusterSnapshot)));
        scheduleHistoryMapper.insertSelective(history);
    }

    private void deleteNonAvailableNode(final String clusterId, ClusterSnapshot postClusterSnapshot) {
        Set<String> containers = postClusterSnapshot.getContainers();
        if (!containers.isEmpty()) {
            nodeInfoMapper.delete(s -> s.where(NodeInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId))
                .and(NodeInfoDynamicSqlSupport.containerId, isNotIn(containers)));
        }
    }

    private boolean needRestart(BinlogTaskConfig newConfig, BinlogTaskConfig oldConfig) {
        // 如果是IP发生了变更，TaskKeepAlive会自动调度，无需设置为"待重启"状态
        if (StringUtils.equals(newConfig.getIp(), oldConfig.getIp())) {
            // 如果是mem或Port发生了变更，靠重启进程来实现
            if (!newConfig.getMem().equals(oldConfig.getMem())) {
                return true;
            }
            if (!newConfig.getPort().equals(oldConfig.getPort())) {
                return true;
            }
        }
        return false;
    }
}
