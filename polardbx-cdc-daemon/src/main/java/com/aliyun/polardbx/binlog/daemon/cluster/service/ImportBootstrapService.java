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
package com.aliyun.polardbx.binlog.daemon.cluster.service;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.cluster.ClusterBootstrapService;
import com.aliyun.polardbx.binlog.daemon.schedule.RplLeaderJob;
import com.aliyun.polardbx.binlog.daemon.schedule.RplWorkerJob;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ImportBootstrapService implements ClusterBootstrapService {

    @Override
    public void start() {
        log.info("Init RplLeaderJob and RplWorkerJob");

        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        String clusterType = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_TYPE);
        // 定期抢 Leader，如抢到 Leader，则负责：调度所有 Rpl 状态机，分发任务到各个 worker
        new RplLeaderJob(clusterId, clusterType, "RplLeaderJob", 1 * 5 * 1000).start();
        // 定期检测本地需要启动哪些 Rpl 任务，停止哪些 Rpl 任务
        new RplWorkerJob(clusterId, clusterType, "RplWorkerJob", 1 * 5 * 1000).start();
    }
}
