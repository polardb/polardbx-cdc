/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * @author yudong
 * @since 2023/1/11
 * <p>
 * 负责将本地的binlog上传到远端存储
 */
@Slf4j
public class BinlogBackupManager {
    private final StreamContext streamContext;
    private final Map<String, MetricsObserver> metrics;
    private BinlogUploadManager binlogUploadManager;

    public BinlogBackupManager(StreamContext context, Map<String, MetricsObserver> metrics) {
        this.streamContext = context;
        this.metrics = metrics;
    }

    public void start() {
        log.info("binlog backup manager start");
        if (needStart()) {
            binlogUploadManager = new BinlogUploadManager(streamContext, metrics);
            binlogUploadManager.start();
        }
    }

    public void stop() {
        log.info("binlog backup manager stop");
        if (binlogUploadManager != null) {
            binlogUploadManager.stop();
        }
    }

    public boolean needStart() {
        if (!RemoteBinlogProxy.getInstance().isBackupOn()) {
            return false;
        }

        return RuntimeLeaderElector.isDumperMasterOrX(streamContext.getVersion(), streamContext.getTaskType(),
            streamContext.getTaskName());
    }
}
