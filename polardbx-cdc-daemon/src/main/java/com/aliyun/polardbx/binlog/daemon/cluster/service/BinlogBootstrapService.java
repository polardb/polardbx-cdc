/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.daemon.cluster.service;

import com.aliyun.polardbx.binlog.ClusterTypeEnum;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.cluster.ClusterBootstrapService;
import com.aliyun.polardbx.binlog.daemon.schedule.TaskAliveWatcher;
import com.aliyun.polardbx.binlog.daemon.schedule.TopologyWatcher;
import com.aliyun.polardbx.binlog.heartbeat.TsoHeartbeat;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TASK_WATCH_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TOPOLOGY_WATCH_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL;

@Slf4j
public class BinlogBootstrapService implements ClusterBootstrapService {
    @Override
    public void start() {

        log.info("Init Global Binlog Job!");

        String cluster = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        String clusterType = ClusterTypeEnum.BINLOG.name();
        // TSO心跳定时任务，逻辑binlog stream强依赖该心跳
        TsoHeartbeat tsoHeartbeat =
            new TsoHeartbeat(DynamicApplicationConfig.getInt(DAEMON_TSO_HEARTBEAT_INTERVAL));
        tsoHeartbeat.setAlarm(t -> MonitorManager.getInstance()
            .triggerAlarm(MonitorType.DAEMON_POLARX_HEARTBEAT_ERROR, ExceptionUtils.getStackTrace(t)));
        tsoHeartbeat.start();

        // Topology Watcher
        TopologyWatcher topologyWatcher = new TopologyWatcher(cluster, clusterType, "TopologyWatcher",
            DynamicApplicationConfig.getInt(DAEMON_TOPOLOGY_WATCH_INTERVAL_MS),
            DynamicApplicationConfig.getInt(ConfigKeys.TOPOLOGY_STORAGE_TRIGGER_RELAY_THRESHOLD));
        topologyWatcher.start();

        // TaskAliveWatcher
        TaskAliveWatcher taskAliveWatcher = new TaskAliveWatcher(cluster, clusterType, "TaskKeepAlive",
            DynamicApplicationConfig.getInt(DAEMON_TASK_WATCH_INTERVAL_MS));
        taskAliveWatcher.start();
    }
}
