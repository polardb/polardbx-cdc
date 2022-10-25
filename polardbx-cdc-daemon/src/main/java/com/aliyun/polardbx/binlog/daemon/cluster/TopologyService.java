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
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.cluster.function.TaskDistributionFunction;
import com.aliyun.polardbx.binlog.dao.BinlogScheduleHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.RelayFinalTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.Cursor;
import com.aliyun.polardbx.binlog.domain.StorageContent;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogScheduleHistory;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.error.RetryableException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ExecutionSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.ScheduleHistoryContent;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.TaskConfig;
import com.aliyun.polardbx.binlog.util.StorageUtil;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SUSPEND_TOPOLOGY_REBUILDING;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instKind;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.status;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.storageInstId;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isIn;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;

/**
 * Created by ShuGuang & ziyang.lb
 */
@Slf4j
public class TopologyService {
    private static final Gson GSON = new GsonBuilder().create();

    private final TaskDistributionFunction taskDistributionFunction;
    private final String clusterId;

    //mappers
    private final BinlogTaskConfigMapper taskConfigMapper =
        SpringContextHolder.getObject(BinlogTaskConfigMapper.class);

    private final DumperInfoMapper dumperInfoMapper =
        SpringContextHolder.getObject(DumperInfoMapper.class);

    private final StorageHistoryInfoMapper storageHistoryMapper =
        SpringContextHolder.getObject(StorageHistoryInfoMapper.class);

    private final BinlogScheduleHistoryMapper scheduleHistoryMapper =
        SpringContextHolder.getObject(BinlogScheduleHistoryMapper.class);

    private final NodeInfoMapper nodeInfoMapper =
        SpringContextHolder.getObject(NodeInfoMapper.class);

    private final RelayFinalTaskInfoMapper taskInfoMapper =
        SpringContextHolder.getObject(RelayFinalTaskInfoMapper.class);

    private final TransactionTemplate transactionTemplate = SpringContextHolder.getObject("metaTransactionTemplate");
    private final JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");

    public TopologyService(TaskDistributionFunction taskDistributionFunction, String clusterId) {
        this.taskDistributionFunction = taskDistributionFunction;
        this.clusterId = clusterId;
    }

    public void tryBuild() throws Throwable {
        String suspendTopologyRebuilding = SystemDbConfig.getSystemDbConfig(CLUSTER_SUSPEND_TOPOLOGY_REBUILDING);
        if (StringUtils.isNotBlank(suspendTopologyRebuilding) && "true".equals(suspendTopologyRebuilding)) {
            log.info("current cluster is in suspend state , skip rebuilding cluster topology");
            return;
        }

        //check and prepare parameter
        log.info("current daemon is leader, do with the cluster's topology project!");
        ResourceManager resourceManager = new ResourceManager(clusterId);
        checkNode(resourceManager);
        checkContainers(resourceManager);
        String preClusterConfigStr = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
        ClusterSnapshot preClusterSnapshot = GSON.fromJson(preClusterConfigStr, ClusterSnapshot.class);
        clearStaleInfos(preClusterSnapshot.getVersion());
        String expectedStorageTso = buildExpectedStorageTso();
        ExecutionSnapshot executionSnapshot = resourceManager.getExecutionSnapshot();

        StorageHistoryInfo storageHistoryInfo = buildStorageHistoryInfo(expectedStorageTso);
        List<StorageInfo> storageInfos = buildStorageInfos(storageHistoryInfo);

        if (shouldRefreshTopology(resourceManager, preClusterSnapshot, storageInfos, executionSnapshot,
            storageHistoryInfo)) {
            long newVersion = preClusterSnapshot.getVersion() + 1;
            List<Container> containers = resourceManager.availableContainers();
            String dumperMasterNode = selectDumperMasterNode(
                containers.stream().map(Container::getContainerId).collect(Collectors.toSet()),
                preClusterSnapshot);

            List<BinlogTaskConfig> topologyConfigs = taskDistributionFunction.apply(containers, storageInfos,
                expectedStorageTso, newVersion, dumperMasterNode);
            ClusterSnapshot postClusterSnapshot = buildPostClusterSnapshot(topologyConfigs,
                containers, storageInfos, newVersion, dumperMasterNode, storageHistoryInfo);
            persist(clusterId, storageHistoryInfo, storageInfos, topologyConfigs, preClusterSnapshot,
                postClusterSnapshot, executionSnapshot);
            log.info("Topology with version {} is successfully build.", newVersion);
        }
    }

