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
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
public class TableMetaHistoryWatcher extends AbstractBinlogTimerTask {

    private static final String TABLE_META_REBUILD_LOCK = "TABLE-META-SNAPSHOT-BUILDER-LOCK";

    private final TableMetaHistoryDbHelper tableMetaHistoryDbHelper =
        SpringContextHolder.getObject(TableMetaHistoryDbHelper.class);

    public TableMetaHistoryWatcher(String clusterId, String clusterType, String name, int interval) {
        super(clusterId, clusterType, name, (int) TimeUnit.SECONDS.toMillis(interval));
    }

    @Override
    public void exec() {
        try {
            if (!RuntimeLeaderElector.isDaemonLeader()) {
                if (log.isDebugEnabled()) {
                    log.debug("current daemon is not a leader, skip to build table mata snap and clean!");
                }
                return;
            }
            if (RuntimeLeaderElector.isLeader(TABLE_META_REBUILD_LOCK)) {
                tableMetaHistoryDbHelper.tryClean();
            }
        } catch (Throwable th) {
            log.error("tableMetaHistoryWatcher.process fail {} {} {}", clusterId, name, interval, th);
            MonitorManager.getInstance()
                .triggerAlarm(MonitorType.DAEMON_TOPOLOGY_WATCHER_ERROR, ExceptionUtils.getStackTrace(th));
            throw new PolardbxException("tableMetaHistoryWatcher.process fail", th);
        }
    }

}
