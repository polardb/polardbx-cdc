/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutableTriple;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class DynamicEndpointWatcher {
    public static void checkAvailableEndpoint() {
        String clusterId = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        List<RplStateMachine> stateMachines = DbTaskMetaManager.listStateMachine(StateMachineStatus.RUNNING, clusterId);
        for (RplStateMachine stateMachine : stateMachines) {
            ReplicaMeta replicaMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
            List<MutableTriple<String, Integer, String>> validMasterInfoList = null;
            if (!replicaMeta.isEnableDynamicMasterHost()) {
                continue;
            }
            List<MutableTriple<String, Integer, String>> currentMasterHostList = replicaMeta.getMasterHostList();
            for (MutableTriple<String, Integer, String> master : currentMasterHostList) {
                try (Connection connection = DriverManager.getConnection(String.format(
                        "jdbc:mysql://%s:%s?allowLoadLocalInfile="
                            + "false&autoDeserialize=false&allowLocalInfile=false&allowUrlInLocalInfile=false",
                        master.getLeft(), master.getMiddle()), replicaMeta.getMasterUser(),
                    replicaMeta.getMasterPassword())) {
                    validMasterInfoList =
                        CommonUtil.getComputeNodesWithFixedInstId(connection, replicaMeta.getMasterInstId());
                    if (!validMasterInfoList.isEmpty()) {
                        break;
                    }
                } catch (Exception e) {
                    log.error("connect to master failed ", e);
                }
            }

            // 遍历完所有的旧cn，仍未能获取最新的cn list,则报警并退出
            if (validMasterInfoList == null || validMasterInfoList.isEmpty()) {
                MonitorManager.getInstance()
                    .triggerAlarm(MonitorType.RPL_PROCESS_ERROR, String.format("fsm : %s", stateMachine.getId()),
                        "checkAvailableEndpoint failed");
                return;
            }

            // 找出失效的旧cn
            List<MutableTriple<String, Integer, String>> finalValidMasterInfoList = validMasterInfoList;
            List<MutableTriple<String, Integer, String>> invalidMasterHostList =
                currentMasterHostList.stream().filter(triple -> !finalValidMasterInfoList.contains(triple))
                    .collect(Collectors.toList());
            if (!invalidMasterHostList.isEmpty()) {
                log.warn("find invalid master host: {}, now begin to refresh tasks", invalidMasterHostList);
                refreshFailedRplTasks(stateMachine, invalidMasterHostList, validMasterInfoList);
            }

            // 如果新旧cn存在diff，则持久化到common meta
            if (!invalidMasterHostList.isEmpty() || currentMasterHostList.size() != validMasterInfoList.size()) {
                replicaMeta.setMasterHostList(validMasterInfoList);
                replicaMeta.setMasterHost(validMasterInfoList.get(0).getLeft());
                replicaMeta.setMasterPort(validMasterInfoList.get(0).getMiddle());
                String config = JSON.toJSONString(replicaMeta);
                RplStateMachine newStateMachine = new RplStateMachine();
                newStateMachine.setId(stateMachine.getId());
                newStateMachine.setConfig(config);
                DbTaskMetaManager.updateStateMachine(newStateMachine);
            }
        }
    }

    public static void refreshFailedRplTasks(RplStateMachine stateMachine,
                                             List<MutableTriple<String, Integer, String>> invalidMasterHostList,
                                             List<MutableTriple<String, Integer, String>> validMasterHostList) {
        List<RplTask> rplTasks = DbTaskMetaManager.listTaskByStateMachine(stateMachine.getId());
        for (RplTask rplTask : rplTasks) {
            ReplicaMeta replicaMeta = RplServiceManager.extractMetaFromTaskConfig(rplTask);
            for (MutableTriple<String, Integer, String> invalidMasterHost : invalidMasterHostList) {
                if (StringUtils.equals(replicaMeta.getMasterHost(), invalidMasterHost.getLeft())
                    && replicaMeta.getMasterPort() == invalidMasterHost.getMiddle()) {
                    log.warn("find invalid master host: {} in task {}, now begin to refresh and restart task",
                        invalidMasterHost, rplTask.getId());
                    MutableTriple<String, Integer, String> randomValidHost =
                        CommonUtil.getRandomElementFromList(validMasterHostList);
                    replicaMeta.setMasterHost(randomValidHost.getLeft());
                    replicaMeta.setMasterPort(randomValidHost.getMiddle());
                    FSMMetaManager.updateReplicaTaskConfig(rplTask, replicaMeta, false);
                    DbTaskMetaManager.updateTaskStatus(rplTask.getId(), TaskStatus.RESTART);
                    break;
                }
            }
        }
    }

}
