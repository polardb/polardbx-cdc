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
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.dao.XStreamDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.StorageContent;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ExecutionSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.util.StorageUtil;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_X_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_FORCE_REFRESH_INTERVAL_MINUTE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_SUPPORT_REBUILD_ONLY_DAEMON_DOWN;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instKind;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.status;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.storageInstId;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isIn;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class TopologyServiceHelper {
    private static final Gson GSON = new GsonBuilder().create();

    private static final DumperInfoMapper dumperInfoMapper = getObject(DumperInfoMapper.class);

    private static final BinlogTaskInfoMapper taskInfoMapper = getObject(BinlogTaskInfoMapper.class);

    private static final JdbcTemplate jdbcTemplate = getObject("metaJdbcTemplate");

    private static long lastForceRefreshTime = System.currentTimeMillis();

    public static void checkContainers(ResourceManager resourceManager) {
        Set<String> set = resourceManager.allOfflineContainers();
        if (!set.isEmpty()) {
            MonitorManager.getInstance().triggerAlarm(MonitorType.DAEMON_PROCESS_DEAD_ERROR, set);
            log.warn("Daemon process on containers {} is down.", set);
        }
    }

    public static void clearStaleInfos(long currentVersion) {
        //忽略版本号为0的记录，兼容老版调度算法，保证顺利平滑升级
        taskInfoMapper.delete(s ->
            s.where(BinlogTaskInfoDynamicSqlSupport.version, SqlBuilder.isLessThan(currentVersion))
                .and(BinlogTaskInfoDynamicSqlSupport.version, SqlBuilder.isNotEqualTo(0L))
                .and(BinlogTaskInfoDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(CLUSTER_ID))));
        dumperInfoMapper.delete(s ->
            s.where(DumperInfoDynamicSqlSupport.version, SqlBuilder.isLessThan(currentVersion))
                .and(DumperInfoDynamicSqlSupport.version, SqlBuilder.isNotEqualTo(0L))
                .and(DumperInfoDynamicSqlSupport.clusterId,
                    SqlBuilder.isEqualTo(DynamicApplicationConfig.getString(CLUSTER_ID))));
    }

    public static String buildExpectedStorageTso() {
        String configuredStorageTso = StorageUtil.getConfiguredExpectedStorageTso();
        return StringUtils.isBlank(configuredStorageTso) ? ExecutionConfig.ORIGIN_TSO
            : configuredStorageTso;
    }

    public static String buildExpectedStorageTso4BinlogX() {
        List<XStream> streamList = getStreamConfig();
        Optional<String> optional = streamList.stream().map(XStream::getExpectedStorageTso).min(String::compareTo);
        if (optional.isPresent() && StringUtils.isNotBlank(optional.get())) {
            return optional.get();
        } else {
            return ExecutionConfig.ORIGIN_TSO;
        }
    }

    public static StorageHistoryInfo buildStorageHistoryInfo(String expectedStorageTso) {
        StorageHistoryInfoMapper storageHistoryMapper = getObject(StorageHistoryInfoMapper.class);
        List<StorageHistoryInfo> storageHistoryInfos = storageHistoryMapper.select(s -> s.where(
            StorageHistoryInfoDynamicSqlSupport.clusterId, isEqualTo(DynamicApplicationConfig.getString(CLUSTER_ID)))
            .and(StorageHistoryInfoDynamicSqlSupport.status, isEqualTo(0)));
        if (storageHistoryInfos.isEmpty()) {
            return null;
        } else {
            Optional<StorageHistoryInfo> optional = storageHistoryInfos.stream()
                .filter(s -> StringUtils.equals(s.getTso(), expectedStorageTso)).findFirst();
            if (!optional.isPresent()) {
                throw new PolardbxException("can't find storage history info for tso :" + expectedStorageTso);
            }
            return optional.get();
        }
    }

    public static boolean shouldRefreshTopology(ResourceManager resourceManager, ClusterSnapshot preClusterSnapshot,
                                                List<StorageInfo> storages, ExecutionSnapshot executionSnapshot,
                                                StorageHistoryInfo storageHistoryInfo) {
        if (preClusterSnapshot.isNew()) {
            log.info("cluster snapshot is new, topology will rebuild.");
            return true;
        }

        int forceRefreshInterval = DynamicApplicationConfig.getInt(TOPOLOGY_FORCE_REFRESH_INTERVAL_MINUTE);
        if (forceRefreshInterval > 0) {
            long intervalMillis = TimeUnit.MINUTES.toMillis(forceRefreshInterval);
            if (System.currentTimeMillis() - lastForceRefreshTime >= intervalMillis) {
                log.info("force refresh topology, with previous cluster snapshot " + preClusterSnapshot);
                lastForceRefreshTime = System.currentTimeMillis();
                return true;
            }
        }

        Set<String> latestContainers = resourceManager.allOnlineContainers();
        Set<String> latestStorages = storages.stream().map(StorageInfo::getStorageInstId).collect(Collectors.toSet());
        Set<String> newlyAddContainers = latestContainers.stream().filter(
            c -> !preClusterSnapshot.getContainers().contains(c)).collect(Collectors.toSet());
        Set<String> newlyRemovedContainers = preClusterSnapshot.getContainers().stream().filter(
            c -> !latestContainers.contains(c)).collect(Collectors.toSet());
        boolean isStorageChange =
            !(latestStorages.equals(preClusterSnapshot.getStorages())
                && StringUtils.equals(storageHistoryInfo.getTso(), preClusterSnapshot.getStorageHistoryTso()));

        if (isStorageChange) {
            log.info("detected storage changing ,will rebuild topology.");
            return true;
        }
        if (!newlyAddContainers.isEmpty()) {
            log.info("detected newly add containers ,will rebuild topology, {}.", newlyAddContainers);
            return true;
        }

        if (!newlyRemovedContainers.isEmpty()) {
            boolean supportRebuild = DynamicApplicationConfig.getBoolean(TOPOLOGY_SUPPORT_REBUILD_ONLY_DAEMON_DOWN);
            if (supportRebuild) {
                return true;
            }

            if (!executionSnapshot.isAllRunningOk()) {
                boolean flag = !resourceManager.isAllContainerExist(newlyRemovedContainers);
                flag |= !newlyRemovedContainers.stream().allMatch(executionSnapshot::isRunningOk4Container);
                if (flag) {
                    log.info("detected newly removed containers ,will rebuild topology, {}.", newlyRemovedContainers);
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean lockAndCheck(ClusterSnapshot preClusterSnapshot) {
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

    public static List<StorageInfo> buildStorageInfos(StorageHistoryInfo latestStorageHistory) {
        final StorageInfoMapper storageInfoMapper = getObject(StorageInfoMapper.class);
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

    public static List<XStream> getStreamConfig() {
        String streamGroupName = DynamicApplicationConfig.getString(BINLOG_X_STREAM_GROUP_NAME);
        XStreamMapper mapper = SpringContextHolder.getObject(XStreamMapper.class);
        return mapper.select(s -> s.where(XStreamDynamicSqlSupport.groupName, isEqualTo(streamGroupName))
            .orderBy(XStreamDynamicSqlSupport.streamName));
    }
}
