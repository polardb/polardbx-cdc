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
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.daemon.rest.ann.ACL;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.fsmutil.ReplicaFSM;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;
import com.aliyun.polardbx.rpl.taskmeta.RplServiceManager;
import com.aliyun.polardbx.rpl.validation.fullvalid.task.ReplicaFullValidTaskManager;
import com.sun.jersey.spi.resource.Singleton;
import lombok.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.util.List;
import java.util.Map;

import static com.aliyun.polardbx.rpl.taskmeta.RplServiceManager.findActiveReplicaCluster;

@Path("/replica")
@Produces(MediaType.APPLICATION_JSON)
@ACL
@Singleton
public class ReplicaApiResource {
    private static final Logger logger = LoggerFactory.getLogger(ReplicaApiResource.class);

    private static final Logger fullValidLogger = LoggerFactory.getLogger("fullValidLogger");

    @POST
    @Path("/changeMaster")
    public ResultCode<?> changeMaster(Map<String, String> params) {
        logger.info("changeMaster: " + params);
        return RplServiceManager.changeMaster(params);
    }

    @POST
    @Path("/changeReplicationFilter")
    public String changeReplicationFilter(Map<String, String> params) {
        logger.info("changeReplicationFilter: " + params);
        return JSON.toJSONString(RplServiceManager.changeReplicationFilter(params));
    }

    @POST
    @Path("/showSlaveStatus")
    public ResultCode<?> showSlaveStatus(Map<String, String> params) {
        logger.info("showSlaveStatus: " + params);
        return RplServiceManager.showSlaveStatus(params);
    }

    @POST
    @Path("/resetSlave")
    public ResultCode<?> resetSlave(Map<String, String> params) {
        logger.info("resetSlave: " + params);
        return RplServiceManager.resetSlave(params);
    }

    @POST
    @Path("/startSlave")
    public ResultCode<?> startSlave(Map<String, String> params) {
        logger.info("startSlave: " + params);
        return RplServiceManager.startSlave(params);
    }

    @POST
    @Path("/stopSlave")
    public ResultCode<?> stopSlave(Map<String, String> params) {
        logger.info("stopSlave: " + params);
        return RplServiceManager.stopSlave(params);
    }

    @POST
    @Path("/service/create")
    public ResultCode<?> create(@QueryParam("masterHost") @NonNull String masterHost,
                                @QueryParam("masterPort") @NonNull Integer masterPort,
                                @QueryParam("masterUser") @NonNull String masterUser,
                                @QueryParam("masterPassword") @NonNull String masterPassword,
                                @QueryParam("serverId") @NonNull String serverId,
                                @QueryParam("ignoreServerIds") @NonNull String ignoreServerIds,
                                @QueryParam("doDb") String doDb,
                                @QueryParam("ignoreDb") String ignoreDb,
                                @QueryParam("doTable") String doTable,
                                @QueryParam("ignoreTable") String ignoreTable,
                                @QueryParam("imageMode") @NonNull Boolean imageMode,
                                @QueryParam("enableDdl") @NonNull Boolean enableDdl,
                                @QueryParam("streamGroup") String streamGroup,
                                @QueryParam("channel") String channel,
                                @QueryParam("binlogPosition") String position) {
        RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
        if (null != stateMachine) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("should run reset slave all for this channel")
                .data(RplConstants.FAILURE).build();
        }
        ReplicaMeta replicaMeta = new ReplicaMeta();
        replicaMeta.setMasterUser(masterUser);
        replicaMeta.setMasterPassword(masterPassword);
        replicaMeta.setServerId(serverId);
        replicaMeta.setIgnoreServerIds(ignoreServerIds);
        replicaMeta.setDoDb(doDb);
        replicaMeta.setIgnoreDb(ignoreDb);
        replicaMeta.setDoTable(doTable);
        replicaMeta.setIgnoreTable(ignoreTable);
        replicaMeta.setImageMode(imageMode);
        replicaMeta.setMasterHost(masterHost);
        replicaMeta.setMasterPort(masterPort);
        replicaMeta.setEnableDdl(enableDdl);
        replicaMeta.setStreamGroup(streamGroup);
        replicaMeta.setEnableDynamicMasterHost(true);
        replicaMeta.setMasterType(HostType.POLARX2);
        replicaMeta.setClusterId(findActiveReplicaCluster());
        replicaMeta.setChannel(channel);
        replicaMeta.setPosition(position);

