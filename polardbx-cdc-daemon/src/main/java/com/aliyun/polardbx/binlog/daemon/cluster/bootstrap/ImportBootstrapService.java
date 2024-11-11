/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.bootstrap;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.schedule.BinlogConsumerMonitor;
import com.aliyun.polardbx.binlog.daemon.schedule.RplLeaderJob;
import com.aliyun.polardbx.binlog.daemon.schedule.RplWorkerJob;
import lombok.extern.slf4j.Slf4j;

import static com.aliyun.polardbx.binlog.ConfigKeys.ALARM_CHECK_CONSUMER_INTERVAL_MS;

@Slf4j
public class ImportBootstrapService implements ClusterBootstrapService {

    @Override
    public void start() {
        log.info("Init RplLeaderJob and RplWorkerJob");

        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        String clusterType = DynamicApplicationConfig.getClusterType();
        // 定期抢 Leader，如抢到 Leader，则负责：调度所有 Rpl 状态机，分发任务到各个 worker
        new RplLeaderJob(clusterId, clusterType, "RplLeaderJob", 1 * 5 * 1000).start();
        // 定期检测本地需要启动哪些 Rpl 任务，停止哪些 Rpl 任务
        new RplWorkerJob(clusterId, clusterType, "RplWorkerJob", 1 * 5 * 1000).start();
    }

    @Override
    public void stop() {

    }
}
