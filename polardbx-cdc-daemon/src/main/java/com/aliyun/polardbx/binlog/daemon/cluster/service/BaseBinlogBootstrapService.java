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
package com.aliyun.polardbx.binlog.daemon.cluster.service;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.cdc.meta.RollbackMode;
import com.aliyun.polardbx.binlog.daemon.cluster.ClusterBootstrapService;
import com.aliyun.polardbx.binlog.daemon.schedule.HistoryMonitor;
import com.aliyun.polardbx.binlog.daemon.schedule.TableMetaHistoryWatcher;
import com.aliyun.polardbx.binlog.daemon.schedule.TaskAliveWatcher;
import com.aliyun.polardbx.binlog.daemon.schedule.TopologyWatcher;
import com.aliyun.polardbx.binlog.heartbeat.TsoHeartbeat;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TASK_WATCH_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TOPOLOGY_WATCH_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.ENABLE_CDC_META_BUILD_SNAPSHOT;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_DDL_RECORD_TABLE_META_CHECK_INTERVAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_ROLLBACK_MODE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * created by ziyang.lb
 **/
@Slf4j
public abstract class BaseBinlogBootstrapService implements ClusterBootstrapService {
    protected TopologyWatcher topologyWatcher;
    protected TaskAliveWatcher taskAliveWatcher;
    protected TsoHeartbeat tsoHeartbeat;
    protected TableMetaHistoryWatcher tableMetaHistoryWatcher;
    protected HistoryMonitor historyMonitor;

    protected void beforeInitCommon() {

    }

    protected void initCommon() {
        String cluster = getString(ConfigKeys.CLUSTER_ID);
        String clusterType = clusterType();

        // Topology Watcher
        topologyWatcher = new TopologyWatcher(cluster, clusterType, "TopologyWatcher",
            getInt(DAEMON_TOPOLOGY_WATCH_INTERVAL_MS));

        // TaskAliveWatcher
        taskAliveWatcher = new TaskAliveWatcher(cluster, clusterType, "TaskKeepAlive",
            getInt(DAEMON_TASK_WATCH_INTERVAL_MS));

        // TSO心跳定时任务，逻辑binlog stream强依赖该心跳
        tsoHeartbeat = new TsoHeartbeat();
        tsoHeartbeat.setAlarm(t -> MonitorManager.getInstance()
            .triggerAlarm(MonitorType.DAEMON_POLARX_HEARTBEAT_ERROR, ExceptionUtils.getStackTrace(t)));

        // history monitor
        historyMonitor = new HistoryMonitor();

        // Table Meta History Watcher
        if (StringUtils.equalsIgnoreCase(getString(META_ROLLBACK_MODE), RollbackMode.SNAPSHOT_EXACTLY.name())
            && getBoolean(ENABLE_CDC_META_BUILD_SNAPSHOT)) {
            tableMetaHistoryWatcher = new TableMetaHistoryWatcher(getString(ConfigKeys.CLUSTER_ID), clusterType,
                "TableMetaWatcher", getInt(META_DDL_RECORD_TABLE_META_CHECK_INTERVAL));
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
        topologyWatcher.start();
        taskAliveWatcher.start();
        tsoHeartbeat.start();
        historyMonitor.start();
        if (tableMetaHistoryWatcher != null) {
            tableMetaHistoryWatcher.start();
        }
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
        topologyWatcher.stop();
        taskAliveWatcher.stop();
        tsoHeartbeat.stop();
        historyMonitor.stop();
        if (tableMetaHistoryWatcher != null) {
            tableMetaHistoryWatcher.stop();
        }
    }

    protected void afterStopCommon() {

    }

    protected abstract String clusterType();
}
