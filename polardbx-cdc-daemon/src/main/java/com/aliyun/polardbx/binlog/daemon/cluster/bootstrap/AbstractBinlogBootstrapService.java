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
package com.aliyun.polardbx.binlog.daemon.cluster.bootstrap;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.cdc.meta.RollbackMode;
import com.aliyun.polardbx.binlog.daemon.rest.resources.MetricsResource;
import com.aliyun.polardbx.binlog.daemon.schedule.LabTestJob;
import com.aliyun.polardbx.binlog.daemon.schedule.MetaDataMonitor;
import com.aliyun.polardbx.binlog.daemon.schedule.TableMetaHistoryWatcher;
import com.aliyun.polardbx.binlog.daemon.schedule.TaskAliveWatcher;
import com.aliyun.polardbx.binlog.daemon.schedule.TopologyWatcher;
import com.aliyun.polardbx.binlog.enums.ClusterRole;
import com.aliyun.polardbx.binlog.heartbeat.TsoHeartbeatTimer;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.task.IScheduleJob;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.LinkedList;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_CLUSTER_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_WORK_PROCESS_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.ENABLE_CDC_META_BUILD_SNAPSHOT;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_FULL_SNAPSHOT_CHECK_INTERVAL_SEC;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_RECOVER_ROLLBACK_MODE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * created by ziyang.lb
 **/
@Slf4j
public abstract class AbstractBinlogBootstrapService implements ClusterBootstrapService {

    private LinkedList<IScheduleJob> jobList;

    protected void beforeInitCommon() {

    }

    protected void initCommon() {
        String cluster = getString(ConfigKeys.CLUSTER_ID);
        String clusterType = clusterType();
        jobList = Lists.newLinkedList();
        // Topology Watcher
        jobList.add(new TopologyWatcher(cluster, clusterType, "TopologyWatcher",
            getInt(DAEMON_WATCH_CLUSTER_INTERVAL_MS)));

        // TaskAliveWatcher
        jobList.add(new TaskAliveWatcher(cluster, clusterType, "TaskKeepAlive",
            getInt(DAEMON_WATCH_WORK_PROCESS_INTERVAL_MS)));

        // TSO心跳定时任务，逻辑binlog stream强依赖该心跳
        TsoHeartbeatTimer tsoHeartbeatTimer = new TsoHeartbeatTimer();
        tsoHeartbeatTimer.setAlarm(t -> MonitorManager.getInstance()
            .triggerAlarm(MonitorType.DAEMON_POLARX_HEARTBEAT_ERROR, ExceptionUtils.getStackTrace(t)));
        tsoHeartbeatTimer.setMetricsProvider(MetricsResource::getMetricsByKey);
        jobList.add(tsoHeartbeatTimer);

        String clusterRole = DynamicApplicationConfig.getClusterRole();
        log.info("CLUSTER_ROLE : " + clusterRole);
        // 全局Binlog的Slave集群不启动元数据snapshot和清理功能
        if (!ClusterRole.slave.name().equalsIgnoreCase(clusterRole)) {
            // 元数据管理
            jobList.add(new MetaDataMonitor());
            // Table Meta History Watcher
            if (StringUtils.equalsIgnoreCase(getString(META_RECOVER_ROLLBACK_MODE),
                RollbackMode.SNAPSHOT_EXACTLY.name())
                && getBoolean(ENABLE_CDC_META_BUILD_SNAPSHOT)) {
                jobList.add(new TableMetaHistoryWatcher(getString(ConfigKeys.CLUSTER_ID), clusterType,
                    "TableMetaWatcher", getInt(META_BUILD_FULL_SNAPSHOT_CHECK_INTERVAL_SEC)));
            }
            if (DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV)) {
                jobList.add(new LabTestJob(cluster, clusterType, "labTestJob", (int) TimeUnit.SECONDS.toMillis(15)));
            }
        }
    }

    protected void afterInitCommon() {

    }

    @Override
    public void start() {
        beforeInitCommon();
        initCommon();
        afterInitCommon();

        beforeStartCommon();
        startCommon();
        afterStartCommon();
    }

    protected void beforeStartCommon() {

    }

    protected void startCommon() {
        jobList.forEach(IScheduleJob::start);
    }

    protected void afterStartCommon() {

    }

    @Override
    public void stop() {
        beforeStopCommon();
        stopCommon();
        afterStopCommon();
    }

    protected void beforeStopCommon() {

    }

    protected void stopCommon() {
        jobList.forEach(IScheduleJob::stop);
    }

    protected void afterStopCommon() {

    }

    protected abstract String clusterType();
}
