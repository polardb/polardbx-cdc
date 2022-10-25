/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.polardbx.binlog.daemon.rest.entities;

import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.domain.po.RelayFinalTaskInfo;

import java.util.List;

/**
 * Created by ziyang.lb
 */
public class ServerInfo {
    private String clusterId;
    private String polarxInstanceId;
    private List<NodeInfo> nodeInfo;
    private List<RelayFinalTaskInfo> taskInfo;
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

    public List<RelayFinalTaskInfo> getTaskInfo() {
        return taskInfo;
    }

    public void setTaskInfo(List<RelayFinalTaskInfo> taskInfo) {
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
