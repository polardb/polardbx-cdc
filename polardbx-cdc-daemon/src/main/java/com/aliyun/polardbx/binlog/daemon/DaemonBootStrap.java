/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RuntimeMode;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.TaskBootStrap;
import com.aliyun.polardbx.binlog.TaskConfigProvider;
import com.aliyun.polardbx.binlog.cdc.meta.CdcMetaManager;
import com.aliyun.polardbx.binlog.daemon.cluster.bootstrap.ClusterBootStrapFactory;
import com.aliyun.polardbx.binlog.daemon.cluster.bootstrap.ClusterBootstrapService;
import com.aliyun.polardbx.binlog.daemon.rest.RestServer;
import com.aliyun.polardbx.binlog.daemon.schedule.ColumnarNodeReporter;
import com.aliyun.polardbx.binlog.daemon.schedule.NodeReporter;
import com.aliyun.polardbx.binlog.dumper.DumperBootStrap;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.COMMON_PORTS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_HEARTBEAT_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;

/**
 * Created by ShuGuang
 */
@Slf4j
public class DaemonBootStrap {

    public static void main(String[] args) {
        try {
            System.setProperty(TASK_NAME, "Daemon");

            // Spring Context
            final SpringContextBootStrap appContextBootStrap =
                new SpringContextBootStrap("spring/spring.xml");
            appContextBootStrap.boot();

            log.info("Env {} {} {} {}", DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID),
                DynamicApplicationConfig.getString(ConfigKeys.INST_ID),
                DynamicApplicationConfig.getString(ConfigKeys.INST_IP),
                DynamicApplicationConfig.getString(COMMON_PORTS));

            // Cluster Parameter
            String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            String clusterType = DynamicApplicationConfig.getClusterType();

            // 初始化表
            CdcMetaManager cdcMetaManager = new CdcMetaManager();
            cdcMetaManager.init();

            // Node Reporter
            if (!clusterType.equals(ClusterType.COLUMNAR.name())) {
                NodeReporter nodeReporter = new NodeReporter(clusterId, clusterType, "NodeReport",
                    DynamicApplicationConfig.getInt(DAEMON_HEARTBEAT_INTERVAL_MS));
                nodeReporter.start();
            } else {
                ColumnarNodeReporter columnarNodeReporter =
                    new ColumnarNodeReporter(clusterId, clusterType, "ColumnarNodeReport",
                        DynamicApplicationConfig.getInt(DAEMON_HEARTBEAT_INTERVAL_MS));
                columnarNodeReporter.start();
            }

            // Cluster bootstrap
            ClusterBootstrapService bootstrapService =
                ClusterBootStrapFactory.getBootstrapService(ClusterType.valueOf(clusterType));
            if (bootstrapService == null) {
                throw new UnsupportedOperationException("not support cluster type :" + clusterType);
            }
            bootstrapService.start();

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

            RuntimeMode runtimeMode = RuntimeMode.valueOf(DynamicApplicationConfig.getString(ConfigKeys.RUNTIME_MODE));
            if (runtimeMode == RuntimeMode.LOCAL_SINGLE) {
                waitForTopologyReady();
                TaskBootStrap taskBootStrap = new TaskBootStrap();
                taskBootStrap.setTaskConfigProvider(new TaskConfigProvider("Final"));
                taskBootStrap.boot(new String[] {TASK_NAME + "=Final"});

                DumperBootStrap dumperBootStrap = new DumperBootStrap();
                dumperBootStrap.setTaskConfigProvider(new TaskConfigProvider("Dumper-1"));
                dumperBootStrap.boot(new String[] {TASK_NAME + "=Dumper-1"});
            }
        } catch (Throwable t) {
            log.error("## Something goes wrong when starting up the daemon process:", t);
            Runtime.getRuntime().halt(1);
        }
    }

    public static void waitForTopologyReady() throws InterruptedException {
        long endTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(30);
        while (System.currentTimeMillis() < endTimestamp) {
            // wait for cluster config create success
            String preClusterSnapshotStr = SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY);
            ClusterSnapshot preClusterSnapshot =
                JSONObject.parseObject(preClusterSnapshotStr, ClusterSnapshot.class);
            if (preClusterSnapshot != null && preClusterSnapshot.getVersion() > 1) {
                // default version is 1,  when topology rebuild success , snapshot version will increment, so we can start task here
                return;
            }
            //topology rebuild need 5 seconds, so we need wait 5 seconds
            Thread.sleep(5000);
        }
        throw new PolardbxException("wait for topology first build failed!");
    }
}
