/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.daemon;

import com.aliyun.polardbx.binlog.ClusterTypeEnum;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.cdc.meta.CdcMetaManager;
import com.aliyun.polardbx.binlog.daemon.cluster.ClusterBootStrapFactory;
import com.aliyun.polardbx.binlog.daemon.cluster.ClusterBootstrapService;
import com.aliyun.polardbx.binlog.daemon.rest.RestServer;
import com.aliyun.polardbx.binlog.daemon.schedule.NodeReporter;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;

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

            // 初始化表
            CdcMetaManager cdcMetaManager = new CdcMetaManager();
            cdcMetaManager.init();
            String cluster = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            String clusterType = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_TYPE);
            if (StringUtils.isBlank(clusterType)) {
                // 兼容一下历史版本，如果没有配置，默认为CDC Global Binlog集群
                clusterType = ClusterTypeEnum.BINLOG.name();
            }

            // Node Reporter
            NodeReporter nodeReporter = new NodeReporter(cluster, clusterType, "NodeReport",
                DynamicApplicationConfig.getInt(DAEMON_HEARTBEAT_INTERVAL_MS));
            nodeReporter.start();

            // Cluster bootstrap
            ClusterBootstrapService bootstrapService =
                ClusterBootStrapFactory.getBootstrapService(ClusterTypeEnum.valueOf(clusterType));
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
        } catch (Throwable t) {
            log.error("## Something goes wrong when starting up the daemon process:", t);
            Runtime.getRuntime().halt(1);
        }
    }

}
