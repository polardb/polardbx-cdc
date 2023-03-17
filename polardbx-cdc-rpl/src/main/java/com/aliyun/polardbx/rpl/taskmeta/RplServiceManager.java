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
package com.aliyun.polardbx.rpl.taskmeta;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.alibaba.polardbx.druid.sql.SQLUtils;
import com.aliyun.polardbx.binlog.ClusterTypeEnum;
import com.aliyun.polardbx.binlog.domain.po.NodeInfo;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.rpc.cdc.ChangeMasterRequest;
import com.aliyun.polardbx.rpc.cdc.ChangeReplicationFilterRequest;
import com.aliyun.polardbx.rpc.cdc.ResetSlaveRequest;
import com.aliyun.polardbx.rpc.cdc.RplCommandResponse;
import com.aliyun.polardbx.rpc.cdc.ShowSlaveStatusRequest;
import com.aliyun.polardbx.rpc.cdc.ShowSlaveStatusResponse;
import com.aliyun.polardbx.rpc.cdc.StartSlaveRequest;
import com.aliyun.polardbx.rpc.cdc.StopSlaveRequest;
import com.aliyun.polardbx.rpl.applier.StatisticUnit;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMState;
import com.aliyun.polardbx.rpl.common.fsmutil.ReplicaFSM;
import io.grpc.stub.StreamObserver;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author shicai.xsc 2021/2/19 10:18
 * @since 5.0.0.0
 */
public class RplServiceManager {

    private final static String OPERATION_ON_RUNNING_SLAVE_ERROR =
        "This operation cannot be performed with a running slave; "
            + "run STOP SLAVE (FOR CHANNEL 'channel_name') first";
    private final static String MULTIPLE_CHANNELS_EXIST =
        "Multiple channels exist on the slave. Please provide channel name as an argument";
    private final static String CHANNEL_NOT_EXIST =
        "Slave channel for this channel name does not exist";

    private final static Logger rplLogger = LoggerFactory.getLogger(RplServiceManager.class);

    //////////////////////////////// For RPC calls ///// ///////////////////////////
    public static void startSlave(StartSlaveRequest request, StreamObserver<RplCommandResponse> responseObserver) {
        try {
            Map<String, String> params = parseRequest(request.getRequest());
            rplLogger.info("receive start slave for channel: {}, params: {}", params.get(RplConstants.CHANNEL), params);
            if (!checkSlaveExist(params, responseObserver)) {
                return;
            }
            List<RplStateMachine> stateMachines = listRplStateMachine(params.get(RplConstants.CHANNEL));
            for (RplStateMachine stateMachine : stateMachines) {
                FSMMetaManager.startStateMachine(stateMachine.getId());
                rplLogger.info("start statemachine, {}", stateMachine.getId());
            }
            FSMMetaManager.distributeTasks();
            setRpcRplCommandResponse(responseObserver, 0, "");
        } catch (Throwable e) {
            rplLogger.error("start slave occurs exception:", e);
            setRpcRplCommandResponse(responseObserver, 1, e.getMessage());
        }
    }

    public static void stopSlave(StopSlaveRequest request, StreamObserver<RplCommandResponse> responseObserver) {
        try {
            Map<String, String> params = parseRequest(request.getRequest());
            rplLogger.info("receive stop slave for channel: {}, params: {}", params.get(RplConstants.CHANNEL), params);
            if (!checkSlaveExist(params, responseObserver)) {
                return;
            }
            List<RplStateMachine> stateMachines = listRplStateMachine(params.get(RplConstants.CHANNEL));
            for (RplStateMachine stateMachine : stateMachines) {
                FSMMetaManager.stopStateMachine(stateMachine.getId());
                rplLogger.info("stop statemachine, {}", stateMachine.getId());
            }
            setRpcRplCommandResponse(responseObserver, 0, "");
        } catch (Throwable e) {
            rplLogger.error("stop slave occurs exception:", e);
            setRpcRplCommandResponse(responseObserver, 1, e.getMessage());
        }
    }

