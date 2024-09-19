package com.aliyun.polardbx.rpl.taskmeta;

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

import com.alibaba.fastjson.JSON;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMState;
import com.aliyun.polardbx.rpl.common.fsmutil.ReplicaFSM;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.MutableTriple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author shicai.xsc 2021/2/19 10:18
 * @since 5.0.0.0
 */
public class RplServiceManager {

    private static final String OPERATION_ON_RUNNING_SLAVE_ERROR =
        "This operation cannot be performed with a running slave; "
            + "run STOP SLAVE (FOR CHANNEL 'channel_name') first";
    private static final String CHANNEL_NOT_EXIST =
        "Slave channel for this channel name does not exist";
    private static final String SUB_CHANNEL_NOT_EXIST =
        "Slave channel for this sub channel does not exist";
    private static final String OPERATION_ON_SUB_CHANNEL = "This operation cannot be performed on sub channel";
    private static final String ILLEGAL_PARAM = "Found illegal param name: %s";
    private static final String CANNOT_CHANGE =
        "Fixed param cannot be changed through change master, run reset slave all [for channel] first";

    private static final Logger rplLogger = LoggerFactory.getLogger(RplServiceManager.class);

    private static final MetaManagerTranProxy TRANSACTION_MANAGER =
        SpringContextHolder.getObject(MetaManagerTranProxy.class);

    private static final List<String> LEGAL_PARAMS_FOR_CHANGE_MASTER = Arrays.asList(
        RplConstants.CHANNEL, RplConstants.MODE, RplConstants.MASTER_HOST, RplConstants.MASTER_PORT,
        RplConstants.MASTER_USER, RplConstants.MASTER_PASSWORD, RplConstants.MASTER_LOG_FILE,
        RplConstants.MASTER_LOG_POS, RplConstants.IGNORE_SERVER_IDS, RplConstants.SOURCE_HOST_TYPE,
        RplConstants.STREAM_GROUP, RplConstants.ENABLE_DYNAMIC_MASTER_HOST, RplConstants.WRITE_SERVER_ID,
        RplConstants.CHANNEL, RplConstants.SUB_CHANNEL, RplConstants.TRIGGER_DYNAMIC_MASTER_HOST,
        RplConstants.TRIGGER_AUTO_POSITION, RplConstants.FORCE_CHANGE, RplConstants.WRITE_TYPE,
        RplConstants.COMPARE_ALL, RplConstants.CONFLICT_STRATEGY, RplConstants.MASTER_INST_ID,
        RplConstants.MASTER_LOG_TIME_SECOND, RplConstants.ENABLE_SRC_LOGICAL_META_SNAPSHOT);

    private static final List<String> LEGAL_PARAMS_FOR_CHANGE_FILTER = Arrays.asList(
        RplConstants.REPLICATE_DO_DB, RplConstants.REPLICATE_IGNORE_DB, RplConstants.REPLICATE_DO_TABLE,
        RplConstants.REPLICATE_IGNORE_TABLE, RplConstants.REPLICATE_WILD_DO_TABLE,
        RplConstants.REPLICATE_WILD_IGNORE_TABLE, RplConstants.REPLICATE_REWRITE_DB, RplConstants.REPLICATE_SKIP_TSO,
        RplConstants.REPLICATE_SKIP_UNTIL_TSO, RplConstants.REPLICATE_ENABLE_DDL,
        RplConstants.CHANNEL, RplConstants.SUB_CHANNEL);

