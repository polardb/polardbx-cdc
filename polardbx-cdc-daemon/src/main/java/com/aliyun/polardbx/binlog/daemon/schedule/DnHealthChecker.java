/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.schedule.checker.HealthChecker;
import com.aliyun.polardbx.binlog.daemon.schedule.checker.ShowSlaveChecker;
import com.aliyun.polardbx.binlog.dao.StorageInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.enums.ClusterRole;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import com.aliyun.polardbx.binlog.util.LabEventType;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_DN_HEALTH_CHECKER_SWITCH;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.id;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.instKind;
import static com.aliyun.polardbx.binlog.dao.StorageInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isNotEqualTo;

public class DnHealthChecker extends AbstractBinlogTimerTask {

    private static final Logger logger = LoggerFactory.getLogger(DnHealthChecker.class);
    private final long sla;
    private final StorageInfoMapper storageInfoMapper;
    private Map<String, HealthChecker> dnDelayCheckerMap = Maps.newHashMap();

    public DnHealthChecker(String clusterId, String clusterType, String name, long interval) {
        super(clusterId, clusterType, name, interval);
        storageInfoMapper = SpringContextHolder.getObject(StorageInfoMapper.class);
        sla = DynamicApplicationConfig.getLong(ConfigKeys.DAEMON_DN_HEALTH_CHECKER_SLA_LIMIT_SECOND);
        logger.info("health checker init");
    }

    @Override
    public void exec() {

        if (!StringUtils.equalsIgnoreCase(DynamicApplicationConfig.getString(DAEMON_DN_HEALTH_CHECKER_SWITCH), "ON")) {
            return;
        }

        String clusterRole = DynamicApplicationConfig.getClusterRole();
        if (!ClusterRole.slave.name().equalsIgnoreCase(clusterRole)) {
            return;
        }
        if (!RuntimeLeaderElector.isDaemonLeader()) {
            if (logger.isDebugEnabled()) {
                logger.debug("current daemon is not a leader, skip the dn health checker!");
            }
            return;
        }

        doCheck();
    }

    private void doCheck() {
        buildCheckStorageMap();
        boolean sameRegion = DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_SAME_REGION_STORAGE_BINLOG);
        for (Map.Entry<String, HealthChecker> checkerEntry : dnDelayCheckerMap.entrySet()) {
            HealthChecker checker = checkerEntry.getValue();
            String storageInstId = checkerEntry.getKey();
            try {
                long checkDelayInSecond = Long.MAX_VALUE;
                try {
                    checkDelayInSecond = checker.check();
                    logger.info("storage[" + storageInstId + "] delay " + checkDelayInSecond + " second!");
                } catch (Throwable t) {
                    logger.error("check storage inst delay failed!, will try switch!", t);
                }

                if (checkDelayInSecond > sla) {
                    if (sameRegion) {
                        // switch to leader
                        switchForceLeader(storageInstId);
                    }
                    return;
                }
            } catch (Exception e) {
                logger.error("do checker dn health failed !", e);
            }
        }
        if (!sameRegion) {
            switchSameZoneDn();
        }

    }

    private void switchForceLeader(String storageInstId) {
        String clusterKeysPattern = DynamicApplicationConfig.getString(CLUSTER_ID) + ":";
        DynamicApplicationConfig.setValue(clusterKeysPattern + ConfigKeys.TASK_DUMP_SAME_REGION_STORAGE_BINLOG,
            false + "");
        LabEventManager.logEvent(LabEventType.DETECTED_DN_FOLLOWER_DELAY, "errorStorage:" + storageInstId);
    }

    private void switchSameZoneDn() {
        String clusterKeysPattern = DynamicApplicationConfig.getString(CLUSTER_ID) + ":";
        DynamicApplicationConfig.setValue(clusterKeysPattern + ConfigKeys.TASK_DUMP_SAME_REGION_STORAGE_BINLOG,
            true + "");
        LabEventManager.logEvent(LabEventType.DETECTED_DN_FOLLOWER_NORMAL);
    }

    private void buildCheckStorageMap() {
        List<StorageInfo> storageInfos = storageInfoMapper.select(c ->
            c.where(instKind, isEqualTo(0))
                .and(status, isNotEqualTo(2))
                .orderBy(id)
        );
        storageInfos = Lists.newArrayList(storageInfos.stream().collect(
            Collectors.toMap(StorageInfo::getStorageInstId, s1 -> s1,
                (s1, s2) -> s1)).values());

        Map<String, HealthChecker> newDnDelayCheckerMap = Maps.newHashMap();
        for (StorageInfo info : storageInfos) {
            HealthChecker checker = dnDelayCheckerMap.get(info.getStorageMasterInstId());
            if (checker != null) {
                newDnDelayCheckerMap.put(info.getStorageMasterInstId(), checker);
                continue;
            }
            HealthChecker delayChecker = buildHealthChecker(info);
            newDnDelayCheckerMap.put(info.getStorageMasterInstId(), delayChecker);
        }
        dnDelayCheckerMap = newDnDelayCheckerMap;
    }

    private HealthChecker buildHealthChecker(StorageInfo storageInfo) {
        return new ShowSlaveChecker(storageInfo);
    }
}
