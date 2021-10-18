/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.domain;

import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;

import java.util.List;

/**
 * Created by ziyang.lb
 **/
public class TaskInfo {
    private Long id;
    private String name;
    private TaskType type;
    private Integer serverPort;
    private String startTSO;
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
