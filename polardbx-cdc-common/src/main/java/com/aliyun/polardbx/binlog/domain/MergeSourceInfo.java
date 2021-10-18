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

import lombok.ToString;

/**
 * Created by ziyang.lb
 **/
@ToString
public class MergeSourceInfo {

    private String id;
    private String taskName;
    private MergeSourceType type;
    private Integer queueSize = 1024;
    private BinlogParameter binlogParameter;
    // for test，发布环境直接使用binlog_task_config中的配置
    private RpcParameter rpcParameter;
    private MockParameter mockParameter;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public MergeSourceType getType() {
        return type;
    }

    public void setType(MergeSourceType type) {
        this.type = type;
    }

    public Integer getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(Integer queueSize) {
        this.queueSize = queueSize;
    }

    public BinlogParameter getBinlogParameter() {
        return binlogParameter;
    }

    public void setBinlogParameter(BinlogParameter binlogParameter) {
        this.binlogParameter = binlogParameter;
    }

    public RpcParameter getRpcParameter() {
        return rpcParameter;
    }

    public void setRpcParameter(RpcParameter rpcParameter) {
        this.rpcParameter = rpcParameter;
    }

    public MockParameter getMockParameter() {
        return mockParameter;
    }

    public void setMockParameter(MockParameter mockParameter) {
        this.mockParameter = mockParameter;
    }
}
