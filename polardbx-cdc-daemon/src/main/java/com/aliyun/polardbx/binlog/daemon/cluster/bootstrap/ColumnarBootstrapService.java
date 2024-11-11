/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.bootstrap;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.columnar.ColumnarMonitor;
import com.aliyun.polardbx.binlog.daemon.schedule.ColumnarWatcher;
import com.aliyun.polardbx.binlog.daemon.schedule.TopologyWatcher;
import com.aliyun.polardbx.binlog.columnar.metrics.MetricsManager;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import lombok.extern.slf4j.Slf4j;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_CLUSTER_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;

@Slf4j
public class ColumnarBootstrapService implements ClusterBootstrapService {
    protected ColumnarWatcher columnarWatcher;
    protected TopologyWatcher topologyWatcher;
    protected ColumnarMonitor columnarMonitor;

    private final MetricsManager metricsManager;

    public ColumnarBootstrapService() {
        this.metricsManager = new MetricsManager();
        this.columnarMonitor = new ColumnarMonitor();
    }

    @Override
    public void start() {
        log.info("Init ColumnarLauncher");

        String clusterId = DynamicApplicationConfig.getString(CLUSTER_ID);
        String clusterType = DynamicApplicationConfig.getClusterType();
        // 定期检测本地需要启动哪些列存进程
        // Topology Watcher
        topologyWatcher = new TopologyWatcher(clusterId, clusterType, "TopologyWatcher",
            getInt(DAEMON_WATCH_CLUSTER_INTERVAL_MS));
        topologyWatcher.start();

        columnarWatcher = new ColumnarWatcher(clusterId, clusterType, "ColumnarWatcher", 1 * 5 * 1000);
        columnarWatcher.start();

        metricsManager.start();
        MonitorManager.getInstance().startup();

        columnarMonitor.start();
    }

    @Override
    public void stop() {
        log.info("Stop ColumnarLauncher");

        columnarWatcher.stop();
        metricsManager.stop();
        MonitorManager.getInstance().shutdown();
    }
}
