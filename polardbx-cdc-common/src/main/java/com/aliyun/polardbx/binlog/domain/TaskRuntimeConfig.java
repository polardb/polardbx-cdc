/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;

import java.util.List;

/**
 * Created by ziyang.lb
 **/
public class TaskRuntimeConfig {
    private Long id;
    private String name;
    private TaskType type;
    private Integer serverPort;
    private String startTSO;
    private boolean forceCompleteHbWindow;
    private List<MergeSourceInfo> mergeSourceInfos;
    private BinlogTaskConfig binlogTaskConfig;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public TaskType getType() {
        return type;
    }

    public void setType(TaskType type) {
        this.type = type;
    }

    public Integer getServerPort() {
        return serverPort;
    }

    public void setServerPort(Integer serverPort) {
        this.serverPort = serverPort;
    }

    public String getStartTSO() {
        return startTSO;
    }

    public void setStartTSO(String startTSO) {
        this.startTSO = startTSO;
    }

    public boolean isForceCompleteHbWindow() {
        return forceCompleteHbWindow;
    }

    public void setForceCompleteHbWindow(boolean forceCompleteHbWindow) {
        this.forceCompleteHbWindow = forceCompleteHbWindow;
    }

    public List<MergeSourceInfo> getMergeSourceInfos() {
        return mergeSourceInfos;
    }

    public void setMergeSourceInfos(List<MergeSourceInfo> mergeSourceInfos) {
        this.mergeSourceInfos = mergeSourceInfos;
    }

    public BinlogTaskConfig getBinlogTaskConfig() {
        return binlogTaskConfig;
    }

    public void setBinlogTaskConfig(BinlogTaskConfig binlogTaskConfig) {
        this.binlogTaskConfig = binlogTaskConfig;
    }

    public ExecutionConfig getExecutionConfig() {
        return JSONObject.parseObject(binlogTaskConfig.getConfig(), ExecutionConfig.class);
    }

    @Override
    public String toString() {
        return "TaskInfo{" +
            "name='" + name + '\'' +
            ", type=" + type +
            ", serverPort=" + serverPort +
            ", startTSO='" + startTSO + '\'' +
            ", mergeSourceInfos=" + mergeSourceInfos +
            '}';
    }
}
