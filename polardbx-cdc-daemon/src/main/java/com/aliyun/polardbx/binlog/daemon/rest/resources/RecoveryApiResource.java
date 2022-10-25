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
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.rest.ann.ACL;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.FlashBackCleanFileParameter;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.FlashBackTaskParameter;
import com.aliyun.polardbx.binlog.daemon.rest.resources.response.SqlFlashBackExecuteInfo;
import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.ServerInfo;
import com.aliyun.polardbx.rpl.common.ResultCode;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMState;
import com.aliyun.polardbx.rpl.common.fsmutil.RecoveryFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.ServiceDetail;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.RecoveryMeta;
import com.aliyun.polardbx.rpl.taskmeta.RecoveryStateMachineContext;
import com.aliyun.polardbx.rpl.taskmeta.ServiceStatus;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.taskmeta.StateMachineStatus;
import com.aliyun.polardbx.rpl.taskmeta.TaskStatus;
import com.sun.jersey.spi.resource.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.ConfigKeys.FLASHBACK_BINLOG_FILES_COUNT_PER_TASK;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.instType;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.status;
import static com.aliyun.polardbx.rpl.common.RplConstants.FLASH_BACK_ROOT_PATH;
import static com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager.getTaskSpecificDetail;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * @author fanfei
 */
@Path("/v1/flashback")
@Produces(MediaType.APPLICATION_JSON)
@ACL
@Singleton
@Slf4j
public class RecoveryApiResource {

    private static final Logger logger = LoggerFactory.getLogger(RecoveryApiResource.class);

    @POST
    @Path("/service/create")
    public ResultCode create(FlashBackTaskParameter request) {
        logger.warn("msg: " + JSON.toJSONString(request));
        try {
            RecoveryMeta recoveryMeta = new RecoveryMeta();

            ServerInfoMapper serverInfoMapper = SpringContextHolder.getObject(ServerInfoMapper.class);
            List<ServerInfo> serverInfoList = serverInfoMapper.select(c ->
                c.where(instType, isEqualTo(0))//0:master, 1:read without htap, 2:read with htap
                    .and(status, isEqualTo(0))//0: ready, 1: not_ready, 2: deleting
            );
            String hostIp = serverInfoList.get(0).getIp();
            recoveryMeta.setHost(hostIp);
            Integer port = serverInfoList.get(0).getPort();
            recoveryMeta.setPort(port);
            String user = DynamicApplicationConfig.getString(ConfigKeys.POLARX_USERNAME);
            recoveryMeta.setUser(user);
            String pwd = DynamicApplicationConfig.getString(ConfigKeys.POLARX_PASSWORD);
            recoveryMeta.setPwd(pwd);

            recoveryMeta.setSchema(request.getLogicDbName());
            recoveryMeta.setMirror(request.isMirror());
            recoveryMeta.setStartTimestamp(request.getStartTimestamp());
            recoveryMeta.setEndTimestamp(request.getEndTimestamp());
            recoveryMeta.setSqlType(request.getSqlType());
            recoveryMeta.setTraceId(request.getTraceId());
            recoveryMeta.setTable(request.getLogicTableName());
            recoveryMeta.setInjectTrouble(request.isInjectTrouble());
            recoveryMeta.setBinlogFilesCountPerTask(DynamicApplicationConfig.getInt(
                FLASHBACK_BINLOG_FILES_COUNT_PER_TASK));

            long fsmId = RecoveryFSM.getInstance().create(recoveryMeta);
            logger.info("create recovery fsm id: " + fsmId);
            if (fsmId < 0) {
                logger.error("create recovery fsm error, " + JSON.toJSONString(recoveryMeta));
                return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("create recovery fsm error")
                    .data(RplConstants.FAILURE).build();
            }
            return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(fsmId).build();
        } catch (Exception e) {
            logger.error("create error!", e);
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg(e.getMessage()).data(RplConstants.FAILURE)
                .build();
        }
    }

    @POST
    @Path("/service/start/{fsmId}")
    public ResultCode start(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive start fsm request " + fsmId);
        return FSMMetaManager.startStateMachine(fsmId);
    }

    @POST
    @Path("/service/stop/{fsmId}")
    public ResultCode stop(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive stop fsm request " + fsmId);
        return FSMMetaManager.stopStateMachine(fsmId);
    }

    @POST
    @Path("/service/delete/{fsmId}")
    public ResultCode delete(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive delete fsm request " + fsmId);
        return FSMMetaManager.deleteStateMachine(fsmId);
    }

