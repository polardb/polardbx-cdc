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

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogScheduleHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryDetailInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.domain.StorageContent;
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
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SUSPEND_TOPOLOGY_REBUILDING;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildExpectedStorageTso4BinlogX;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildStorageHistoryInfo;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.buildStorageInfos;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.checkContainers;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.clearStaleInfos;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.getStreamConfig;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.lockAndCheck;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.shouldRefreshTopology;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BinlogXTopologyService implements TopologyService {
    private static final Gson GSON = new GsonBuilder().create();

    private final String clusterId;
    private final String clusterType;
    private final BinlogXTopologyBuilder topologyBuilder;
    private final ResourceManager resourceManager;

    private final TransactionTemplate transactionTemplate =
        SpringContextHolder.getObject("metaTransactionTemplate");
    private final BinlogTaskConfigMapper taskConfigMapper =
        SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
    private final DumperInfoMapper dumperInfoMapper =
        SpringContextHolder.getObject(DumperInfoMapper.class);
    private final StorageHistoryInfoMapper storageHistoryMapper =
        SpringContextHolder.getObject(StorageHistoryInfoMapper.class);
    private final StorageHistoryDetailInfoMapper storageHistoryDetailInfoMapper =
        SpringContextHolder.getObject(StorageHistoryDetailInfoMapper.class);
    private final BinlogScheduleHistoryMapper scheduleHistoryMapper =
        SpringContextHolder.getObject(BinlogScheduleHistoryMapper.class);
    private final BinlogTaskInfoMapper taskInfoMapper =
        SpringContextHolder.getObject(BinlogTaskInfoMapper.class);

    public BinlogXTopologyService(String clusterId, String clusterType) {
        this.clusterId = clusterId;
        this.clusterType = clusterType;
        this.topologyBuilder = new BinlogXTopologyBuilder(clusterId);
        this.resourceManager = new ResourceManager(clusterId);
    }

    @Override
    public void tryBuild() {
        String suspendTopologyRebuilding = SystemDbConfig.getSystemDbConfig(CLUSTER_SUSPEND_TOPOLOGY_REBUILDING);
        if (StringUtils.isNotBlank(suspendTopologyRebuilding) && "true".equals(suspendTopologyRebuilding)) {
            log.info("current cluster is in suspend state , skip rebuilding cluster topology");
            return;
        }

        refreshTopology();
    }

    private void refreshTopology() {
        log.info("current daemon is leader, do with the cluster's topology project!");
        checkContainers(resourceManager);
        String preClusterConfigStr = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
        ClusterSnapshot preClusterSnapshot = GSON.fromJson(preClusterConfigStr, ClusterSnapshot.class);
        clearStaleInfos(preClusterSnapshot.getVersion());
        String expectedStorageTso = buildExpectedStorageTso4BinlogX();
        ExecutionSnapshot executionSnapshot = resourceManager.getExecutionSnapshot();
        StorageHistoryInfo storageHistoryInfo = buildStorageHistoryInfo(expectedStorageTso);
        List<StorageInfo> storageInfos = buildStorageInfos(storageHistoryInfo);

        if (shouldRefreshTopology(resourceManager, preClusterSnapshot, storageInfos, executionSnapshot,
            storageHistoryInfo)) {
            long newVersion = preClusterSnapshot.getVersion() + 1;
            List<Container> containers = resourceManager.availableContainers();
            List<BinlogTaskConfig> taskConfigs = topologyBuilder.buildTopology(containers, storageInfos,
                buildExpectedStorageTso4BinlogX(), newVersion, preClusterSnapshot);
            ClusterSnapshot postClusterSnapshot = new ClusterSnapshot(newVersion,
                System.currentTimeMillis(),
                containers.stream().map(Container::getContainerId).collect(Collectors.toSet()),
                storageInfos.stream().map(StorageInfo::getStorageInstId).collect(Collectors.toSet()),
                "",
                "",
                storageHistoryInfo == null ? ExecutionConfig.ORIGIN_TSO : storageHistoryInfo.getTso(),
                clusterType);
            persist(taskConfigs, storageInfos, preClusterSnapshot, postClusterSnapshot,
                storageHistoryInfo, executionSnapshot);
        }
    }

    private void persist(List<BinlogTaskConfig> taskConfigs, List<StorageInfo> storageInfos,
                         ClusterSnapshot preClusterSnapshot, ClusterSnapshot postClusterSnapshot,
                         StorageHistoryInfo storageHistoryInfo, ExecutionSnapshot executionSnapshot) {
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
            Set<String> configs =
                taskConfigs.stream().map(BinlogTaskConfig::getTaskName).collect(Collectors.toSet());
            taskConfigMapper.delete(
                s -> s.where(BinlogTaskConfigDynamicSqlSupport.taskName, SqlBuilder.isNotIn(configs))
                    .and(BinlogTaskConfigDynamicSqlSupport.clusterId, isEqualTo(clusterId)));

            // 删除Dumper_info和Task_info
            dumperInfoMapper.delete(s -> s.where(DumperInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId)));
            taskInfoMapper.delete(s -> s.where(DumperInfoDynamicSqlSupport.clusterId, isEqualTo(clusterId)));

            //初始化storageHistory
            if (storageHistoryInfo == null) {
                StorageContent content = new StorageContent();
                content.setStorageInstIds(storageInfos.stream()
                    .map(StorageInfo::getStorageInstId).collect(Collectors.toList()));

                StorageHistoryInfo info = new StorageHistoryInfo();
                info.setStatus(0);
                info.setTso(ExecutionConfig.ORIGIN_TSO);
                info.setStorageContent(GSON.toJson(content));
                info.setInstructionId("-1");
                info.setClusterId(clusterId);
                info.setGroupName(DynamicApplicationConfig.getString(BINLOG_X_STREAM_GROUP_NAME));
                storageHistoryMapper.insert(info);

                List<XStream> streams = getStreamConfig();
                for (XStream stream : streams) {
                    StorageHistoryDetailInfo detailInfo = new StorageHistoryDetailInfo();
                    detailInfo.setStreamName(stream.getStreamName());
                    detailInfo.setInstructionId("-1");
                    detailInfo.setTso(ExecutionConfig.ORIGIN_TSO);
                    detailInfo.setClusterId(clusterId);
                    detailInfo.setStatus(0);
                    storageHistoryDetailInfoMapper.insert(detailInfo);
                }
            }

            //对版本号进行+1并更新
            SystemDbConfig.updateSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY, GSON.toJson(postClusterSnapshot));

            //记录历史
            ScheduleHistoryContent content = new ScheduleHistoryContent(executionSnapshot,
                taskConfigs, postClusterSnapshot);
            BinlogScheduleHistory history = new BinlogScheduleHistory();
            history.setVersion(postClusterSnapshot.getVersion());
            history.setClusterId(clusterId);
            history.setContent(GSON.toJson(content));
            scheduleHistoryMapper.insert(history);

            return null;
        });
    }
}