    private void checkNode(ResourceManager resourceManager) throws Throwable {
        //抛异常出去也是继续重试，所以尝试多等一些时间，1min
        RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(60)
            .fixedBackoff(1000)
            .retryOn(RetryableException.class)
            .build();

        //未达到法定个数，不能进行拓扑计算，避免不必要的的Topology build
        //nodeCount是数据库中所有合法的node记录的个数，如果出现Node节点宕机，记录还在，所以不影响拓扑重建，即不影响HA
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

    private boolean shouldRefreshTopology(ResourceManager resourceManager, ClusterSnapshot preClusterSnapshot,
                                          List<StorageInfo> storages, ExecutionSnapshot executionSnapshot,
                                          StorageHistoryInfo storageHistoryInfo) {
        if (preClusterSnapshot.isNew()) {
            log.info("cluster snapshot is new, topology will rebuild.");
            return true;
        }

        Set<String> latestContainers = resourceManager.allOnlineContainers();
        Set<String> latestStorages = storages.stream().map(StorageInfo::getStorageInstId).collect(Collectors.toSet());
        boolean isMetaChange =
            !(latestContainers.equals(preClusterSnapshot.getContainers())
                && latestStorages.equals(preClusterSnapshot.getStorages())
                && StringUtils.equals(storageHistoryInfo.getTso(), preClusterSnapshot.getStorageHistoryTso()));
        boolean isAllRunningOk = executionSnapshot.isOK();

        // 如果所有Dumper&Task进程运行OK，只要元数据未发生变化，则不进行拓扑重建
        // 如果有Dumper或Task进程执行有问题，只要其所在的Daemon状态OK，则不进行拓扑重建，对Daemon予以充分信任
        if (isAllRunningOk) {
            if (isMetaChange) {
                log.info("Dumper&Task is all running ok, but containers or storages is changed, "
                    + "topology will rebuild.");
            }
            return isMetaChange;
        } else {
            // 对异常状态的Dumper或Task所在的容器进行检测，如果所在容器的Daemon也不正常，则进行拓扑重建
            Set<String> containers4Check = executionSnapshot.downProcessContainers();
            boolean isOK = resourceManager.isContainersOk(containers4Check);
            if (!isOK) {
                log.warn("something goes wrong for containers {}, topology will rebuild.", containers4Check);
            }
            return !isOK;
        }
    }

    private void checkContainers(ResourceManager resourceManager) {
        Set<String> set = resourceManager.allOfflineContainers();
        if (!set.isEmpty()) {
            MonitorManager.getInstance().triggerAlarm(MonitorType.DAEMON_PROCESS_DEAD_ERROR, set);
            log.warn("Daemon process on containers {} is down.", set);
        }
    }

    private String buildExpectedStorageTso() {
        String configuredStorageTso = StorageUtil.getConfiguredExpectedStorageTso();
        return StringUtils.isBlank(configuredStorageTso) ? TaskConfig.ORIGIN_TSO
            : configuredStorageTso;
    }

    private ClusterSnapshot buildPostClusterSnapshot(List<BinlogTaskConfig> topologyConfigs,
                                                     List<Container> containers,
                                                     List<StorageInfo> storageInfos,
                                                     long newVersion,
                                                     String dumperMasterNode,
                                                     StorageHistoryInfo storageHistoryInfo) {
        Optional<String> dumperMasterOptional = topologyConfigs.stream()
            .filter(c -> TaskType.Dumper.name().equals(c.getRole()) && c.getContainerId().equals(dumperMasterNode))
            .map(BinlogTaskConfig::getTaskName)
            .findFirst();
        String dumperMasterName;
        if (!dumperMasterOptional.isPresent()) {
            throw new PolardbxException("can not found dumper on container " + dumperMasterNode +
                " with topology configs :" + topologyConfigs);
        } else {
            dumperMasterName = dumperMasterOptional.get();
        }

        return new ClusterSnapshot(newVersion,
            System.currentTimeMillis(),
            containers.stream().map(Container::getContainerId).collect(Collectors.toSet()),
            storageInfos.stream().map(StorageInfo::getStorageInstId).collect(Collectors.toSet()),
            dumperMasterNode,
            dumperMasterName,
            storageHistoryInfo == null ? TaskConfig.ORIGIN_TSO : storageHistoryInfo.getTso());
    }

    private String selectDumperMasterNode(Set<String> containers, ClusterSnapshot preClusterSnapshot) {

        //如果强制指定了master node, 且node状态正常，则使用强制指定的node
        String assignedDumperNode = DynamicApplicationConfig.getString(CLUSTER_TOPOLOGY_DUMPER_MASTER_NODE_KEY);
        if (StringUtils.isNotBlank(assignedDumperNode) && containers.contains(assignedDumperNode)) {
            log.info("Dumper master node is selected by force mode, with name {}", assignedDumperNode);
            return assignedDumperNode;
        }

        //优先取上一次的Node继续当master
        String lastNode = preClusterSnapshot.getDumperMasterNode();
        if (StringUtils.isNotBlank(lastNode) && containers.contains(lastNode)) {
            log.info("Dumper master node is selected by previous mode, with name {}.", lastNode);
            return lastNode;
        }

        //取位点最大的Container对应的Dumper为MasterDumper
        Map<Cursor, List<Pair<Cursor, String>>> cursorsMap =
            nodeInfoMapper.select(s -> s.where(NodeInfoDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId))
                .and(NodeInfoDynamicSqlSupport.containerId, SqlBuilder.isIn(containers)))
                .stream().filter(d -> StringUtils.isNotBlank(d.getLatestCursor()))
                .map(s -> new ImmutablePair<>(GSON.fromJson(s.getLatestCursor(), Cursor.class), s.getContainerId()))
                .collect(Collectors.groupingBy(Pair::getLeft));
        Optional<Cursor> maxCursorOptional = cursorsMap.keySet().stream().max(Comparator.comparing(s -> s));
        if (maxCursorOptional.isPresent()) {
            //如果有多个container的cursor并列最大，则随机取一个
            String selectedContainer = cursorsMap.get(maxCursorOptional.get()).get(0).getRight();
            log.info("Dumper master node is selected by max cursor mode, with name {} and max cursor {}.",
                selectedContainer, maxCursorOptional.get());
            return selectedContainer;
        }

        //随机取一个container作为MasterNode
        String masterNode = containers.stream().findAny().get();
        log.warn("Dumper master is selected by min-load mode, with name {}.", masterNode);
        return masterNode;
    }

