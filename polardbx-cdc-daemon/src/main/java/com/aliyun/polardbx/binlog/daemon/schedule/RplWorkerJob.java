/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.taskmeta.TaskDistributor;
import org.slf4j.Logger;

/**
 * @author shicai.xsc 2021/1/15 14:20
 * @since 5.0.0.0
 */
public class RplWorkerJob extends AbstractBinlogTimerTask {

    private static Logger metaLogger = LogUtil.getMetaLogger();

    public RplWorkerJob(String cluster, String clusterType, String name, int interval) {
        super(cluster, clusterType, name, interval);
    }

    @Override
    public void exec() {
        refresh();
    }

    public void refresh() {
        try {
            TaskDistributor.checkAndRunLocalTasks(clusterId);
        } catch (Throwable e) {
            metaLogger.error("RplWorkerJob fail {} {} {}", clusterId, name, interval, e);
            throw new PolardbxException("RplWorkerJob fail", e);
        }
    }
}
