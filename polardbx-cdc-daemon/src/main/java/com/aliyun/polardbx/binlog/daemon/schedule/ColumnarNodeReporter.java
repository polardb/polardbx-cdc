/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.ColumnarNodeInfoDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.ColumnarNodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.domain.NodeRole;
import com.aliyun.polardbx.binlog.domain.po.ColumnarNodeInfo;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.enums.ClusterType;
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

import static com.aliyun.polardbx.binlog.ConfigKeys.COLUMNAR_PORT;

/**
 * @author wenki
 */
@Slf4j
public class ColumnarNodeReporter extends AbstractBinlogTimerTask {

    private final String instId;
    private final String portStr;

    public ColumnarNodeReporter(String cluster, String clusterType, String name, int interval) {
        super(cluster, clusterType, name, interval);
        instId = DynamicApplicationConfig.getString(ConfigKeys.INST_ID);
        portStr = DynamicApplicationConfig.getString(COLUMNAR_PORT);
    }

    @Override
    public void exec() {
        boolean isLeader = RuntimeLeaderElector.isDaemonLeader();
        String role = isLeader ? NodeRole.MASTER.getName() : NodeRole.SLAVE.getName();

        ColumnarNodeInfoMapper nodeInfoMapper = SpringContextHolder.getObject(ColumnarNodeInfoMapper.class);
        ColumnarNodeInfo nodeInfo = new ColumnarNodeInfo();
        Optional<ColumnarNodeInfo> node = nodeInfoMapper.selectOne(
            s -> s.where(ColumnarNodeInfoDynamicSqlSupport.containerId,
                SqlBuilder.isEqualTo(instId)));

        if (node.isPresent()) {
            int result = nodeInfoMapper.updateNodeHeartbeat(node.get().getId(),
                role,
                clusterType
            );
            if (result == 0) {
                log.warn("node info has removed from meta db.");
            }

            return;
        }

        ClusterType clusterType = ClusterType.valueOf(this.clusterType);
        nodeInfo.setRole(role);
        nodeInfo.setClusterId(clusterId);
        nodeInfo.setClusterType(clusterType.name());
        nodeInfo.setContainerId(DynamicApplicationConfig.getString(ConfigKeys.INST_ID));
        nodeInfo.setIp(DynamicApplicationConfig.getString(ConfigKeys.INST_IP));
        nodeInfo.setDaemonPort(DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_PORT));
        nodeInfo.setAvailablePorts(portStr);
        nodeInfo.setCore(DynamicApplicationConfig.getLong(ConfigKeys.CPU_CORES));
        nodeInfo.setMem(DynamicApplicationConfig.getLong(ConfigKeys.MEM_SIZE));
        nodeInfo.setStatus(0);

        nodeInfoMapper.insertSelective(nodeInfo);
    }
}