    //////////////////////////////// For RESTFUL calls ///// ///////////////////////////
    public static ResultCode<?> startSlave(Map<String, String> params) {
        try {
            return TRANSACTION_MANAGER.startSlaveWithTran(params);
        } catch (Throwable e) {
            rplLogger.error("start slave occurs exception:", e);
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage()).data(false).build();
        }
    }

    public static ResultCode<?> startSlaveWithTran(Map<String, String> params) {
        rplLogger.info("receive start slave, params: {}", params);
        if (!checkSubChannel(params)) {
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(SUB_CHANNEL_NOT_EXIST).data(false)
                .build();
        }
        if (!checkSlaveExist(params)) {
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(CHANNEL_NOT_EXIST).data(false).build();
        }
        if (!params.containsKey(RplConstants.SUB_CHANNEL)) {
            List<RplStateMachine> stateMachines = listRplStateMachineDefaultAll(params.get(RplConstants.CHANNEL));
            for (RplStateMachine stateMachine : stateMachines) {
                FSMMetaManager.startStateMachine(stateMachine.getId());
                rplLogger.info("start statemachine, {}", stateMachine.getId());
            }
        } else {
            FSMMetaManager.startTask(Long.parseLong(params.get(RplConstants.SUB_CHANNEL)));
            rplLogger.info("start task, {}", params.get(RplConstants.SUB_CHANNEL));
        }
        return ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
    }

    public static ResultCode<?> stopSlave(Map<String, String> params) {
        try {
            return TRANSACTION_MANAGER.stopSlaveWithTran(params);
        } catch (Throwable e) {
            rplLogger.error("stop slave occurs exception:", e);
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage()).data(false).build();
        }
    }

    public static ResultCode<?> stopSlaveWithTran(Map<String, String> params) {
        rplLogger.info("receive stop slave, params: {}", params);
        if (!checkSubChannel(params)) {
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(SUB_CHANNEL_NOT_EXIST).data(false)
                .build();
        }
        if (!checkSlaveExist(params)) {
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(CHANNEL_NOT_EXIST).data(false).build();
        }
        if (!params.containsKey(RplConstants.SUB_CHANNEL)) {
            List<RplStateMachine> stateMachines = listRplStateMachineDefaultAll(params.get(RplConstants.CHANNEL));
            for (RplStateMachine stateMachine : stateMachines) {
                FSMMetaManager.stopStateMachine(stateMachine.getId());
                rplLogger.info("stop statemachine, {}", stateMachine.getId());
            }
        } else {
            FSMMetaManager.stopTask(Long.parseLong(params.get(RplConstants.SUB_CHANNEL)));
            rplLogger.info("stop task, {}", params.get(RplConstants.SUB_CHANNEL));
        }

        return ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
    }

    public static ResultCode<?> resetSlave(Map<String, String> params) {
        try {
            return TRANSACTION_MANAGER.resetSlaveWithTran(params);
        } catch (Throwable e) {
            rplLogger.error("reset slave occurs exception:", e);
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage()).data(false).build();
        }
    }

    public static ResultCode<?> resetSlaveWithTran(Map<String, String> params) {
        rplLogger.info("receive reset slave, params: {}", params);

        // rules :
        // RESET SLAVE ALL, remove all rpl fsm
        // RESET SLAVE ALL FOR CHANNEL 'xxx', remove rpl fsm with specific channel name
        // RESET SLAVE, remove history of all rpl fsm
        // RESET SLAVE FOR CHANNEL 'xxx', remove history of rpl fsm with specific channel name
        // here history means position, error and statistical data

        if (params.containsKey(RplConstants.SUB_CHANNEL)) {
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(OPERATION_ON_SUB_CHANNEL)
                .data(false).build();
        }

        if (!checkSlaveExist(params)) {
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(CHANNEL_NOT_EXIST).data(false).build();
        }

        if (!checkSlaveRunning(params)) {
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(OPERATION_ON_RUNNING_SLAVE_ERROR)
                .data(false).build();
        }

        List<RplStateMachine> stateMachines = listRplStateMachineDefaultAll(params.get(RplConstants.CHANNEL));
        for (RplStateMachine stateMachine : stateMachines) {
            if (StringUtils.equalsIgnoreCase("false", params.get(RplConstants.IS_ALL))) {
                FSMMetaManager.clearReplicaHistory(stateMachine);
                rplLogger.info("remove history of replica fsm, {}", stateMachine.getId());
            } else {
                FSMMetaManager.deleteStateMachine(stateMachine.getId());
                rplLogger.info("remove replica fsm itself, {}", stateMachine.getId());
            }
        }
        return ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
    }

    public static ResultCode<?> showSlaveStatus(Map<String, String> params) {
        rplLogger.info("receive show slave status, params: {}", params);
        List<LinkedHashMap<String, String>> responses = new ArrayList<>();
        if (!checkSubChannel(params)) {
            return ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success")
                .data(JSON.toJSONString(responses)).build();
        }
        if (!params.containsKey(RplConstants.SUB_CHANNEL)) {
            List<RplStateMachine> stateMachines = listRplStateMachineDefaultAll(params.get(RplConstants.CHANNEL));
            for (RplStateMachine stateMachine : stateMachines) {
                rplLogger.info("show status for fsm, {}", stateMachine.getId());
                RplService service = DbTaskMetaManager.getService(stateMachine.getId(), ServiceType.REPLICA_INC);
                List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
                extractStatusFromTask(tasks, stateMachine, responses);
            }
        } else {
            RplTask task = DbTaskMetaManager.getTask(Long.parseLong(params.get(RplConstants.SUB_CHANNEL)));
            List<RplTask> tasks = Collections.singletonList(task);
            RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(task.getStateMachineId());
            extractStatusFromTask(tasks, stateMachine, responses);
        }
        return ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success")
            .data(JSON.toJSONString(responses)).build();
    }

    public static ResultCode<?> changeMaster(Map<String, String> params) {
        try {
            return TRANSACTION_MANAGER.changeMasterWithTran(params);
        } catch (Throwable e) {
            rplLogger.error("change master occurs exception:", e);
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage())
                .data(false).build();
        }
    }

    public static ResultCode<?> changeMasterWithTran(Map<String, String> params) {
        rplLogger.info("receive change master, params: {}", params);
        try {
            // 参数检查
            for (String paramName : params.keySet()) {
                if (!LEGAL_PARAMS_FOR_CHANGE_MASTER.contains(paramName)) {
                    return ResultCode.builder().code(CommonConstants.FAILURE_CODE)
                        .msg(String.format(ILLEGAL_PARAM, paramName))
                        .data(false)
                        .build();
                }
            }

            params.putIfAbsent(RplConstants.CHANNEL, "");
            if (!checkSubChannel(params)) {
                return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(SUB_CHANNEL_NOT_EXIST).data(false)
                    .build();
            }
            if (!checkSlaveRunning(params)) {
                return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(OPERATION_ON_RUNNING_SLAVE_ERROR)
                    .data(false).build();
            }

            boolean useMetaPosition = false;
            if (params.containsKey(RplConstants.MASTER_LOG_FILE) && params.containsKey(RplConstants.MASTER_LOG_POS)
                || params.containsKey(RplConstants.MASTER_LOG_TIME_SECOND)) {
                useMetaPosition = true;
            }

            // sub channel单独处理 只允许修改指定的几个参数，详见extractChangeMasterParamsForModify
            if (params.containsKey(RplConstants.SUB_CHANNEL)) {
                RplTask task = DbTaskMetaManager.getTask(Long.parseLong(params.get(RplConstants.SUB_CHANNEL)));
                ReplicaMeta replicaMeta = extractMetaFromTaskConfig(task);
                extractChangeMasterParamsForSubChannelModify(params, replicaMeta);
                FSMMetaManager.updateReplicaTaskConfig(task, replicaMeta, useMetaPosition);
                return ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
            }

            // 处理channel
            RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(params.get(RplConstants.CHANNEL));
            if (stateMachine == null) {
                createMaster(params);
                return ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
            } else {
                // rules:
                // 1. 如果我们设置MASTER_HOST 或 MASTER_PORT 或 MODE 或 host_type 或 stream_group参数，
                // 则"无论如何"视为新的master,直接重建 (与mysql行为一致)
                // 2. 如果我们设置 MASTER_LOG_FILE 与 MASTER_LOG_POS 或者 MASTER_LOG_TIME_SECOND 参数，则视为新的position
                // MASTER_LOG_TIME_SECOND 优先
                // 清除位点上下文并修改fsm/service/task的元数据
                // 3. 如果创建任务时未指定位点，则默认采用最新位点
                if (!Boolean.parseBoolean(params.getOrDefault(RplConstants.FORCE_CHANGE, "false"))) {
                    if (params.containsKey(RplConstants.MASTER_HOST) || params.containsKey(RplConstants.MASTER_PORT)
                        || params.containsKey(RplConstants.MODE) || params.containsKey(RplConstants.SOURCE_HOST_TYPE)
                        || params.containsKey(RplConstants.STREAM_GROUP)) {
                        return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(CANNOT_CHANGE).data(false)
                            .build();
                    }
                }
                List<RplTask> tasks = DbTaskMetaManager.listTaskByStateMachine(stateMachine.getId());
                for (RplTask task : tasks) {
                    ReplicaMeta replicaMeta = extractMetaFromTaskConfig(task);
                    extractChangeMasterParams(params, replicaMeta);
                    FSMMetaManager.updateReplicaTaskConfig(task, replicaMeta, useMetaPosition);
                }
                // 目前只有更新meta cn的时候需要更新common meta
                ReplicaMeta commonMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
                extractChangeMasterParams(params, commonMeta);
                FSMMetaManager.updateReplicaConfig(stateMachine, commonMeta);

                // 特殊处理 TRIGGER_AUTO_POSITION 和 TRIGGER_DYNAMIC_MASTER_HOST
                if (params.containsKey(RplConstants.TRIGGER_DYNAMIC_MASTER_HOST) &&
                    CommonConstants.TRUE.equalsIgnoreCase(params.get(RplConstants.TRIGGER_DYNAMIC_MASTER_HOST))) {
                    generateDynamicHostInfo(stateMachine);
                }
                if (params.containsKey(RplConstants.TRIGGER_AUTO_POSITION) &&
                    CommonConstants.TRUE.equalsIgnoreCase(params.get(RplConstants.TRIGGER_AUTO_POSITION))) {
                    generateLatestPosition(stateMachine);
                }
            }
        } catch (Throwable e) {
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage()).build();
        }
        return ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
    }

    public static void createMaster(Map<String, String> params) throws Exception {
        ReplicaMeta replicaMeta = new ReplicaMeta();
        extractChangeMasterParams(params, replicaMeta);
        rplLogger.info("receive change master for channel: {}, create new fsm from config: {}",
            params.get(RplConstants.CHANNEL), replicaMeta);
        ReplicaFSM.getInstance().create(replicaMeta);
    }

    public static ResultCode<?> changeReplicationFilter(Map<String, String> params) {
        try {
            return TRANSACTION_MANAGER.changeReplicationFilterWithTran(params);
        } catch (Throwable e) {
            rplLogger.error("change replication filter occurs exception:", e);
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(e.getMessage()).data(false).build();
        }
    }

    public static ResultCode<?> changeReplicationFilterWithTran(Map<String, String> params) {
        for (String paramName : params.keySet()) {
            if (!LEGAL_PARAMS_FOR_CHANGE_FILTER.contains(paramName)) {
                return ResultCode.builder().code(CommonConstants.FAILURE_CODE)
                    .msg(String.format(ILLEGAL_PARAM, paramName))
                    .data(false)
                    .build();
            }
        }
        rplLogger.info("receive change replication filter, params: {}", params);
        if (!checkSlaveRunning(params)) {
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(OPERATION_ON_RUNNING_SLAVE_ERROR)
                .data(false).build();
        }
        if (!checkSlaveExist(params)) {
            return ResultCode.builder().code(CommonConstants.FAILURE_CODE).msg(CHANNEL_NOT_EXIST).data(false).build();
        }
        processSchemaTableName(params);

        if (!params.containsKey(RplConstants.SUB_CHANNEL)) {
            List<RplStateMachine> stateMachines = listRplStateMachineDefaultAll(params.get(RplConstants.CHANNEL));
            for (RplStateMachine stateMachine : stateMachines) {
                List<RplTask> tasks = DbTaskMetaManager.listTaskByStateMachine(stateMachine.getId());
                for (RplTask task : tasks) {
                    ReplicaMeta replicaMeta = extractMetaFromTaskConfig(task);
                    rplLogger.info("receive change replication filter for stateMachine id: {}, new config: {}",
                        stateMachine.getId(), replicaMeta);
                    extractChangeFilterParams(params, replicaMeta);
                    FSMMetaManager.updateReplicaTaskConfig(task, replicaMeta, false);
                }
                ReplicaMeta commonMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
                rplLogger.info("receive change replication filter for stateMachine id: {}, old config: {}",
                    stateMachine.getId(), commonMeta);
                extractChangeFilterParams(params, commonMeta);
                FSMMetaManager.updateReplicaConfig(stateMachine, commonMeta);
            }
        } else {
            RplTask task = DbTaskMetaManager.getTask(Long.parseLong(params.get(RplConstants.SUB_CHANNEL)));
            ReplicaMeta replicaMeta = extractMetaFromTaskConfig(task);
            extractChangeFilterParams(params, replicaMeta);
            FSMMetaManager.updateReplicaTaskConfig(task, replicaMeta, false);
        }

        return ResultCode.builder().code(CommonConstants.SUCCESS_CODE).msg("success").data(true).build();
    }

    public static void processSchemaTableName(Map<String, String> params) {
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String paramName = entry.getKey();
            String paramValue = entry.getValue();
            if (!StringUtils.equals(paramName, RplConstants.REPLICATE_WILD_DO_TABLE) &&
                !StringUtils.equals(paramName, RplConstants.REPLICATE_WILD_IGNORE_TABLE)) {
                entry.setValue(SQLUtils.normalize(paramValue));
            }
        }
    }

    public static boolean checkSubChannel(Map<String, String> params) {
        try {
            if (params.containsKey(RplConstants.SUB_CHANNEL)) {
                long taskId = Long.parseLong(params.get(RplConstants.SUB_CHANNEL));
                RplTask task = DbTaskMetaManager.getTask(taskId);
                if (null == task) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return true;
    }

    public static List<RplStateMachine> listRplStateMachineDefaultAll(String channel) {
        List<RplStateMachine> stateMachines = new ArrayList<>();
        if (null != channel) {
            RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
            if (stateMachine != null) {
                stateMachines.add(stateMachine);
            }
        } else {
            stateMachines = DbTaskMetaManager.listRplStateMachine();
        }
        return stateMachines;
    }

    public static boolean checkSlaveExist(Map<String, String> params) {
        if (!params.containsKey(RplConstants.SUB_CHANNEL)) {
            List<RplStateMachine> stateMachines = listRplStateMachineDefaultAll(params.get(RplConstants.CHANNEL));
            if (params.containsKey(RplConstants.CHANNEL) && stateMachines.isEmpty()) {
                rplLogger.error("replica fsm not exist for this channel name : {}", params.get(RplConstants.CHANNEL));
                return false;
            }
        }
        return true;
    }

    public static boolean checkSlaveRunning(Map<String, String> params) {
        if (!params.containsKey(RplConstants.SUB_CHANNEL)) {
            List<RplStateMachine> stateMachines = listRplStateMachineDefaultAll(params.get(RplConstants.CHANNEL));
            for (RplStateMachine stateMachine : stateMachines) {
                if (StateMachineStatus.valueOf(stateMachine.getStatus()) == StateMachineStatus.RUNNING) {
                    rplLogger.error("replica fsm is running thus not support this action, channel name: {}",
                        params.get(RplConstants.CHANNEL));
                    return false;
                }
            }
        } else {
            RplTask rpltask = DbTaskMetaManager.getTask(Long.parseLong(params.get(RplConstants.SUB_CHANNEL)));
            TaskStatus status = TaskStatus.valueOf(rpltask.getStatus());
            if (status == TaskStatus.RUNNING || status == TaskStatus.READY || status == TaskStatus.RESTART) {
                rplLogger.error("replica task is running thus not support this action, sub channel name: {}",
                    params.get(RplConstants.SUB_CHANNEL));
                return false;
            }
        }
        return true;
    }

    // return first replica cluster
    public static String findActiveReplicaCluster() {
        if (ClusterType.REPLICA.name().equals(DynamicApplicationConfig.getClusterType())) {
            return DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        } else {
            List<NodeInfo> nodeInfoList = DbTaskMetaManager.listActiveClusterNode(ClusterType.REPLICA.name());
            if (CollectionUtils.isEmpty(nodeInfoList)) {
                return DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
            } else {
                return nodeInfoList.get(0).getClusterId();
            }
        }
    }

    public static void extractStatusFromTask(List<RplTask> tasks, RplStateMachine stateMachine,
                                             List<LinkedHashMap<String, String>> responses) {
        for (RplTask task : tasks) {
            RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
            ExtractorConfig extractorConfig = JSON.parseObject(config.getExtractorConfig(), ExtractorConfig.class);
            ReplicaMeta replicaMeta = JSON.parseObject(extractorConfig.getPrivateMeta(), ReplicaMeta.class);
            BinlogPosition binlogPosition;
            if (StringUtils.isNotBlank(task.getPosition())) {
                binlogPosition = BinlogPosition.parseFromString(task.getPosition());
            } else {
                binlogPosition = BinlogPosition.parseFromString(CommonUtil.getRplInitialPosition());
            }
            String running = TaskStatus.valueOf(task.getStatus()) == TaskStatus.RUNNING ? "Yes" : "No";
            LinkedHashMap<String, String> response = new LinkedHashMap<>();
            response.put("Master_Host", replicaMeta.getMasterHost());
            response.put("Master_User", replicaMeta.getMasterUser());
            response.put("Master_Port", Integer.toString(replicaMeta.getMasterPort()));
            response.put("Master_Log_File", binlogPosition.getFileName());
            response.put("Read_Master_Log_Pos", String.valueOf(binlogPosition.getPosition()));
            response.put("Relay_Log_File", binlogPosition.getFileName());
            response.put("Relay_Log_Pos", String.valueOf(binlogPosition.getPosition()));
            response.put("Relay_Master_Log_File", binlogPosition.getFileName());
            response.put("Slave_IO_Running", running);
            response.put("Slave_SQL_Running", running);
            response.put("Replicate_Do_DB", replicaMeta.getDoDb());
            response.put("Replicate_Ignore_DB", replicaMeta.getIgnoreDb());
            response.put("Replicate_Do_Table", replicaMeta.getDoTable());
            response.put("Replicate_Ignore_Table", replicaMeta.getIgnoreTable());
            response.put("Replicate_Wild_Do_Table", replicaMeta.getWildDoTable());
            response.put("Replicate_Wild_Ignore_Table", replicaMeta.getWildIgnoreTable());
            response.put("Last_Error", task.getLastError());
            response.put("Exec_Master_Log_Pos", String.valueOf(binlogPosition.getPosition()));
            response.put("Exec_Master_Log_Tso", StringUtils.isNotEmpty(binlogPosition.getRtso()) ?
                binlogPosition.getRtso().substring(0, Math.min(19, binlogPosition.getRtso().length())) : "NULL");

            response.put("Until_Condition", "None");
            response.put("Master_SSL_Allowed", "No");
            response.put("Seconds_Behind_Master", String.valueOf(FSMMetaManager.computeTaskDelay(task)));
            response.put("Master_SSL_Verify_Server_Cert", "No");
            response.put("Replicate_Ignore_Server_Ids", replicaMeta.getIgnoreServerIds());
            response.put("SQL_Remaining_Delay", "NULL");
            response.put("Slave_SQL_Running_State", running);
            response.put("Auto_Position", "0");
            response.put("Replicate_Rewrite_DB", replicaMeta.getRewriteDb());
            response.put("Replicate_Mode", replicaMeta.isImageMode() ?
                RplConstants.IMAGE_MODE : RplConstants.INCREMENTAL_MODE);
            response.put("Running_Stage", FSMState.valueOf(stateMachine.getState()).name());
            response.put("Replicate_Enable_Ddl", String.valueOf(replicaMeta.isEnableDdl()));
            response.put("Skip_Tso", replicaMeta.getSkipTso());
            response.put("Skip_Until_Tso", replicaMeta.getSkipUntilTso());
            response.put("Write_Type", replicaMeta.getApplierType().name());
            response.put("Conflict_Strategy", replicaMeta.getConflictStrategy().name());
            response.put("Source_Stream_Group_Name", replicaMeta.getStreamGroup());
            response.put("Channel_Name", stateMachine.getChannel());
            response.put("Sub_Channel_Name", task.getId().toString());
            for (Map.Entry<String, String> entry : response.entrySet()) {
                if (entry.getValue() == null) {
                    entry.setValue("");
                }
            }
            responses.add(response);
        }
    }

    public static void extractChangeFilterParams(Map<String, String> params, ReplicaMeta replicaMeta) {
        if (params.containsKey(RplConstants.REPLICATE_DO_DB)) {
            // origin: (full_src_1, rpl)
            // in db: full_src_1,rpl
            replicaMeta.setDoDb(CommonUtil.removeBracket(params.get(RplConstants.REPLICATE_DO_DB)));
        }
        if (params.containsKey(RplConstants.REPLICATE_IGNORE_DB)) {
            // origin: (full_src_1, gbktest)
            // in db: full_src_1,gbktest
            replicaMeta.setIgnoreDb(CommonUtil.removeBracket(params.get(RplConstants.REPLICATE_IGNORE_DB)));
        }
        if (params.containsKey(RplConstants.REPLICATE_DO_TABLE)) {
            // origin: (full_src_1.t1, full_src_1.t2)
            // in db: full_src_1.t1,full_src_1.t2
            replicaMeta.setDoTable(CommonUtil.removeBracket(params.get(RplConstants.REPLICATE_DO_TABLE)));
        }
        if (params.containsKey(RplConstants.REPLICATE_IGNORE_TABLE)) {
            // origin: (full_src_1.t2, full_src_1.t3)
            // in db: full_src_1.t3,full_src_1.t2
            replicaMeta
                .setIgnoreTable(CommonUtil.removeBracket(params.get(RplConstants.REPLICATE_IGNORE_TABLE)));
        }
        if (params.containsKey(RplConstants.REPLICATE_WILD_DO_TABLE)) {
            // origin: ('d%.tb\_charset%', 'd%.col\_charset%')
            // in db: d%.tb\_charset%,d%.col\_charset%
            String replicateWildDoTable = CommonUtil
                .removeBracket(params.get(RplConstants.REPLICATE_WILD_DO_TABLE));
            replicateWildDoTable = replicateWildDoTable.replace("'", "");
            replicaMeta.setWildDoTable(replicateWildDoTable);
        }
        if (params.containsKey(RplConstants.REPLICATE_WILD_IGNORE_TABLE)) {
            // origin: ('d%.tb\\_charset%', 'd%.col\\_charset%')
            // in db: d%.tb\_charset%,d%.col\_charset%
            String replicateWildIgnoreTable = CommonUtil
                .removeBracket(params.get(RplConstants.REPLICATE_WILD_IGNORE_TABLE));
            replicateWildIgnoreTable = replicateWildIgnoreTable.replace("'", "");
            replicaMeta.setWildIgnoreTable(replicateWildIgnoreTable);
        }
        if (params.containsKey(RplConstants.REPLICATE_REWRITE_DB)) {
            // origin: ((full_src_1, full_dst_1), (full_src_2, full_dst_2))
            // in db: (full_src_1,full_dst_1),(full_src_2,full_dst_2)
            replicaMeta.setRewriteDb(CommonUtil.removeBracket(params.get(RplConstants.REPLICATE_REWRITE_DB)));
        }
        if (params.containsKey(RplConstants.REPLICATE_SKIP_TSO)) {
            replicaMeta.setSkipTso(params.get(RplConstants.REPLICATE_SKIP_TSO));
        }
        if (params.containsKey(RplConstants.REPLICATE_SKIP_UNTIL_TSO)) {
            replicaMeta.setSkipUntilTso(params.get(RplConstants.REPLICATE_SKIP_UNTIL_TSO));
        }
        if (params.containsKey(RplConstants.REPLICATE_ENABLE_DDL)) {
            replicaMeta.setEnableDdl(Boolean.parseBoolean(params.get(RplConstants.REPLICATE_ENABLE_DDL)));
        }
    }

    public static ReplicaMeta extractMetaFromTaskConfig(RplTask task) {
        RplTaskConfig config = DbTaskMetaManager.getTaskConfig(task.getId());
        ExtractorConfig extractorConfig = JSON.parseObject(config.getExtractorConfig(), ExtractorConfig.class);
        return JSON.parseObject(extractorConfig.getPrivateMeta(), ReplicaMeta.class);
    }

    public static void extractChangeMasterParams(Map<String, String> params, ReplicaMeta replicaMeta) {
        replicaMeta.setClusterId(findActiveReplicaCluster());
        if (params.containsKey(RplConstants.CHANNEL)) {
            replicaMeta.setChannel(params.get(RplConstants.CHANNEL));
        }
        replicaMeta.setImageMode(false);
        if (params.containsKey(RplConstants.MODE)) {
            if (StringUtils.equalsIgnoreCase(params.get(RplConstants.MODE), RplConstants.IMAGE_MODE)) {
                replicaMeta.setImageMode(true);
            }
        }
        if (params.containsKey(RplConstants.MASTER_HOST)) {
            CommonUtil.hostSafeCheck(params.get(RplConstants.MASTER_HOST));
            replicaMeta.setMasterHost(params.get(RplConstants.MASTER_HOST));
        }
        if (params.containsKey(RplConstants.MASTER_PORT)) {
            replicaMeta.setMasterPort(Integer.parseInt(params.get(RplConstants.MASTER_PORT)));
        }
        if (params.containsKey(RplConstants.MASTER_USER)) {
            replicaMeta.setMasterUser(params.get(RplConstants.MASTER_USER));
        }
        if (params.containsKey(RplConstants.MASTER_PASSWORD)) {
            replicaMeta.setMasterPassword(params.get(RplConstants.MASTER_PASSWORD));
        }
        if (params.containsKey(RplConstants.MASTER_LOG_FILE)
            && params.containsKey(RplConstants.MASTER_LOG_POS)) {
            replicaMeta.setPosition(
                params.get(RplConstants.MASTER_LOG_FILE) + ":" + params.get(RplConstants.MASTER_LOG_POS));
        }
        if (params.containsKey(RplConstants.MASTER_LOG_TIME_SECOND)) {
            replicaMeta.setPosition("0:0#" + RplConstants.ROLLBACK_STRING + "."
                + params.get(RplConstants.MASTER_LOG_TIME_SECOND));
        }
        if (params.containsKey(RplConstants.IGNORE_SERVER_IDS)) {
            // origin: (1,2)
            // in db: 1,2
            String ignoreServerIds = CommonUtil.removeBracket(params.get(RplConstants.IGNORE_SERVER_IDS));
            replicaMeta.setIgnoreServerIds(ignoreServerIds);
        }
        replicaMeta.setMasterType(HostType.MYSQL);
        if (params.containsKey(RplConstants.SOURCE_HOST_TYPE)) {
            if (StringUtils.equalsIgnoreCase(RplConstants.POLARDBX, params.get(RplConstants.SOURCE_HOST_TYPE))) {
                replicaMeta.setMasterType(HostType.POLARX2);
            } else if (StringUtils.equalsIgnoreCase(RplConstants.RDS, params.get(RplConstants.SOURCE_HOST_TYPE))) {
                replicaMeta.setMasterType(HostType.RDS);
            }
        }
        replicaMeta.setApplierType(ApplierType.SPLIT);
        if (params.containsKey(RplConstants.WRITE_TYPE)) {
            ApplierType type = ApplierType.valueOf(StringUtils.upperCase(params.get(RplConstants.WRITE_TYPE)));
            replicaMeta.setApplierType(type);
        }
        if (params.containsKey(RplConstants.COMPARE_ALL)) {
            replicaMeta.setCompareAll(Boolean.parseBoolean(params.get(RplConstants.COMPARE_ALL)));
        }
        if (params.containsKey(RplConstants.ENABLE_SRC_LOGICAL_META_SNAPSHOT)) {
            replicaMeta.setEnableSrcLogicalMetaSnapshot(Boolean.parseBoolean(
                params.get(RplConstants.ENABLE_SRC_LOGICAL_META_SNAPSHOT)));
        }
        replicaMeta.setInsertOnUpdateMiss(true);
        if (params.containsKey(RplConstants.INSERT_ON_UPDATE_MISS)) {
            replicaMeta.setInsertOnUpdateMiss(Boolean.parseBoolean(params.get(RplConstants.INSERT_ON_UPDATE_MISS)));
        }
        replicaMeta.setConflictStrategy(ConflictStrategy.OVERWRITE);
        if (params.containsKey(RplConstants.CONFLICT_STRATEGY)) {
            ConflictStrategy conflictStrategy = ConflictStrategy.valueOf(StringUtils
                .upperCase(params.get(RplConstants.CONFLICT_STRATEGY)));
            replicaMeta.setConflictStrategy(conflictStrategy);
        }
        if (params.containsKey(RplConstants.MASTER_INST_ID)) {
            replicaMeta.setMasterInstId(params.get(RplConstants.MASTER_INST_ID));
        }
        if (params.containsKey(RplConstants.STREAM_GROUP)) {
            replicaMeta.setStreamGroup(params.get(RplConstants.STREAM_GROUP));
        }
        if (params.containsKey(RplConstants.ENABLE_DYNAMIC_MASTER_HOST)) {
            replicaMeta.setEnableDynamicMasterHost(Boolean.parseBoolean(params
                .get(RplConstants.ENABLE_DYNAMIC_MASTER_HOST)));
        }
        if (params.containsKey(RplConstants.WRITE_SERVER_ID)) {
            replicaMeta.setServerId(params.get(RplConstants.WRITE_SERVER_ID));
        }
        replicaMeta.setDoDb("");
        replicaMeta.setIgnoreDb("");
        replicaMeta.setDoTable("");
        replicaMeta.setIgnoreTable("");
        replicaMeta.setWildDoTable("");
        replicaMeta.setWildIgnoreTable("");
        replicaMeta.setRewriteDb("");
    }

    public static void extractChangeMasterParamsForSubChannelModify(Map<String, String> params,
                                                                    ReplicaMeta replicaMeta) {
        if (params.containsKey(RplConstants.MASTER_HOST)) {
            CommonUtil.hostSafeCheck(params.get(RplConstants.MASTER_HOST));
            replicaMeta.setMasterHost(params.get(RplConstants.MASTER_HOST));
        }
        if (params.containsKey(RplConstants.MASTER_PORT)) {
            replicaMeta.setMasterPort(Integer.parseInt(params.get(RplConstants.MASTER_PORT)));
        }
        if (params.containsKey(RplConstants.MASTER_USER)) {
            replicaMeta.setMasterUser(params.get(RplConstants.MASTER_USER));
        }
        if (params.containsKey(RplConstants.MASTER_PASSWORD)) {
            replicaMeta.setMasterPassword(params.get(RplConstants.MASTER_PASSWORD));
        }
        if (params.containsKey(RplConstants.MASTER_LOG_FILE)
            && params.containsKey(RplConstants.MASTER_LOG_POS)) {
            replicaMeta.setPosition(
                params.get(RplConstants.MASTER_LOG_FILE) + ":" + params.get(RplConstants.MASTER_LOG_POS));
        }
        if (params.containsKey(RplConstants.MASTER_LOG_TIME_SECOND)) {
            replicaMeta.setPosition("0:0#" + RplConstants.ROLLBACK_STRING + "."
                + params.get(RplConstants.MASTER_LOG_TIME_SECOND));
        }
    }

    public static void generateDynamicHostInfo(RplStateMachine stateMachine) {
        ReplicaMeta meta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
        try (Connection connection = DriverManager.getConnection(
            String.format(
                "jdbc:mysql://%s:%s?allowLoadLocalInfile=false&autoDeserialize=false"
                    + "&allowLocalInfile=false&allowUrlInLocalInfile=false",
                meta.getMasterHost(), meta.getMasterPort()),
            meta.getMasterUser(), meta.getMasterPassword())) {
            List<MutableTriple<String, Integer, String>> masterInfos = CommonUtil.getComputeNodesWithFixedInstId(
                connection, meta.getMasterInstId());
            List<RplTask> tasks = DbTaskMetaManager.listTaskByStateMachine(stateMachine.getId());
            for (int i = 0; i < tasks.size(); i++) {
                RplTask task = tasks.get(i);
                ReplicaMeta replicaMeta = RplServiceManager.extractMetaFromTaskConfig(task);
                replicaMeta.setMasterHost(masterInfos.get(i % masterInfos.size()).getLeft());
                replicaMeta.setMasterPort(masterInfos.get(i % masterInfos.size()).getMiddle());
                FSMMetaManager.updateReplicaTaskConfig(tasks.get(i), replicaMeta, false);
            }
        } catch (SQLException e) {
            throw new PolardbxException("connect to master failed ", e);
        }
    }

    public static void generateLatestPosition(RplStateMachine stateMachine) {
        ReplicaMeta meta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
        try (Connection connection = DriverManager.getConnection(
            String.format(
                "jdbc:mysql://%s:%s?allowLoadLocalInfile=false&autoDeserialize=false"
                    + "&allowLocalInfile=false&allowUrlInLocalInfile=false",
                meta.getMasterHost(), meta.getMasterPort()),
            meta.getMasterUser(), meta.getMasterPassword())) {
            List<String> streamPositions;
            if (StringUtils.isNotEmpty(meta.getStreamGroup())) {
                streamPositions = CommonUtil.getStreamLatestPositions(connection, meta.getStreamGroup());
            } else {
                streamPositions = Collections.singletonList(CommonUtil.getBinaryLatestPosition(connection));
            }
            RplService service = DbTaskMetaManager.getService(stateMachine.getId(), ServiceType.REPLICA_INC);
            List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
            for (int i = 0; i < tasks.size(); i++) {
                RplTask task = tasks.get(i);
                ReplicaMeta replicaMeta = RplServiceManager.extractMetaFromTaskConfig(task);
                replicaMeta.setPosition(streamPositions.get(i));
                FSMMetaManager.updateReplicaTaskConfig(tasks.get(i), replicaMeta, true);
            }

        } catch (SQLException e) {
            throw new PolardbxException("connect to master failed ", e);
        }
    }
}
