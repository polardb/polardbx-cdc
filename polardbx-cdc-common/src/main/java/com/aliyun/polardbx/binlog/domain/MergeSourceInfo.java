/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

import lombok.ToString;

/**
 * Created by ziyang.lb
 **/
@ToString
public class MergeSourceInfo {

    private String id;
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
