/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RuntimeMode;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.TaskBootStrap;
import com.aliyun.polardbx.binlog.TaskRuntimeConfigProvider;
import com.aliyun.polardbx.binlog.cdc.meta.CdcMetaManager;
import com.aliyun.polardbx.binlog.daemon.cluster.bootstrap.ClusterBootStrapFactory;
import com.aliyun.polardbx.binlog.daemon.cluster.bootstrap.ClusterBootstrapService;
import com.aliyun.polardbx.binlog.daemon.rest.RestServer;
import com.aliyun.polardbx.binlog.daemon.schedule.ColumnarNodeReporter;
import com.aliyun.polardbx.binlog.daemon.schedule.NodeReporter;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.dumper.DumperBootStrap;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.COMMON_PORTS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_HEARTBEAT_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

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

            log.info("Env {} {} {} {}", getString(ConfigKeys.CLUSTER_ID),
                getString(ConfigKeys.INST_ID),
                getString(ConfigKeys.INST_IP),
                getString(COMMON_PORTS));

            // Cluster Parameter
            String clusterId = getString(ConfigKeys.CLUSTER_ID);
            String clusterType = DynamicApplicationConfig.getClusterType();

            // 初始化表
            // 如果是Columnar Daemon，不需要初始化系统表，防止与cdc版本不对齐
            if (!clusterType.equals(ClusterType.COLUMNAR.name())) {
                CdcMetaManager cdcMetaManager = new CdcMetaManager();
                cdcMetaManager.init();
            }

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

            tryStartWorkerModule();
        } catch (Throwable t) {
            log.error("## Something goes wrong when starting up the daemon process:", t);
            Runtime.getRuntime().halt(1);
        }
    }

    private static String buildDumperName() {
        BinlogTaskConfigMapper mapper = SpringContextHolder.getObject(BinlogTaskConfigMapper.class);
        Optional<BinlogTaskConfig> dumperConfig = mapper.selectOne(
            s -> s.where(BinlogTaskConfigDynamicSqlSupport.clusterId, isEqualTo(getString(ConfigKeys.CLUSTER_ID)))
                .and(BinlogTaskConfigDynamicSqlSupport.role, isEqualTo(TaskType.Dumper.name())));
        return dumperConfig.map(BinlogTaskConfig::getTaskName).orElse("Dumper-1");
    }

    public static void tryStartWorkerModule() throws InterruptedException {
        RuntimeMode runtimeMode = RuntimeMode.valueOf(getString(ConfigKeys.RUNTIME_MODE));
        if (runtimeMode == RuntimeMode.LOCAL_SINGLE) {
            waitForTopologyReady(30);
            TaskBootStrap taskBootStrap = new TaskBootStrap();
            taskBootStrap.setTaskRuntimeConfigProvider(new TaskRuntimeConfigProvider("Final"));
            taskBootStrap.boot(new String[] {TASK_NAME + "=Final"});

            String dumperName = buildDumperName();
            DumperBootStrap dumperBootStrap = new DumperBootStrap();
            dumperBootStrap.setTaskRuntimeConfigProvider(new TaskRuntimeConfigProvider(dumperName));
            dumperBootStrap.boot(new String[] {TASK_NAME + "=" + dumperName});
        }
    }

    public static void waitForTopologyReady(long timeoutSeconds) throws InterruptedException {
        long endTimestamp = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(timeoutSeconds);
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