    private StorageHistoryInfo buildStorageHistoryInfo(String expectedStorageTso) {
        StorageHistoryInfoMapper storageHistoryMapper = SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
        List<StorageHistoryInfo> storageHistoryInfos = storageHistoryMapper.select(s -> s);
        if (storageHistoryInfos.isEmpty()) {
            return null;
        } else {
            Optional<StorageHistoryInfo> optional =
                storageHistoryInfos.stream().filter(s -> StringUtils.equals(s.getTso(), expectedStorageTso))
                    .findFirst();
            if (!optional.isPresent()) {
                throw new PolardbxException("can't find storage history info for tso :" + expectedStorageTso);
            }
            return optional.get();
        }
    }

    private List<StorageInfo> buildStorageInfos(StorageHistoryInfo latestStorageHistory) {
        final StorageInfoMapper storageInfoMapper = SpringContextHolder.getObject(StorageInfoMapper.class);
        List<StorageInfo> storageInfos;

        if (latestStorageHistory != null) {
            StorageContent content = GSON.fromJson(latestStorageHistory.getStorageContent(), StorageContent.class);
            storageInfos = storageInfoMapper.select(c ->
                c.where(storageInstId, isIn(content.getStorageInstIds()))
                    .and(instKind, isEqualTo(0))
                    .and(status, isNotEqualTo(2)).orderBy(id));

            storageInfos = Lists.newArrayList(storageInfos.stream().collect(
                Collectors.toMap(StorageInfo::getStorageInstId, s1 -> s1,
                    (s1, s2) -> s1)).values());

            for (String s : content.getStorageInstIds()) {
                Optional<StorageInfo> optional =
                    storageInfos.stream().filter(i -> s.equals(i.getStorageInstId())).findFirst();
                if (!optional.isPresent()) {
                    throw new PolardbxException("storage info is not found for storageInstId : " + s);
                }
            }
        } else {
            storageInfos = storageInfoMapper.select(c ->
                c.where(instKind, isEqualTo(0))//0:master, 1:slave, 2:metadb
                    .and(status, isNotEqualTo(2))//0:storage ready, 1:prepare offline, 2:storage offline
                    .orderBy(id)
            );
            storageInfos = Lists.newArrayList(storageInfos.stream().collect(
                Collectors.toMap(StorageInfo::getStorageInstId, s1 -> s1,
                    (s1, s2) -> s1)).values());

        }

        return storageInfos;
    }

