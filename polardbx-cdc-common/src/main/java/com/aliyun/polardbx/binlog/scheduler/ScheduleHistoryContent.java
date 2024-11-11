/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler;

import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;

import java.util.List;

/**
 * Created by ziyang.lb
 **/
public class ScheduleHistoryContent {
    private ExecutionSnapshot executionSnapshot;
    private List<BinlogTaskConfig> taskConfigs;
    private ClusterSnapshot clusterSnapshot;

    public ScheduleHistoryContent() {
    }

    public ScheduleHistoryContent(ExecutionSnapshot executionSnapshot,
                                  List<BinlogTaskConfig> taskConfigs,
                                  ClusterSnapshot clusterSnapshot) {
        this.executionSnapshot = executionSnapshot;
        this.taskConfigs = taskConfigs;
        this.clusterSnapshot = clusterSnapshot;
    }

    public ExecutionSnapshot getExecutionSnapshot() {
        return executionSnapshot;
    }

    public void setExecutionSnapshot(ExecutionSnapshot executionSnapshot) {
        this.executionSnapshot = executionSnapshot;
    }

    public List<BinlogTaskConfig> getTaskConfigs() {
        return taskConfigs;
    }

    public void setTaskConfigs(List<BinlogTaskConfig> taskConfigs) {
        this.taskConfigs = taskConfigs;
    }

    public ClusterSnapshot getClusterSnapshot() {
        return clusterSnapshot;
    }

    public void setClusterSnapshot(ClusterSnapshot clusterSnapshot) {
        this.clusterSnapshot = clusterSnapshot;
    }
}