    @POST
    @Path("task/memory/{taskId}")
    public ResultCode setMemory(@PathParam("taskId") Long taskId, @QueryParam("memory") Integer memory) {
        logger.warn("receive set memory request for task: " + taskId);
        return FSMMetaManager.setMemory(taskId, memory);
    }

    @GET
    @Path("/service/detail/{fsmId}")
    public ResultCode detail(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive detail fsm request " + fsmId);
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
        if (stateMachine == null) {
            log.error("from id: {} , fsm not found", fsmId);
            return new ResultCode(RplConstants.FAILURE_CODE, "fsm not found", RplConstants.FAILURE);
        }
        ResultCode<SqlFlashBackExecuteInfo> returnResult = new ResultCode(RplConstants.SUCCESS_CODE, "success");
        SqlFlashBackExecuteInfo info = new SqlFlashBackExecuteInfo();

        //基础信息
        info.setFsmId(stateMachine.getId());
        info.setFsmState(FSMState.from(stateMachine.getState()).name());
        info.setFsmStatus(StateMachineStatus.from(stateMachine.getStatus()).name());
        info.setServiceDetailList(new ArrayList<>());
        List<RplService> services = DbTaskMetaManager.listService(fsmId);
        for (RplService service : services) {
            ServiceDetail serviceDetail = new ServiceDetail();
            serviceDetail.setId(service.getId());
            serviceDetail.setStatus(ServiceStatus.nameFrom(service.getStatus()));
            serviceDetail.setType(ServiceType.from(service.getServiceType()).name());
            serviceDetail.setTaskDetailList(new ArrayList<>());
            List<RplTask> tasks = DbTaskMetaManager.listTaskByService(service.getId());
            for (RplTask task : tasks) {
                serviceDetail.getTaskDetailList().add(getTaskSpecificDetail(task));
            }
            info.getServiceDetailList().add(serviceDetail);
        }
        setProgress(info);

        if (StringUtils.isNotBlank(stateMachine.getContext())) {
            RecoveryStateMachineContext context =
                JSONObject.parseObject(stateMachine.getContext(), RecoveryStateMachineContext.class);
            info.setDownloadUrl(context.getDownloadUrl());
            info.setExpireTime(context.getExpireTime().getTime());
            info.setFilePathPrefix(context.getFileDirectory());
            info.setFileStorageType(context.getFileStorageType());
            info.setSqlCounter(context.getSqlCounter());
        }

        returnResult.setData(info);
        return returnResult;
    }

    @POST
    @Path("/service/cleanfile")
    public ResultCode clean(FlashBackCleanFileParameter request) {
        logger.warn("receive clean result file request " + request.getPathPrefix());
        if (StringUtils.isBlank(request.getPathPrefix())) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("empty path prefix")
                .data(false)
                .build();
        }
        if (!StringUtils.startsWith(request.getPathPrefix(), FLASH_BACK_ROOT_PATH)) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("invalid path prefix " + request.getPathPrefix())
                .data(false)
                .build();
        }

        RemoteBinlogProxy.getInstance().deleteAll(request.getPathPrefix());
        boolean result = RemoteBinlogProxy.getInstance().isObjectsExistForPrefix(request.getPathPrefix());
        if (!result) {
            return ResultCode.builder().code(RplConstants.SUCCESS_CODE).data(true).build();
        } else {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE)
                .msg("delete files failed " + request.getPathPrefix())
                .data(false)
                .build();
        }
    }

    private void setProgress(SqlFlashBackExecuteInfo info) {
        AtomicInteger totalTaskCount = new AtomicInteger();
        AtomicInteger finishedTaskCount = new AtomicInteger();
        info.getServiceDetailList().forEach(s -> {
            s.getTaskDetailList().forEach(t -> {
                totalTaskCount.incrementAndGet();
                if (TaskStatus.FINISHED.name().equals(t.getStatus())) {
                    finishedTaskCount.incrementAndGet();
                }
            });
        });

        BigDecimal decimal = new BigDecimal(finishedTaskCount.doubleValue() / totalTaskCount.doubleValue());
        double progress = decimal.setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
        info.setProgress(progress);
    }

    public static void main(String args[]) {
        FlashBackCleanFileParameter parameter = new FlashBackCleanFileParameter();
        parameter.setPathPrefix("SQL_FLASH_BACK/5c5332d7-4fbe-44f4-af14-3589d0224219/");
        System.out.println(JSONObject.toJSONString(parameter));
    }
}