    private void persist(final String cluster, final StorageHistoryInfo storageHistoryInfo,
                         final List<StorageInfo> storageInfos, final List<BinlogTaskConfig> taskConfigs,
                         final ClusterSnapshot preClusterSnapshot, final ClusterSnapshot postClusterSnapshot,
                         final ExecutionSnapshot executionSnapshot) {
        // 持久化之前再次进行一下验证，如果已经不是Leader，则放弃持久化
        if (!RuntimeLeaderElector.isDaemonLeader()) {
            log.info("current daemon is not a leader, skip the topology persisting.!");
            return;
        }

        transactionTemplate.execute((o) -> {
            if (!lockAndCheck(preClusterSnapshot)) {
                return null;
            }

            //执行拓扑保存
            for (BinlogTaskConfig taskConfig : taskConfigs) {
                Optional<BinlogTaskConfig> config = taskConfigMapper.selectOne(
                    s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId, isEqualTo(cluster))
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
            Set<String> configs = taskConfigs.stream().map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());
            taskConfigMapper.delete(
                s -> s.where(BinlogTaskConfigDynamicSqlSupport.taskName, SqlBuilder.isNotIn(configs)));

            // 删除Dumper_info和Task_info
            dumperInfoMapper.delete(s -> s);
            taskInfoMapper.delete(s -> s);

            //初始化storageHistory
            if (storageHistoryInfo == null) {
                StorageContent content = new StorageContent();
                content.setStorageInstIds(storageInfos.stream()
                    .map(StorageInfo::getStorageInstId).collect(Collectors.toList()));

                StorageHistoryInfo info = new StorageHistoryInfo();
                info.setStatus(0);
                info.setTso(TaskConfig.ORIGIN_TSO);
                info.setStorageContent(GSON.toJson(content));
                info.setInstructionId("-1");
                storageHistoryMapper.insert(info);
            }

            //对版本号进行+1并更新
            SystemDbConfig.updateSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY, GSON.toJson(postClusterSnapshot));

            //记录历史
            BinlogScheduleHistory history = new BinlogScheduleHistory();
            history.setVersion(postClusterSnapshot.getVersion());
            history.setContent(
                GSON.toJson(new ScheduleHistoryContent(executionSnapshot, taskConfigs, postClusterSnapshot)));
            scheduleHistoryMapper.insert(history);
            return null;
        });
    }

    private boolean lockAndCheck(ClusterSnapshot preClusterSnapshot) {
        //加锁并验证版本号是否一致(当daemon出现脑裂的时候，可能出现并发更新拓扑的场景)
        String snapshotInDbStr = jdbcTemplate.queryForObject(
            String.format("select config_value from binlog_system_config where config_key = '%s' for update",
                CLUSTER_SNAPSHOT_VERSION_KEY), String.class);
        ClusterSnapshot snapshotInDb = GSON.fromJson(snapshotInDbStr, ClusterSnapshot.class);

        if (preClusterSnapshot.getVersion() != snapshotInDb.getVersion()) {
            log.info("Topology persisting is ignored because of mismatching versions,"
                    + " old version is {},latest version in db is {} ",
                preClusterSnapshot.getVersion(), snapshotInDb.getVersion());
            return false;
        }
        return true;
    }

    private void clearStaleInfos(long currentVersion) {
        //忽略版本号为0的记录，兼容老版调度算法，保证顺利平滑升级
        taskInfoMapper.delete(s ->
            s.where(RelayFinalTaskInfoDynamicSqlSupport.version, SqlBuilder.isLessThan(currentVersion))
                .and(RelayFinalTaskInfoDynamicSqlSupport.version, SqlBuilder.isNotEqualTo(0L))
                .and(RelayFinalTaskInfoDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID))));
        dumperInfoMapper.delete(s ->
            s.where(DumperInfoDynamicSqlSupport.version, SqlBuilder.isLessThan(currentVersion))
                .and(DumperInfoDynamicSqlSupport.version, SqlBuilder.isNotEqualTo(0L))
                .and(DumperInfoDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID))));
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
