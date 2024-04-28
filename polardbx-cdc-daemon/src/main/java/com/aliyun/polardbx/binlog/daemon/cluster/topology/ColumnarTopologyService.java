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
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.columnar.ColumnarMetaManager;
import com.aliyun.polardbx.binlog.daemon.constant.ClusterRebalanceInstruction;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ColumnarTaskMapper;
import com.aliyun.polardbx.binlog.domain.po.ColumnarTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ColumnarResourceManager;
import com.aliyun.polardbx.binlog.scheduler.ExecutionSnapshot;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.mybatis.dynamic.sql.where.condition.IsEqualTo;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SUSPEND_TOPOLOGY_REBUILDING;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildExpectedStorageTso;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildStorageHistoryInfo;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildStorageInfos;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.lockAndCheck;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Slf4j
public class ColumnarTopologyService implements TopologyService {
    private static final Gson GSON = new GsonBuilder().create();

    private final ColumnarTopologyBuilder topologyBuilder;
    private final String clusterId;
    private final String clusterType;
    private static String leaderIp;
    private final ColumnarTaskConfigMapper columnarTaskConfigMapper = getObject(ColumnarTaskConfigMapper.class);

    private final ColumnarTaskMapper columnarTaskMapper = getObject(ColumnarTaskMapper.class);

    private final TransactionTemplate transactionTemplate = getObject("metaTransactionTemplate");

    private static long lastForceRefreshTime = System.currentTimeMillis();

    public ColumnarTopologyService(String clusterId, String clusterType) {
        this.clusterId = clusterId;
        this.clusterType = clusterType;
        this.topologyBuilder = new ColumnarTopologyBuilder(clusterId);
    }

    @Override
    public void tryBuild() throws Throwable {
        String suspendTopologyRebuilding = SystemDbConfig.getSystemDbConfig(CLUSTER_SUSPEND_TOPOLOGY_REBUILDING);
        if (StringUtils.isNotBlank(suspendTopologyRebuilding) && "true".equals(suspendTopologyRebuilding)) {
            log.info("current cluster is in suspend state , skip rebuilding cluster topology");
            return;
        }

        //check and prepare parameter
        log.info("current daemon is leader, do with the cluster's topology project!");
        ColumnarResourceManager resourceManager = new ColumnarResourceManager(clusterId);
        checkColumnarContainers(resourceManager);
        String preClusterConfigStr = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
        ClusterSnapshot preClusterSnapshot = GSON.fromJson(preClusterConfigStr, ClusterSnapshot.class);
        ExecutionSnapshot executionSnapshot = resourceManager.getExecutionSnapshot();

        String expectedStorageTso = buildExpectedStorageTso();

        StorageHistoryInfo storageHistoryInfo = buildStorageHistoryInfo(expectedStorageTso);
        List<StorageInfo> storageInfos = buildStorageInfos(storageHistoryInfo);

        if (shouldRefreshTopology(resourceManager, preClusterSnapshot, executionSnapshot)) {
            if (leaderIp == null || leaderIp.isEmpty()) {
                log.warn("columnar_lease is empty, no leader found.");
            }
            long newVersion = preClusterSnapshot.getVersion() + 1;
            List<Container> containers = resourceManager.availableContainers();
            List<ColumnarTaskConfig> topologyConfigs = topologyBuilder.buildTopology(containers, newVersion, leaderIp);
            ClusterSnapshot postClusterSnapshot =
                buildPostClusterSnapshot(containers, storageInfos, newVersion, storageHistoryInfo);

            persist(clusterId, topologyConfigs, preClusterSnapshot, postClusterSnapshot
            );
        }
    }

    public static void checkColumnarContainers(ColumnarResourceManager resourceManager) {
        Set<String> set = resourceManager.allOfflineContainers();
        if (!set.isEmpty()) {
            MonitorManager.getInstance().triggerAlarm(MonitorType.COLUMNAR_PROCESS_DEAD_ERROR, set);
            log.warn("Columnar process on containers {} is down.", set);
        }
    }

    private ClusterSnapshot buildPostClusterSnapshot(List<Container> containers,
                                                     List<StorageInfo> storageInfos,
                                                     long newVersion,
                                                     StorageHistoryInfo storageHistoryInfo) {
        return new ClusterSnapshot(newVersion,
            System.currentTimeMillis(),
            containers.stream().map(Container::getContainerId).collect(Collectors.toSet()),
            storageInfos.stream().map(StorageInfo::getStorageInstId).collect(Collectors.toSet()),
            null,
            null,
            storageHistoryInfo == null ? ExecutionConfig.ORIGIN_TSO : storageHistoryInfo.getTso(),
            clusterType,
            0L);
    }

