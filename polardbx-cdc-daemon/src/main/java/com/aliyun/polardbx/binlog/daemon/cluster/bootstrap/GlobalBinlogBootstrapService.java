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
import com.aliyun.polardbx.binlog.daemon.schedule.BinlogConsumerMonitor;
import com.aliyun.polardbx.binlog.daemon.schedule.DnHealthChecker;
import com.aliyun.polardbx.binlog.daemon.schedule.RplLeaderJob;
import com.aliyun.polardbx.binlog.daemon.schedule.RplWorkerJob;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.ALARM_CHECK_CONSUMER_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_DN_HEALTH_CHECKER_INTERVAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_REPLICA_IN_BINLOG_CLUSTER_ENABLE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

@Slf4j
public class GlobalBinlogBootstrapService extends AbstractBinlogBootstrapService {

    private BinlogConsumerMonitor binlogConsumerMonitor;
    private RplLeaderJob rplLeaderJob;
    private RplWorkerJob rplWorkerJob;
    private DnHealthChecker dnHealthChecker;

    @Override
    protected void beforeInitCommon() {
        log.info("Init Global Binlog Job!");
    }

    @Override
    protected void afterInitCommon() {
        binlogConsumerMonitor = new BinlogConsumerMonitor(clusterId(), clusterType(), "ConsumerChecker",
            DynamicApplicationConfig.getInt(ALARM_CHECK_CONSUMER_INTERVAL_MS));

        dnHealthChecker = new DnHealthChecker(clusterId(), clusterType(), "dn-health-checker",
            TimeUnit.SECONDS.toMillis(DynamicApplicationConfig.getInt(DAEMON_DN_HEALTH_CHECKER_INTERVAL)));

        if (DynamicApplicationConfig.getBoolean(DAEMON_WATCH_REPLICA_IN_BINLOG_CLUSTER_ENABLE)) {
            // 定期抢 Leader，如抢到 Leader，则负责：调度所有 Rpl 状态机，分发任务到各个 worker
            rplLeaderJob = new RplLeaderJob(clusterId(), clusterType(), "RplLeaderJob", 1 * 5 * 1000);
            // 定期检测本地需要启动哪些 Rpl 任务，停止哪些 Rpl 任务
            rplWorkerJob = new RplWorkerJob(clusterId(), clusterType(), "RplWorkerJob", 1 * 5 * 1000);
        }
    }

    @Override
    protected void afterStartCommon() {
        binlogConsumerMonitor.start();
        if (rplLeaderJob != null) {
            rplLeaderJob.start();
        }
        if (rplWorkerJob != null) {
            rplWorkerJob.start();
        }
        if (dnHealthChecker != null) {
            dnHealthChecker.start();
        }
    }

    @Override
    protected void afterStopCommon() {
        if (binlogConsumerMonitor != null) {
            binlogConsumerMonitor.stop();
        }
        if (rplLeaderJob != null) {
            rplLeaderJob.stop();
        }
        if (rplWorkerJob != null) {
            rplWorkerJob.stop();
        }
        if (dnHealthChecker != null) {
            dnHealthChecker.stop();
        }
    }

    @Override
    protected String clusterType() {
        return ClusterType.BINLOG.name();
    }

    private String clusterId() {
        return getString(ConfigKeys.CLUSTER_ID);
    }
}