    public static void resetSlave(ResetSlaveRequest request, StreamObserver<RplCommandResponse> responseObserver) {
        try {
            Map<String, String> params = parseRequest(request.getRequest());
            rplLogger.info("receive reset slave for channel: {}, params: {}", params.get(RplConstants.CHANNEL), params);

            // rules :
            // RESET SLAVE ALL, remove all rpl fsm
            // RESET SLAVE ALL FOR CHANNEL 'xxx', remove rpl fsm with specific channel name
            // RESET SLAVE, remove history of all rpl fsm
            // RESET SLAVE FOR CHANNEL 'xxx', remove history of rpl fsm with specific channel name
            // here history means position, error and statistical data

            if (!checkSlaveRunning(params, responseObserver) || !checkSlaveExist(params, responseObserver)) {
                return;
            }

            List<RplStateMachine> stateMachines = listRplStateMachine(params.get(RplConstants.CHANNEL));
            for (RplStateMachine stateMachine : stateMachines) {
                if (StringUtils.equalsIgnoreCase("false", params.get(RplConstants.IS_ALL))) {
                    FSMMetaManager.clearReplicaHistory(stateMachine, false);
                    rplLogger.info("remove history of replica fsm, {}", stateMachine.getId());
                } else {
                    DbTaskMetaManager.deleteStateMachine(stateMachine.getId());
                    rplLogger.info("remove replica fsm itself, {}", stateMachine.getId());
                }
            }
            setRpcRplCommandResponse(responseObserver, 0, "");
        } catch (Throwable e) {
            rplLogger.error("reset slave occurs exception:", e);
            setRpcRplCommandResponse(responseObserver, 1, e.getMessage());
        }
    }

    public static void showSlaveStatus(ShowSlaveStatusRequest request,
                                       StreamObserver<ShowSlaveStatusResponse> responseObserver) {
        Map<String, String> params = parseRequest(request.getRequest());
        rplLogger.info("receive show slave status for channel: {}, params: {}", params.get(RplConstants.CHANNEL),
            params);

        List<RplStateMachine> stateMachines = listRplStateMachine(params.get(RplConstants.CHANNEL));
        // no need to check channel exist
        // mysql> show slave status for channel 'a';
        // Empty set (0.01 sec)

        for (RplStateMachine stateMachine : stateMachines) {
            rplLogger.info("show status for fsm, {}", stateMachine.getId());
            ReplicaMeta replicaMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
            RplService service = DbTaskMetaManager.getService(stateMachine.getId(), ServiceType.REPLICA_INC);
            List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
            RplTask slowestTask = findSlowestTask(tasks);
            List<String> positionDetails;
            if (StringUtils.isNotBlank(slowestTask.getPosition())) {
                positionDetails = CommonUtil.parsePosition(slowestTask.getPosition());
            } else {
                rplLogger.error("position of task is blank, fsm id: {}, task id: {}",
                    stateMachine.getId(), slowestTask.getId());
                positionDetails = CommonUtil.parsePosition(CommonUtil.getRplInitialPosition());
            }

            long skipCounter = 0;
            for (RplTask task : tasks) {
                if (StringUtils.isBlank(task.getStatistic())) {
                    continue;
                }
                StatisticUnit unit = JSON.parseObject(task.getStatistic(), StatisticUnit.class);
                skipCounter += unit.getSkipCounter();
            }

            String running = service.getStatus() == ServiceStatus.RUNNING.getValue() ? "Yes" : "No";
            Map<String, String> response = new HashMap<>();
            response.put("Master_Host", replicaMeta.getMasterHost());
            response.put("Master_User", replicaMeta.getMasterUser());
            response.put("Master_Port", Integer.toString(replicaMeta.getMasterPort()));
            response.put("Master_Log_File", positionDetails.get(0));
            response.put("Read_Master_Log_Pos", positionDetails.get(1));
            response.put("Relay_Log_File", positionDetails.get(0));
            response.put("Relay_Log_Pos", positionDetails.get(1));
            response.put("Relay_Master_Log_File", positionDetails.get(0));
            response.put("Slave_IO_Running", running);
            response.put("Slave_SQL_Running", running);
            response.put("Replicate_Do_DB", replicaMeta.getDoDb());
            response.put("Replicate_Ignore_DB", replicaMeta.getIgnoreDb());
            response.put("Replicate_Do_Table", replicaMeta.getDoTable());
            response.put("Replicate_Ignore_Table", replicaMeta.getIgnoreTable());
            response.put("Replicate_Wild_Do_Table", replicaMeta.getWildDoTable());
            response.put("Replicate_Wild_Ignore_Table", replicaMeta.getWildIgnoreTable());
            response.put("Last_Error", slowestTask.getLastError());
            response.put("Skip_Counter", String.valueOf(skipCounter));
            response.put("Exec_Master_Log_Pos", positionDetails.get(1));
            response.put("Until_Condition", "None");
            response.put("Master_SSL_Allowed", "No");
            response.put("Seconds_Behind_Master", String.valueOf(FSMMetaManager.computeTaskDelay(slowestTask)));
            response.put("Master_SSL_Verify_Server_Cert", "No");
            response.put("Replicate_Ignore_Server_Ids", replicaMeta.getIgnoreServerIds());
            response.put("SQL_Remaining_Delay", "NULL");
            response.put("Slave_SQL_Running_State", running);
            response.put("Auto_Position", "0");
            response.put("Replicate_Rewrite_DB", replicaMeta.getRewriteDb());
            response.put("Channel_Name", stateMachine.getChannel());
            response.put("Replicate_Mode", replicaMeta.isImageMode() ?
                RplConstants.IMAGE_MODE: RplConstants.INCREMENTAL_MODE);
            response.put("Running_Stage", FSMState.from(stateMachine.getState()).name());
            responseObserver
                .onNext(ShowSlaveStatusResponse.newBuilder().setResponse(JSON.toJSONString(response))
                    .build());
        }
        responseObserver.onCompleted();
    }

