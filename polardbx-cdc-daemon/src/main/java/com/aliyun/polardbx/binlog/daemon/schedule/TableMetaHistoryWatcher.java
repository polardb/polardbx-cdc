/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.CnInstConfigUtil;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.CnInstConfigKeys.ENABLE_CDC_META_BUILD_SNAPSHOT;

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

            if (CnInstConfigUtil.getBoolean(ENABLE_CDC_META_BUILD_SNAPSHOT) &&
                RuntimeLeaderElector.isLeader(
                    TABLE_META_REBUILD_LOCK)) {
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