        long fsmId = ReplicaFSM.getInstance().create(replicaMeta);
        if (fsmId < 0) {
            logger.error("create replica fsm error, " + JSON.toJSONString(replicaMeta));
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("create replica fsm error")
                .data(RplConstants.FAILURE).build();
        }
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    // 除了创建和meta control 别的都是直接修改rpl task 和  rpl task config

    // meta control: 修改 dynamic 情况下的 meta master host/port
    @POST
    @Path("/service/modifyMetaComputeNode/{channel}")
    public ResultCode<?> modifyMetaComputeNode(@PathParam("channel") String channel,
                                               @QueryParam("masterHost") @NonNull String masterHost,
                                               @QueryParam("masterPort") @NonNull Integer masterPort,
                                               @QueryParam("masterUser") String masterUser,
                                               @QueryParam("masterPassword") String masterPassword) {
        RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
        if (null == stateMachine) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("do not exist replica task with this channel")
                .data(RplConstants.FAILURE).build();
        }
        ReplicaMeta replicaMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
        replicaMeta.setMasterHost(masterHost);
        replicaMeta.setMasterPort(masterPort);
        if (masterUser != null) {
            replicaMeta.setMasterUser(masterUser);
        }
        if (masterPassword != null) {
            replicaMeta.setMasterPassword(masterPassword);
        }
        String config = JSON.toJSONString(replicaMeta);
        RplStateMachine newStateMachine = new RplStateMachine();
        newStateMachine.setId(stateMachine.getId());
        newStateMachine.setConfig(config);
        DbTaskMetaManager.updateStateMachine(newStateMachine);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    // 批量修改生成的host info
    @POST
    @Path("/service/generateDynamicHostInfo/{channel}")
    public ResultCode<?> generateDynamicHostInfo(@PathParam("channel") String channel) {
        RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
        if (null == stateMachine) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("do not exist replica task with this channel")
                .data(RplConstants.FAILURE).build();
        }
        RplServiceManager.generateDynamicHostInfo(stateMachine);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    // 批量修改channel所有子任务位点到最新位点
    @POST
    @Path("/service/modifyPosition/{channel}")
    public ResultCode<?> modifyPosition(@PathParam("channel") String channel) {
        RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
        if (null == stateMachine) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("do not exist replica task with this channel")
                .data(RplConstants.FAILURE).build();
        }
        RplServiceManager.generateLatestPosition(stateMachine);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    // 修改子任务位点 taskId and position
    @POST
    @Path("/task/modifyTaskPosition/{taskId}")
    public ResultCode<?> modifyTaskPosition(@PathParam("taskId") Long taskId,
                                            @QueryParam("masterLogFile") @NonNull String masterLogFile,
                                            @QueryParam("masterLogPos") @NonNull Integer masterLogPos) {

        DbTaskMetaManager.updateBinlogPosition(taskId, masterLogFile + ":" + masterLogPos);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    // 这几个直接走原来的命令
    // 查询报错

    // 修改过滤条件 dodb / ignoredb / dotable / ignore table

    // 启动该channel所有任务

    // 暂停该channel所有任务

    // 直接update config即可 并且要set进meta
    @POST
    @Path("/service/modifyEnableDdl/{channel}")
    public ResultCode<?> modifyEnableDdl(@PathParam("channel") String channel,
                                         @QueryParam("enableDdl") @NonNull Boolean enableDdl) {
        RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
        if (null == stateMachine) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("do not exist replica task with this channel")
                .data(RplConstants.FAILURE).build();
        }
        List<RplTask> tasks = DbTaskMetaManager.listTaskByStateMachine(stateMachine.getId());
        for (RplTask task : tasks) {
            ReplicaMeta privateMeta = RplServiceManager.extractMetaFromTaskConfig(task);
            privateMeta.setEnableDdl(enableDdl);
            FSMMetaManager.updateReplicaTaskConfig(task, privateMeta, false);
        }
        ReplicaMeta commonMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
        commonMeta.setEnableDdl(enableDdl);
        stateMachine.setConfig(JSON.toJSONString(commonMeta));
        DbTaskMetaManager.updateStateMachine(stateMachine);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @POST
    @Path("/service/modifyServerId/{channel}")
    public ResultCode<?> modifyServerId(@PathParam("channel") String channel,
                                        @QueryParam("serverId") @NonNull String serverId) {
        RplStateMachine stateMachine = DbTaskMetaManager.getRplStateMachine(channel);
        if (null == stateMachine) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("do not exist replica task with this channel")
                .data(RplConstants.FAILURE).build();
        }
        List<RplTask> tasks = DbTaskMetaManager.listTaskByStateMachine(stateMachine.getId());
        for (RplTask task : tasks) {
            ReplicaMeta privateMeta = RplServiceManager.extractMetaFromTaskConfig(task);
            privateMeta.setServerId(serverId);
            FSMMetaManager.updateReplicaTaskConfig(task, privateMeta, false);
        }
        ReplicaMeta commonMeta = JSON.parseObject(stateMachine.getConfig(), ReplicaMeta.class);
        commonMeta.setServerId(serverId);
        stateMachine.setConfig(JSON.toJSONString(commonMeta));
        DbTaskMetaManager.updateStateMachine(stateMachine);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @POST
    @Path("/fullValidation/create")
    public ResultCode<?> createFullValidTask(Map<String, String> params) {
        fullValidLogger.info("create full validation task, params:{}", params);
        return ReplicaFullValidTaskManager.createTask(params.get(RplConstants.CHANNEL),
            params.get(RplConstants.RPL_FULL_VALID_DB), params.get(RplConstants.RPL_FULL_VALID_TB),
            params.get(RplConstants.RPL_FULL_VALID_MODE));
    }

    @POST
    @Path("/fullValidationSchema/create")
    public ResultCode<?> createFullValidSchemaTask(Map<String, String> params) {
        fullValidLogger.info("create full validation schema task, params:{}", params);
        return ReplicaFullValidTaskManager.createSchemaCheckTask(params.get(RplConstants.CHANNEL));
    }

    @POST
    @Path("/fullValidation/cancel")
    public ResultCode<?> cancelFullValidTask(Map<String, String> params) {
        fullValidLogger.info("cancel full validation task, params:{}", params);
        return ReplicaFullValidTaskManager.cancelTask(params.get(RplConstants.RPL_FULL_VALID_DB),
            params.get(RplConstants.RPL_FULL_VALID_TB));
    }

    @POST
    @Path("/fullValidationSchema/cancel")
    public ResultCode<?> cancelFullValidSchemaTask() {
        fullValidLogger.info("cancel full validation schema task");
        return ReplicaFullValidTaskManager.cancelSchemaCheckTask();
    }

    @POST
    @Path("/fullValidation/pause")
    public ResultCode<?> pauseFullValidTask(Map<String, String> params) {
        fullValidLogger.info("pause full validation task, params:{}", params);
        return ReplicaFullValidTaskManager.pauseTask(params.get(RplConstants.RPL_FULL_VALID_DB),
            params.get(RplConstants.RPL_FULL_VALID_TB));
    }

    @POST
    @Path("/fullValidationSchema/pause")
    public ResultCode<?> pauseFullValidSchemaTask() {
        fullValidLogger.info("pause full validation schema task");
        return ReplicaFullValidTaskManager.pauseSchemaCheckTask();
    }

    @POST
    @Path("/fullValidation/continue")
    public ResultCode<?> continueFullValidTask(Map<String, String> params) {
        fullValidLogger.info("continue full validation task, params:{}", params);
        return ReplicaFullValidTaskManager.continueTask(params.get(RplConstants.RPL_FULL_VALID_DB),
            params.get(RplConstants.RPL_FULL_VALID_TB));
    }

    @POST
    @Path("/fullValidationSchema/continue")
    public ResultCode<?> continueFullValidSchemaTask() {
        fullValidLogger.info("continue full validation schema task");
        return ReplicaFullValidTaskManager.continueSchemaCheckTask();
    }

    @POST
    @Path("/fullValidation/reset")
    public ResultCode<?> resetFullValidTask(Map<String, String> params) {
        fullValidLogger.info("reset full validation task, params:{}", params);
        return ReplicaFullValidTaskManager.resetTask(params.get(RplConstants.RPL_FULL_VALID_DB),
            params.get(RplConstants.RPL_FULL_VALID_TB));
    }

    @POST
    @Path("/fullValidationSchema/reset")
    public ResultCode<?> resetFullValidSchemaTask() {
        fullValidLogger.info("reset full validation schema task");
        return ReplicaFullValidTaskManager.resetSchemaCheckTask();
    }

    @POST
    @Path("/fullValidation/progress")
    public ResultCode<?> showFullValidProgress(Map<String, String> params) {
        fullValidLogger.info("show full validation progress, params:{}", params);
        return ReplicaFullValidTaskManager.showProgress(params.get(RplConstants.RPL_FULL_VALID_DB),
            params.get(RplConstants.RPL_FULL_VALID_TB));
    }
}