    private void persist(final String cluster, final List<ColumnarTaskConfig> taskConfigs,
                         final ClusterSnapshot preClusterSnapshot, final ClusterSnapshot postClusterSnapshot) {
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
            for (ColumnarTaskConfig taskConfig : taskConfigs) {
                Optional<ColumnarTaskConfig> config = columnarTaskConfigMapper.selectOne(
                    s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId, isEqualTo(cluster))
                        .and(BinlogTaskConfigDynamicSqlSupport.taskName, isEqualTo(taskConfig.getTaskName())));
                if (config.isPresent()) {
                    ColumnarTaskConfig origin = config.get();
                    taskConfig.setId(origin.getId());
                    taskConfig.setStatus(null);
                    columnarTaskConfigMapper.updateByPrimaryKeySelective(taskConfig);
                    updateTaskRole(taskConfig.getClusterId(), taskConfig.getTaskName(), taskConfig.getVersion(),
                        taskConfig.getRole());
                } else {
                    columnarTaskConfigMapper.insert(taskConfig);
                }
            }
            Set<String> configs = taskConfigs.stream().map(ColumnarTaskConfig::getTaskName).collect(Collectors.toSet());
            columnarTaskConfigMapper.delete(
                s -> s.where(ColumnarTaskConfigDynamicSqlSupport.taskName, SqlBuilder.isNotIn(configs))
                    .and(ColumnarTaskConfigDynamicSqlSupport.clusterId, isEqualTo(cluster)));

            columnarTaskMapper.delete(
                s -> s.where(ColumnarTaskConfigDynamicSqlSupport.taskName, SqlBuilder.isNotIn(configs))
                    .and(ColumnarTaskDynamicSqlSupport.taskName, SqlBuilder.isNotIn(configs)));

            //对版本号进行+1并更新
            SystemDbConfig.updateSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY, GSON.toJson(postClusterSnapshot));

            return null;
        });
    }

    public void updateTaskRole(String clusterId, String taskName, long version, String role) {
        columnarTaskMapper.update(
            s -> s.set(ColumnarTaskDynamicSqlSupport.role).equalTo(role)
                .set(ColumnarTaskDynamicSqlSupport.version).equalTo(version)
                .where(ColumnarTaskDynamicSqlSupport.taskName, SqlBuilder.isEqualTo(taskName))
                .and(ColumnarTaskDynamicSqlSupport.clusterId, SqlBuilder.isEqualTo(clusterId)));
    }

    public static boolean shouldRefreshTopology(ColumnarResourceManager resourceManager,
                                                ClusterSnapshot preClusterSnapshot,
                                                ExecutionSnapshot executionSnapshot) {
        if (SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION).equals(
            ClusterRebalanceInstruction.SET_REBALANCE_INSTRUCTION)) {
            SystemDbConfig.updateSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION,
                ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);
            log.info("cluster rebalance instruction is set, topology will rebuild");
            return true;
        }

        if (preClusterSnapshot.isNew()) {
            log.info("cluster snapshot is new, topology will rebuild.");
            return true;
        }

        int forceRefreshInterval = DynamicApplicationConfig.getInt(DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL);
        if (forceRefreshInterval > 0) {
            long intervalMillis = TimeUnit.MINUTES.toMillis(forceRefreshInterval);
            if (System.currentTimeMillis() - lastForceRefreshTime >= intervalMillis) {
                log.info("force refresh topology, with previous cluster snapshot " + preClusterSnapshot);
                lastForceRefreshTime = System.currentTimeMillis();
                return true;
            }
        }

        Set<String> latestContainers = resourceManager.allOnlineContainers();
        Set<String> newlyAddContainers = latestContainers.stream().filter(
            c -> !preClusterSnapshot.getContainers().contains(c)).collect(Collectors.toSet());
        if (!newlyAddContainers.isEmpty()) {
            log.info("detected newly add containers ,will rebuild topology, {}.", newlyAddContainers);
            return true;
        }

        leaderIp = ColumnarMetaManager.getInstant().getLeaderInfo().getIp();
        ColumnarTaskConfigMapper mapper = SpringContextHolder.getObject(ColumnarTaskConfigMapper.class);
        Optional<ColumnarTaskConfig> opTask = mapper
            .selectOne(s -> s.where(ColumnarTaskConfigDynamicSqlSupport.role, IsEqualTo.of(() -> "Leader")));
        if (!opTask.isPresent() || leaderIp == null || leaderIp.isEmpty()) {
            log.info("detected columnar leader is empty ,will rebuild topology, {}.", newlyAddContainers);
            return true;
        }

        ColumnarTaskConfig taskConfig = opTask.get();
        if (!Objects.equals(taskConfig.getIp(), leaderIp)) {
            log.info("detected columnar leader has changed ,will rebuild topology, {}.", newlyAddContainers);
            return true;
        }

        // 如果上一个拓扑中有 a b c三个容器，而现在之后a b两个容器
        // 那么可能有两种情况：
        // 1. c容器中的daemon不正常，导致心跳超时，但是其中的Columnar还是有可能正常运行的
        // 2. c容器被删除了
        Set<String> missedContainers = preClusterSnapshot.getContainers().stream().filter(
            c -> !latestContainers.contains(c)).collect(Collectors.toSet());
        if (!missedContainers.isEmpty()) {
            // Columnar运行不正常
            if (!executionSnapshot.isAllRunningOk()) {
                // 容器被删除了
                boolean flag = !resourceManager.isAllContainerExist(missedContainers);
                // 容器宕机
                flag |= !missedContainers.stream().allMatch(executionSnapshot::isRunningOk4Container);
                if (flag) {
                    log.info("detected newly removed containers ,will rebuild topology, {}.", missedContainers);
                    return true;
                }
            }
        }

        return false;
    }
}
