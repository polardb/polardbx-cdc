/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.entities;

import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;

import java.util.List;

/**
 * Created by ziyang.lb
 */
public class ServerInfo {
    private String clusterId;
    private String polarxInstanceId;
    private List<NodeInfo> nodeInfo;
    private List<BinlogTaskInfo> taskInfo;
    private List<DumperInfo> dumperInfo;
    private List<BinlogTaskConfig> topologyConfig;

    public String getClusterId() {
        return clusterId;
    }

    public void setClusterId(String clusterId) {
        this.clusterId = clusterId;
    }

    public String getPolarxInstanceId() {
        return polarxInstanceId;
    }

    public void setPolarxInstanceId(String polarxInstanceId) {
        this.polarxInstanceId = polarxInstanceId;
    }

    public List<NodeInfo> getNodeInfo() {
        return nodeInfo;
    }

    public void setNodeInfo(List<NodeInfo> nodeInfo) {
        this.nodeInfo = nodeInfo;
    }

    public List<BinlogTaskInfo> getTaskInfo() {
        return taskInfo;
    }

    public void setTaskInfo(List<BinlogTaskInfo> taskInfo) {
        this.taskInfo = taskInfo;
    }

    public List<DumperInfo> getDumperInfo() {
        return dumperInfo;
    }

    public void setDumperInfo(List<DumperInfo> dumperInfo) {
        this.dumperInfo = dumperInfo;
    }

    public List<BinlogTaskConfig> getTopologyConfig() {
        return topologyConfig;
    }

    public void setTopologyConfig(List<BinlogTaskConfig> topologyConfig) {
        this.topologyConfig = topologyConfig;
    }
}
