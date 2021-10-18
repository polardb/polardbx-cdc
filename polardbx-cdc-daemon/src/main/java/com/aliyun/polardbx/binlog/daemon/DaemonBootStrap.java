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

package com.aliyun.polardbx.binlog.daemon;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.cdc.meta.CdcMetaManager;
import com.aliyun.polardbx.binlog.daemon.rest.RestServer;
import com.aliyun.polardbx.binlog.daemon.schedule.NodeReporter;
import com.aliyun.polardbx.binlog.daemon.schedule.TaskAliveWatcher;
import com.aliyun.polardbx.binlog.daemon.schedule.TopologyWatcher;
import com.aliyun.polardbx.binlog.heartbeat.TsoHeartbeat;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import static com.aliyun.polardbx.binlog.ConfigKeys.COMMON_PORTS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_HEARTBEAT_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TASK_WATCH_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TOPOLOGY_WATCH_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_INTERVAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;

/**
 * Created by ShuGuang
 */
@Slf4j
public class DaemonBootStrap {

    public static void main(String[] args) {
        try {
            System.setProperty(TASK_NAME, "DAEMON");

            // Spring Context
            final SpringContextBootStrap appContextBootStrap =
                new SpringContextBootStrap("spring/spring.xml");
            appContextBootStrap.boot();

            log.info("Env {} {} {} {}", DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID),
                DynamicApplicationConfig.getString(ConfigKeys.INST_ID),
                DynamicApplicationConfig.getString(ConfigKeys.INST_IP),
                DynamicApplicationConfig.getString(COMMON_PORTS));

            // 初始化表
            CdcMetaManager cdcMetaManager = new CdcMetaManager();
            cdcMetaManager.init();

            // TSO心跳定时任务，逻辑binlog stream强依赖该心跳
            TsoHeartbeat tsoHeartbeat =
                new TsoHeartbeat(DynamicApplicationConfig.getInt(DAEMON_TSO_HEARTBEAT_INTERVAL));
            tsoHeartbeat.setAlarm(t -> MonitorManager.getInstance()
                .triggerAlarm(MonitorType.DAEMON_POLARX_HEARTBEAT_ERROR, ExceptionUtils.getStackTrace(t)));
            tsoHeartbeat.start();

            // Node Reporter
            String cluster = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            NodeReporter nodeReporter = new NodeReporter(cluster, "NodeReport",
                DynamicApplicationConfig.getInt(DAEMON_HEARTBEAT_INTERVAL_MS));
            nodeReporter.start();

            // Topology Watcher
            TopologyWatcher topologyWatcher = new TopologyWatcher(cluster, "TopologyWatcher",
                DynamicApplicationConfig.getInt(DAEMON_TOPOLOGY_WATCH_INTERVAL_MS),
                DynamicApplicationConfig.getInt(ConfigKeys.TOPOLOGY_STORAGE_TRIGGER_RELAY_THRESHOLD));
            topologyWatcher.start();

            // TaskAliveWatcher
            TaskAliveWatcher taskAliveWatcher = new TaskAliveWatcher(cluster, "TaskKeepAlive",
                DynamicApplicationConfig.getInt(DAEMON_TASK_WATCH_INTERVAL_MS));
            taskAliveWatcher.start();

            // RestServer
            RestServer restServer = new RestServer();
            restServer.start();

            MonitorManager.getInstance().startup();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    log.info("## stop the daemon server.");
                    restServer.stop();
                    MonitorManager.getInstance().shutdown();
                } catch (Throwable e) {
                    log.warn("##something goes wrong when stopping the daemon server.", e);
                } finally {
                    log.info("## daemon server is down.");
                }
            }));

        } catch (Throwable t) {
            log.error("## Something goes wrong when starting up the daemon process:", t);
            Runtime.getRuntime().halt(1);
        }
    }
}
