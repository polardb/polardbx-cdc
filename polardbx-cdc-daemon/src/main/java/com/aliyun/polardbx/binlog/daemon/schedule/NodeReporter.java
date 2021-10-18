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

package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.NodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.NodeRole;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.task.AbstractBinlogTimerTask;
import com.google.common.collect.Maps;
import com.google.gson.GsonBuilder;
import lombok.extern.slf4j.Slf4j;
import org.mybatis.dynamic.sql.SqlBuilder;

import java.util.Date;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.COMMON_PORTS;

/**
 * Creatd by ziyang.lb
 */
@Slf4j
public class NodeReporter extends AbstractBinlogTimerTask {

    private final String instId;
    private final String portStr;

    public NodeReporter(String cluster, String name, int interval) {
        super(cluster, name, interval);
        instId = DynamicApplicationConfig.getString(ConfigKeys.INST_ID);
        portStr = buildPortStr();
    }

    @Override
    public void exec() {
        boolean isLeader = RuntimeLeaderElector.isDaemonLeader();
        String role = isLeader ? NodeRole.MASTER.getName() : NodeRole.SLAVE.getName();

        NodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(NodeInfoMapper.class);
        NodeInfo nodeInfo = new NodeInfo();
        Optional<NodeInfo> node = nodeInfoMapper.selectOne(
            s -> s.where(NodeInfoDynamicSqlSupport.containerId,
                SqlBuilder.isEqualTo(instId)));
        Date now = new Date();
        if (node.isPresent()) {
            NodeInfo record = node.get();
            record.setGmtHeartbeat(now);
            record.setRole(role);
            nodeInfoMapper.updateByPrimaryKeySelective(record);
            return;
        }

        nodeInfo.setRole(role);
        nodeInfo.setClusterId(cluster);
        nodeInfo.setContainerId(DynamicApplicationConfig.getString(ConfigKeys.INST_ID));
        nodeInfo.setIp(DynamicApplicationConfig.getString(ConfigKeys.INST_IP));
        nodeInfo.setDaemonPort(DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT));
        nodeInfo.setAvailablePorts(portStr);
        nodeInfo.setCore(DynamicApplicationConfig.getLong(ConfigKeys.CPU_CORES));
        nodeInfo.setMem(DynamicApplicationConfig.getLong(ConfigKeys.MEM_SIZE));
        nodeInfo.setStatus(0);
        nodeInfo.setGmtCreated(now);
        nodeInfo.setGmtHeartbeat(now);
        nodeInfo.setGmtModified(now);
        nodeInfoMapper.insertSelective(nodeInfo);
    }

    private String buildPortStr() {
        Map<String, String> portsConfig =
            new GsonBuilder().create().fromJson(DynamicApplicationConfig.getString(COMMON_PORTS), Map.class);
        return Maps.filterKeys(portsConfig, s -> s.startsWith("cdc")).values().stream().collect(
            Collectors.joining(","));
    }
}