    public static void changeMaster(ChangeMasterRequest request, StreamObserver<RplCommandResponse> responseObserver) {
        try {
            Map<String, String> params = parseRequest(request.getRequest());
            rplLogger.info("receive change master for channel: {}, params: {}", params.get(RplConstants.CHANNEL),
                params);
            List<RplStateMachine> existStateMachines = listRplStateMachine(params.get(RplConstants.CHANNEL));
            if (!checkSlaveRunning(params, responseObserver)) {
                return;
            }
            if (StringUtils.isBlank(params.get(RplConstants.CHANNEL)) && existStateMachines.size() > 1) {
                rplLogger.error("This action does not support multiple channels, channel name: {}",
                    params.get(RplConstants.CHANNEL));
                setRpcRplCommandResponse(responseObserver, 1, MULTIPLE_CHANNELS_EXIST);
                return;
            }
            // 如未设置file和pos，则MASTER_LOG_FILE='' and MASTER_LOG_POS=4,意思是找所能找到的最早的binlog的开头
            if (existStateMachines.size() == 0) {
                ReplicaMeta replicaMeta = new ReplicaMeta();
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
                } else {
                    replicaMeta.setPosition(CommonUtil.getRplInitialPosition());
                }
                if (params.containsKey(RplConstants.IGNORE_SERVER_IDS)) {
                    // origin: (1,2)
                    // in db: 1,2
                    String ignoreServerIds = CommonUtil.removeBracket(params.get(RplConstants.IGNORE_SERVER_IDS));
                    replicaMeta.setIgnoreServerIds(ignoreServerIds);
                }
                replicaMeta.setMasterType(HostType.POLARX2);
                if (params.containsKey(RplConstants.SOURCE_HOST_TYPE)) {
                    if (StringUtils.equalsIgnoreCase("rds", params.get(RplConstants.SOURCE_HOST_TYPE))) {
                        replicaMeta.setMasterType(HostType.RDS);
                    } else if (StringUtils.equalsIgnoreCase("mysql", params.get(RplConstants.SOURCE_HOST_TYPE))) {
                        replicaMeta.setMasterType(HostType.MYSQL);
                    }
                }

                replicaMeta.setDoDb("");
                replicaMeta.setIgnoreDb("");
                replicaMeta.setDoTable("");
                replicaMeta.setIgnoreTable("");
                replicaMeta.setWildDoTable("");
                replicaMeta.setWildIgnoreTable("");
                replicaMeta.setRewriteDb("");

                rplLogger.info("receive change master for channel: {}, create new fsm from config: {}",
                    params.get(RplConstants.CHANNEL), replicaMeta);
                ReplicaFSM.getInstance().create(replicaMeta);

            } else {
                // rules:

                // 1. 如果我们设置MASTER_HOST 或 MASTER_PORT参数，则"无论如何"视为新的master
                // 2. 如果我们设置MASTER_LOG_FILE 与 MASTER_LOG_POS参数，则视为新的position
                // 清除位点上下文并修改fsm/service/task的元数据
                // 此时如未设置file和pos，则MASTER_LOG_FILE='0' and MASTER_LOG_POS=4,意思是找所能找到的最早的binlog的开头

                // 如果未采用新master且MASTER_LOG_FILE 与 MASTER_LOG_POS都没有指定，则使用上次保存的位置
                // 复用table_position和ddl表，修改fsm/service/task的元数据

                // 不支持MASTER_LOG_POS=0时自动匹配最新binlog offset，mysql8.0文档中无相关信息


                // assert that only 1 statemachine here
                RplStateMachine stateMachine = existStateMachines.get(0);
                ReplicaMeta replicaMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
                if (params.containsKey(RplConstants.MASTER_HOST)) {
                    if (StringUtils.equalsIgnoreCase(params.get(RplConstants.MASTER_HOST),
                        replicaMeta.getMasterHost())) {
                        params.remove(RplConstants.MASTER_HOST);
                    } else {
                        replicaMeta.setMasterHost(params.get(RplConstants.MASTER_HOST));
                    }
                }
                if (params.containsKey(RplConstants.MASTER_PORT)) {
                    if (StringUtils.equalsIgnoreCase(params.get(RplConstants.MASTER_PORT),
                        String.valueOf(replicaMeta.getMasterPort()))) {
                        params.remove(RplConstants.MASTER_PORT);
                    } else {
                        replicaMeta.setMasterPort(Integer.parseInt(params.get(RplConstants.MASTER_PORT)));
                    }
                }
                if (params.containsKey(RplConstants.MODE)) {
                    boolean tempImageMode =
                        StringUtils.equalsIgnoreCase(params.get(RplConstants.MODE), RplConstants.IMAGE_MODE);
                    if (tempImageMode == replicaMeta.imageMode) {
                        params.remove(RplConstants.MODE);
                    } else {
                        replicaMeta.setImageMode(tempImageMode);
                    }
                }
                if (params.containsKey(RplConstants.MASTER_USER)) {
                    replicaMeta.setMasterUser(params.get(RplConstants.MASTER_USER));
                }
                if (params.containsKey(RplConstants.MASTER_PASSWORD)) {
                    replicaMeta.setMasterPassword(params.get(RplConstants.MASTER_PASSWORD));
                }
                if (params.containsKey(RplConstants.MASTER_LOG_FILE)
                    && params.containsKey(RplConstants.MASTER_LOG_POS)) {
                    replicaMeta.setPosition(params.get(RplConstants.MASTER_LOG_FILE) + ":" +
                        params.get(RplConstants.MASTER_LOG_POS));
                } else {
                    replicaMeta.setPosition(CommonUtil.getRplInitialPosition());
                }
                if (params.containsKey(RplConstants.IGNORE_SERVER_IDS)) {
                    String ignoreServerIds = CommonUtil.removeBracket(params.get(RplConstants.IGNORE_SERVER_IDS));
                    replicaMeta.setIgnoreServerIds(ignoreServerIds);
                }

                replicaMeta.setMasterType(HostType.POLARX2);
                if (params.containsKey(RplConstants.SOURCE_HOST_TYPE)) {
                    if (StringUtils.equalsIgnoreCase("rds", params.get(RplConstants.SOURCE_HOST_TYPE))) {
                        replicaMeta.setMasterType(HostType.RDS);
                    } else if (StringUtils.equalsIgnoreCase("mysql", params.get(RplConstants.SOURCE_HOST_TYPE))) {
                        replicaMeta.setMasterType(HostType.MYSQL);
                    }
                }

                // 1. if host / port / mode modified, then remove all history (full and incremental),reset fsm state
                // and use new position
                // 2. if log file name / log file offset modified, then remove incremental history and use new position
                // 3. if above keep unchanged, update meta and do not use new position
                if (params.containsKey(RplConstants.MASTER_HOST)
                    || params.containsKey(RplConstants.MASTER_PORT) || params.containsKey(RplConstants.MODE)) {
                    // check if equal to now config
                    rplLogger.info("receive change master for channel: {}, update old fsm with new master or position "
                        + "from config: {}", params.get(RplConstants.CHANNEL), replicaMeta);
                    // clear all context about table position
                    FSMMetaManager.clearReplicaHistory(stateMachine, true);
                    FSMMetaManager.updateReplicaConfig(stateMachine, replicaMeta, true, true);
                } else if (params.containsKey(RplConstants.MASTER_LOG_FILE)
                    && params.containsKey(RplConstants.MASTER_LOG_POS)) {
                    FSMMetaManager.clearReplicaHistory(stateMachine, false);
                    FSMMetaManager.updateReplicaConfig(stateMachine, replicaMeta, true, false);
                } else {
                    rplLogger.info("receive change master for channel: {}, update old fsm without new position "
                        + "from config: {}", params.get(RplConstants.CHANNEL), replicaMeta);
                    FSMMetaManager.updateReplicaConfig(stateMachine, replicaMeta, false, false);
                }
            }
            setRpcRplCommandResponse(responseObserver, 0, "");
        } catch (Throwable e) {
            rplLogger.error("change master occurs exception:", e);
            setRpcRplCommandResponse(responseObserver, 1, e.getMessage());
        }
    }

    public static void changeReplicationFilter(ChangeReplicationFilterRequest request,
                                               StreamObserver<RplCommandResponse> responseObserver) {
        try {

            Map<String, String> params = parseRequest(request.getRequest());
            rplLogger.info("receive change replication filter for channel: {}, params: {}",
                params.get(RplConstants.CHANNEL), params);
            List<RplStateMachine> stateMachines = listRplStateMachine(params.get(RplConstants.CHANNEL));

            if (!checkSlaveRunning(params, responseObserver) || !checkSlaveExist(params, responseObserver) ||
                !checkSchemaTableName(params, responseObserver)) {
                return;
            }

            for (RplStateMachine stateMachine : stateMachines) {
                ReplicaMeta replicaMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
                rplLogger.info("receive change replication filter for stateMachine id: {}, old config: {}",
                    stateMachine.getId(), replicaMeta);
                if (params.containsKey(RplConstants.REPLICATE_DO_DB)) {
                    // origin: (full_src_1, rpl)  d
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
                rplLogger.info("receive change replication filter for stateMachine id: {}, new config: {}",
                    stateMachine.getId(), replicaMeta);
                // update config in task context
                FSMMetaManager.updateReplicaConfig(stateMachine, replicaMeta, false, true);
            }
            setRpcRplCommandResponse(responseObserver, 0, "");
        } catch (Throwable e) {
            rplLogger.error("change replication filter occurs exception:", e);
            setRpcRplCommandResponse(responseObserver, 1, e.getMessage());
        }
    }

    public static boolean checkSchemaTableName(Map<String, String> params,
                                        StreamObserver<RplCommandResponse> responseObserver) {
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String paramName = entry.getKey();
            String paramValue = entry.getValue();
            if (!StringUtils.equals(paramName, RplConstants.REPLICATE_WILD_DO_TABLE) &&
                !StringUtils.equals(paramName, RplConstants.REPLICATE_WILD_IGNORE_TABLE)) {
                entry.setValue(SQLUtils.normalize(paramValue));
            }
        }
        return true;
    }

    private static Map<String, String> parseRequest(String request) {
        return JSON.parseObject(request, new TypeReference<HashMap<String, String>>() {});
    }

    private static void setRpcRplCommandResponse(StreamObserver<RplCommandResponse> responseObserver, int resultCode,
                                                 String error) {
        responseObserver.onNext(RplCommandResponse.newBuilder().setResultCode(resultCode).setError(error).build());
        responseObserver.onCompleted();
    }

    private static RplTask findSlowestTask(List<RplTask> tasks) {
        if (tasks.size() == 0) {
            return null;
        }

        // if pos of one task is blank, return this task as slowest task
        for (RplTask task:tasks) {
            if (StringUtils.isBlank(task.getPosition())) {
                return task;
            }
        }

        RplTask slowest = tasks.get(0);
        for (int i = 1; i < tasks.size(); i++) {
            int compare = CommonUtil.comparePosition(slowest.getPosition(), tasks.get(i).getPosition());
            if (compare > 0) {
                slowest = tasks.get(i);
            } else if (compare == 0 && StringUtils.isNotBlank(tasks.get(i).getLastError())) {
                slowest = tasks.get(i);
            }
        }
        return slowest;
    }

    private static List<RplStateMachine> listRplStateMachine(String channel) {
        List<RplStateMachine> stateMachines = new ArrayList<>();

        if (StringUtils.isNotBlank(channel)) {
            RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
            if (stateMachine != null) {
                stateMachines.add(stateMachine);
            }
        } else {
            stateMachines = DbTaskMetaManager.listRplStateMachine();
        }
        return stateMachines;
    }


    private static boolean checkSlaveExist(Map<String, String> params,
                                           StreamObserver<RplCommandResponse> responseObserver) {
        List<RplStateMachine> stateMachines = listRplStateMachine(params.get(RplConstants.CHANNEL));
        if (StringUtils.isNotBlank(params.get(RplConstants.CHANNEL)) && stateMachines.isEmpty()) {
            setRpcRplCommandResponse(responseObserver, 1, CHANNEL_NOT_EXIST);
            rplLogger.error("replica fsm not exist for this channel name : {}", params.get(RplConstants.CHANNEL));
            return false;
        }
        return true;
    }

    private static boolean checkSlaveRunning(Map<String, String> params,
                                             StreamObserver<RplCommandResponse> responseObserver) {
        List<RplStateMachine> stateMachines = listRplStateMachine(params.get(RplConstants.CHANNEL));
        for (RplStateMachine stateMachine : stateMachines) {
            if (stateMachine.getStatus() == StateMachineStatus.RUNNING.getValue()) {
                rplLogger.error("replica fsm is running thus not support this action, channel name: {}",
                    params.get(RplConstants.CHANNEL));
                setRpcRplCommandResponse(responseObserver, 1, OPERATION_ON_RUNNING_SLAVE_ERROR);
                return false;
            }
        }
        return true;
    }

    // return first replica cluster
    private static String findActiveReplicaCluster() {
        List<NodeInfo> nodeInfoList = DbTaskMetaManager.listActiveClusterNode(ClusterTypeEnum.REPLICA.name());
        if (CollectionUtils.isEmpty(nodeInfoList)) {
            return "";
        } else {
            return nodeInfoList.get(0).getClusterId();
        }
    }
}
