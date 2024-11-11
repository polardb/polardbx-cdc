/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import com.aliyun.polardbx.rpl.common.LogUtil;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMManager;
import com.aliyun.polardbx.rpl.taskmeta.DynamicEndpointWatcher;
import com.aliyun.polardbx.rpl.taskmeta.MetaManagerTranProxy;
import org.slf4j.Logger;

/**
 * @author shicai.xsc 2021/1/15 14:19
 * @since 5.0.0.0
 */
public class RplLeaderJob extends AbstractBinlogTimerTask {

    private static Logger metaLogger = LogUtil.getMetaLogger();
    private static final MetaManagerTranProxy TRANSACTION_MANAGER =
        SpringContextHolder.getObject(MetaManagerTranProxy.class);

    public RplLeaderJob(String cluster, String clusterType, String name, int interval) {
        super(cluster, clusterType, name, interval);
    }

    @Override
    public void exec() {
        try {
            metaLogger.info("try to elect leader");
            if (!RuntimeLeaderElector.isDaemonLeader()) {
                metaLogger.info("NOT a leader");
                return;
            }

            FSMManager.update();
            TRANSACTION_MANAGER.distributeTasks();
            DynamicEndpointWatcher.checkAvailableEndpoint();
        } catch (Throwable th) {
            metaLogger.error("RplLeaderJob fail {} {} {}", clusterId, name, interval, th);
            throw new PolardbxException("RplLeaderJob fail", th);
        }
    }
}
