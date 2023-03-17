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

import com.aliyun.polardbx.binlog.ClusterTypeEnum;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.cluster.ClusterBootstrapService;
import com.aliyun.polardbx.binlog.daemon.rest.resources.MetricsResource;
import com.aliyun.polardbx.binlog.daemon.schedule.ConsumerChecker;
import com.aliyun.polardbx.binlog.daemon.schedule.RplLeaderJob;
import com.aliyun.polardbx.binlog.daemon.schedule.RplWorkerJob;
import lombok.extern.slf4j.Slf4j;

import static com.aliyun.polardbx.binlog.ConfigKeys.ALARM_CHECK_CONSUMER_INTERVAL_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_CLUSTER_BINLOG_SUPPORT_RUN_REPLICA;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

@Slf4j
public class GlobalBinlogBootstrapService extends BaseBinlogBootstrapService implements ClusterBootstrapService {

    private ConsumerChecker consumerChecker;
    private RplLeaderJob rplLeaderJob;
    private RplWorkerJob rplWorkerJob;

    @Override
    protected void beforeInitCommon() {
        log.info("Init Global Binlog Job!");
    }

    @Override
    protected void afterInitCommon() {
        tsoHeartbeat.setMetricsProvider(MetricsResource::getMetricsByKey);
        consumerChecker = new ConsumerChecker(clusterId(), clusterType(), "ConsumerChecker",
            DynamicApplicationConfig.getInt(ALARM_CHECK_CONSUMER_INTERVAL_MS));

        if (DynamicApplicationConfig.getBoolean(DAEMON_CLUSTER_BINLOG_SUPPORT_RUN_REPLICA)) {
            // 定期抢 Leader，如抢到 Leader，则负责：调度所有 Rpl 状态机，分发任务到各个 worker
            rplLeaderJob = new RplLeaderJob(clusterId(), clusterType(), "RplLeaderJob", 1 * 5 * 1000);
            // 定期检测本地需要启动哪些 Rpl 任务，停止哪些 Rpl 任务
            rplWorkerJob = new RplWorkerJob(clusterId(), clusterType(), "RplWorkerJob", 1 * 5 * 1000);
        }
    }

    @Override
    protected void afterStartCommon() {
        consumerChecker.start();
        if (rplLeaderJob != null) {
            rplLeaderJob.start();
        }
        if (rplWorkerJob != null) {
            rplWorkerJob.start();
        }
    }

    @Override
    protected void afterStopCommon() {
        consumerChecker.stop();
        if (rplLeaderJob != null) {
            rplLeaderJob.stop();
        }
        if (rplWorkerJob != null) {
            rplWorkerJob.stop();
        }
    }

    @Override
    protected String clusterType() {
        return ClusterTypeEnum.BINLOG.name();
    }

    private String clusterId() {
        return getString(ConfigKeys.CLUSTER_ID);
    }
}
