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
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.cluster.StorageCountStrategy;
import com.aliyun.polardbx.binlog.daemon.cluster.TopologyService;
import com.aliyun.polardbx.binlog.dao.SystemConfigInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.SystemConfigInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.dao.DataIntegrityViolationException;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;

/**
 * Created by ShuGuang
 */
@Slf4j
public class TopologyWatcher extends AbstractBinlogTimerTask {
    private final SystemConfigInfoMapper systemConfigInfoMapper =
        SpringContextHolder.getObject(SystemConfigInfoMapper.class);
    private volatile boolean initFlag;

    public TopologyWatcher(String cluster, String clusterType, String name, int interval) {
        super(cluster, clusterType, name, interval);
    }

    @Override
    public void exec() {
        StorageCountStrategy storageCountStrategy = new StorageCountStrategy(clusterId);
        TopologyService topologyService = new TopologyService(storageCountStrategy, clusterId);
        try {
            if (!RuntimeLeaderElector.isDaemonLeader()) {
                if (log.isDebugEnabled()) {
                    log.debug("current daemon is not a leader, skip the cluster's topology project!");
                }
                return;
            }
            tryInit();
            topologyService.tryBuild();
        } catch (Throwable th) {
            log.error("topologyService.project fail {} {} {}", clusterId, name, interval, th);
            MonitorManager.getInstance()
                .triggerAlarm(MonitorType.DAEMON_TOPOLOGY_WATCHER_ERROR, ExceptionUtils.getStackTrace(th));
            throw new PolardbxException("topologyService.project fail", th);
        }
    }

    private void tryInit() {
        if (!initFlag) {
            try {
                ClusterSnapshot clusterSnapshot = new ClusterSnapshot(1, null, null,
                    null, null, null, null);
                SystemConfigInfo info = new SystemConfigInfo();
                info.setConfigKey(CLUSTER_SNAPSHOT_VERSION_KEY);
                info.setConfigValue(JSONObject.toJSONString(clusterSnapshot));
                systemConfigInfoMapper.insert(info);
            } catch (DataIntegrityViolationException e) {
                log.info("System config for {} has already exist, init skipped.", CLUSTER_SNAPSHOT_VERSION_KEY);
            }
            initFlag = true;
        }
    }
}
