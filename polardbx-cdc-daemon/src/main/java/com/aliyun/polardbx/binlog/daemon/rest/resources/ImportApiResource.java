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
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.ServerConfigUtil;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.TopologyManager;
import com.aliyun.polardbx.binlog.daemon.rest.ann.ACL;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ConnectionInfo;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ImportTaskConfig;
import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.ServerInfo;
import com.aliyun.polardbx.rpl.common.ResultCode;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.fsmutil.DataImportFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMState;
import com.aliyun.polardbx.rpl.taskmeta.CdcExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorType;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FilterType;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.RdsExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.taskmeta.StateMachineStatus;
import com.aliyun.polardbx.rpl.taskmeta.ValidationExtractorConfig;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.sun.jersey.spi.resource.Singleton;
import lombok.Data;
import org.apache.commons.lang3.math.NumberUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.instType;
import static com.aliyun.polardbx.binlog.dao.ServerInfoDynamicSqlSupport.status;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

@Path("/v1/import")
@Produces(MediaType.APPLICATION_JSON)
@ACL
@Singleton
public class ImportApiResource {
    private static final Logger logger = LoggerFactory.getLogger(ImportApiResource.class);

    public ImportApiResource() {
        System.out.println("constructor importApiResource");
    }

    @POST
    @Path("/service/create")
    public ResultCode create(ImportTaskConfig config) {
        logger.warn("msg : " + JSON.toJSONString(config));
        try {
            DataImportMeta importMeta = new DataImportMeta();
            List<DataImportMeta.PhysicalMeta> metaList = new ArrayList<>();
            ServerInfoMapper serverInfoMapper = SpringContextHolder.getObject(ServerInfoMapper.class);
            List<ServerInfo> serverInfoList = serverInfoMapper.select(c ->
                c.where(instType, isEqualTo(0))//0:master, 1:read without htap, 2:read with htap
                    .and(status, isEqualTo(0))//0: ready, 1: not_ready, 2: deleting
            );
            String dstIp = serverInfoList.get(0).getIp();
            Integer dstPort = serverInfoList.get(0).getPort();
            String dstUser = DynamicApplicationConfig.getString(ConfigKeys.POLARX_USERNAME);
            String dstPwd = DynamicApplicationConfig.getString(ConfigKeys.POLARX_PASSWORD);

            // TopologyManager tm = new TopologyManagerImpl(config.getRules());
            Constructor<?> tmConstructor =
                Class.forName("com.aliyun.polardbx.binlog.TopologyManagerImpl").getConstructor(String.class);
            TopologyManager tm = (TopologyManager) tmConstructor.newInstance(config.getRules());
            importMeta.setAllTableList(config.getTableList().stream().map(String::toLowerCase).collect(Collectors.toList()));
            importMeta.setRules(config.getRules());
            importMeta.setSupportXa(true);
            importMeta.setCdcClusterId(config.getClusterId());
            int drdsServerId = Math.abs(Objects.hash(config.getRules()));
            int polarxServerId = Math.abs(new Long(ServerConfigUtil.getGlobalNumberVar("SERVER_ID")).intValue());
            if (drdsServerId == polarxServerId) {
                drdsServerId = (drdsServerId - 1) > 0 ? (drdsServerId - 1) : (drdsServerId + 1);
            }
            List<String> logicTableList = config.getTableList();
            for (ConnectionInfo connectionInfo : config.getSrcPhyConnList()) {
                DataImportMeta.PhysicalMeta meta = new DataImportMeta.PhysicalMeta();
                meta.setDstDb(config.getDstDbName().toLowerCase());
                meta.setDstHost(dstIp);
                meta.setDstPort(dstPort);
                meta.setDstUser(dstUser);
                meta.setDstPassword(dstPwd);
                meta.setDstType(HostType.POLARX2);
                meta.setSrcDbList(Sets.newHashSet(connectionInfo.getDbNameList()));
                Map<String, Set<String>> doTables = Maps.newHashMap();
                for (String dbName : connectionInfo.getDbNameList()) {
                    List<String> phyTableList = tm.getAllPhyTableList(dbName, new HashSet<>(logicTableList));
                    phyTableList = phyTableList.stream().map(String::toLowerCase).collect(Collectors.toList());
                    dbName = dbName.toLowerCase();
                    doTables.put(dbName, new HashSet<>(phyTableList));
                }
                // each instance should have same logical table list
                meta.setLogicalTableList(logicTableList);
                meta.setAllowTableList(doTables);
                meta.setSrcHost(connectionInfo.getIp());
                meta.setSrcPort(connectionInfo.getPort());
                meta.setSrcUser(connectionInfo.getUser());
                meta.setSrcPassword(connectionInfo.getPwd());
                meta.setDenyTableList(Sets.newHashSet());
                meta.setSrcType(HostType.RDS);
                meta.setIgnoreServerIds(polarxServerId + "");
                meta.setDstServerId(drdsServerId);
                meta.setRdsBid(config.getRdsBid());
                meta.setRdsUid(config.getRdsUid());
                meta.setRdsInstanceId(connectionInfo.getDbInstanceId());

                Map<String, String> rewriteTableMap = Maps.newHashMap();
                for (Set<String> phyTabs : doTables.values()) {
                    for (String pt : phyTabs) {
                        pt = pt.toLowerCase();
                        rewriteTableMap.put(pt, tm.getLogicTable(pt));
                    }
                }
                meta.setRewriteTableMapping(rewriteTableMap);
                metaList.add(meta);
            }
            importMeta.setMetaList(metaList);
            ConnectionInfo drdsConn = config.getSrcConn();
            importMeta.setBackflowHost(drdsConn.getIp());
            importMeta.setBackflowPort(drdsConn.getPort());
            importMeta.setBackflowUser(drdsConn.getUser());
            importMeta.setBackflowTableList(logicTableList);
            importMeta.setBackflowPwd(drdsConn.getPwd());
            importMeta.setBackflowDbName(config.getDstDbName());
            importMeta.setBackflowDstDbName(config.getSrcDbName());
            importMeta.setBackflowServerId(polarxServerId);
            importMeta.setBackflowIgnoreServerId(drdsServerId);
            importMeta.setBackflowType(HostType.POLARX1);
            long fsmId = DataImportFSM.getInstance().create(importMeta);
            logger.warn("create import fsm id : " + fsmId);
            logger.warn("import meta:" + JSON.toJSONString(importMeta));
            if (fsmId < 0) {
                logger.error("create import fsm error , " + JSON.toJSONString(importMeta));
                return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("create import fsm error")
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
    @Path("/service/restart/{fsmId}")
    public ResultCode restart(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive restart fsm request " + fsmId);
        return FSMMetaManager.restartStateMachine(fsmId);
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
    @Path("/service/backflow/start/{fsmId}")
    public ResultCode startBackflow(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive start backflow fsm request, fsm id: {}", fsmId);
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
        if (stateMachine == null) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("fsm not exist")
                .data(RplConstants.FAILURE).build();
        }
        if (stateMachine.getState() != FSMState.RECON_FINISHED_CATCH_UP.getValue()) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("state not support this action")
                .data(RplConstants.FAILURE).build();
        }
        if (stateMachine.getStatus() != StateMachineStatus.RUNNING.getValue()) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("fsm not running")
                .data(RplConstants.FAILURE).build();
        }
        DataImportFSM.getInstance().updateState(fsmId, FSMState.BACK_FLOW);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @POST
    @Path("/service/backflow/stop/{fsmId}")
    public ResultCode stopBackflow(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive stop backflow fsm request, fsm id: {}", fsmId);
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
        if (stateMachine == null) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("fsm not exist")
                .data(RplConstants.FAILURE).build();
        }
        if (stateMachine.getState() != FSMState.BACK_FLOW.getValue()) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("state not support this action")
                .data(RplConstants.FAILURE).build();
        }
        if (stateMachine.getStatus() != StateMachineStatus.RUNNING.getValue()) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("fsm not running")
                .data(RplConstants.FAILURE).build();
        }
        DataImportFSM.getInstance().updateState(fsmId, FSMState.RECON_FINISHED_CATCH_UP);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @GET
    @Path("/service/detail/{fsmId}")
    public ResultCode detail(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive detail fsm request " + fsmId);
        return FSMMetaManager.getStateMachineDetail(fsmId);
    }

    @Deprecated
    @POST
    @Path("/service/setupCrossCheck/{fsmId}")
    public ResultCode setupCrossCheck(@PathParam("fsmId") Long fsmId) {
        logger.info("Received setupCrossCheck request. fsmId: {}", fsmId);
        RplService backFlowService = DbTaskMetaManager.getService(fsmId, ServiceType.CDC_INC);
        RplTask backFlowTask = DbTaskMetaManager.listTaskByService(backFlowService.getId()).get(0);
        RplTaskConfig taskConfig = DbTaskMetaManager.getTaskConfig(backFlowTask.getId());
        ExtractorConfig cdcExtractorConfig = JSON.parseObject(taskConfig.getExtractorConfig(), CdcExtractorConfig.class);
        ValidationExtractorConfig validationExtractorConfig = new ValidationExtractorConfig();
        validationExtractorConfig.setExtractorType(ExtractorType.FULL_VALIDATION_CROSSCHECK.getValue());
        validationExtractorConfig.setFilterType(FilterType.NORMAL_FILTER.getValue());
        validationExtractorConfig.setParallelCount(4);
        validationExtractorConfig.setHostInfo(cdcExtractorConfig.getHostInfo());
        validationExtractorConfig.setSourceToTargetConfig(cdcExtractorConfig.getSourceToTargetConfig());

        DataImportMeta.PhysicalMeta importMeta = JSON.parseObject(
            validationExtractorConfig.getSourceToTargetConfig(), DataImportMeta.PhysicalMeta.class);

        // It should only have one db, which is the logical db
        String db = importMeta.getSrcDbList().stream().findFirst().get();
        importMeta.setLogicalTableList(new ArrayList<>(importMeta.getAllowTableList().get(db)));
        validationExtractorConfig.setSourceToTargetConfig(JSON.toJSONString(importMeta));

        // Add validation crosscheck config service and task
        RplService valServiceCrossCheck = DbTaskMetaManager.addService(backFlowService.getStateMachineId(), ServiceType.FULL_VALIDATION_CROSSCHECK, new ArrayList<>());
        RplTask rplTask = DbTaskMetaManager.addTask(backFlowService.getStateMachineId(),
            valServiceCrossCheck.getId(),
            JSON.toJSONString(validationExtractorConfig),
            taskConfig.getPipelineConfig(),
            taskConfig.getApplierConfig(),
            ServiceType.from(valServiceCrossCheck.getServiceType()), 0, backFlowTask.getClusterId());
        logger.info("Finished setting up CrossCheckValidation, service: {}, task: {}", valServiceCrossCheck.getId(), rplTask.getId());

        // Add reconciliation crosscheck
        validationExtractorConfig.setExtractorType(ExtractorType.RECONCILIATION_CROSSCHECK.getValue());
        RplService reconXCheckService = DbTaskMetaManager.addService(backFlowService.getStateMachineId(), ServiceType.RECONCILIATION_CROSSCHECK, new ArrayList<>());
        RplTask reconXCheckTask = DbTaskMetaManager.addTask(backFlowService.getStateMachineId(),
            reconXCheckService.getId(),
            JSON.toJSONString(validationExtractorConfig),
            taskConfig.getPipelineConfig(),
            taskConfig.getApplierConfig(),
            ServiceType.from(reconXCheckService.getServiceType()), 0, backFlowTask.getClusterId());
        logger.info("Finished setting up XCheckReconciliation, service: {}, task: {}", reconXCheckService.getId(), reconXCheckTask.getId());

        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @POST
    @Path("/service/addtaskconfig/{fsmId}")
    public ResultCode addTaskConfig(@PathParam("fsmId") Long fsmId) {
        FSMMetaManager.addTaskConfig(fsmId);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @POST
    @Path("service/converttobyte/start/{fsmId}")
    public ResultCode startConvertToByte(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive start convert to byte task request " + fsmId);
        return FSMMetaManager.setConvertToByte(fsmId, true);
    }

    @POST
    @Path("service/converttobyte/stop/{fsmId}")
    public ResultCode stopConvertToByte(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive stop convert to byte task request " + fsmId);
        return FSMMetaManager.setConvertToByte(fsmId, false);
    }

    @POST
    @Path("/task/setossinstanceid/{taskId}/{instanceId}")
    public ResultCode setOssInstanceId(@PathParam("taskId") Long taskId,
                                       @PathParam("instanceId") Long instanceId) {
        logger.warn("receive add rds info request for task" + taskId);
        RplTask task = DbTaskMetaManager.getTask(taskId);
        RplTaskConfig taskConfig = DbTaskMetaManager.getTaskConfig(task.getId());
        RdsExtractorConfig config = JSON.parseObject(taskConfig.getExtractorConfig(), RdsExtractorConfig.class);
        config.setFixHostInstanceId(instanceId);
        DbTaskMetaManager.updateTaskConfig(task.getId(), JSON.toJSONString(config), null, null);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @POST
    @Path("/task/removeossinstanceid/{taskId}/{instanceId}")
    public ResultCode removeOssInstanceId(@PathParam("taskId") Long taskId) {
        logger.warn("receive remove rds info request for task" + taskId);
        RplTask task = DbTaskMetaManager.getTask(taskId);
        RplTaskConfig taskConfig = DbTaskMetaManager.getTaskConfig(task.getId());
        RdsExtractorConfig config = JSON.parseObject(taskConfig.getExtractorConfig(), RdsExtractorConfig.class);
        config.setFixHostInstanceId(null);
        DbTaskMetaManager.updateTaskConfig(task.getId(), JSON.toJSONString(config), null, null);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }


    @POST
    @Path("task/exception/skip/start/{taskId}")
    public ResultCode startSkipException(@PathParam("taskId") Long taskId) {
        logger.warn("receive start skipException task request " + taskId);
        return FSMMetaManager.startSkipException(taskId);
    }

    @POST
    @Path("task/exception/skip/stop/{taskId}")
    public ResultCode stopSkipException(@PathParam("taskId") Long taskId) {
        logger.warn("receive stop skipException task request " + taskId);
        return FSMMetaManager.stopSkipException(taskId);
    }

    @POST
    @Path("task/safemode/start/{taskId}")
    public ResultCode startSafeMode(@PathParam("taskId") Long taskId) {
        logger.warn("receive start safe mode task request " + taskId);
        return FSMMetaManager.startSafeMode(taskId);
    }

    @POST
    @Path("task/safemode/stop/{taskId}")
    public ResultCode stopSafeMode(@PathParam("taskId") Long taskId) {
        logger.warn("receive stop safe mode task request " + taskId);
        return FSMMetaManager.stopSafeMode(taskId);
    }

    @POST
    @Path("task/flowcontrol/{taskId}")
    public ResultCode setFlowControl(@PathParam("taskId") Long taskId, @QueryParam("rpslimit") Integer rpsLimit) {
        logger.warn("receive set flow control request " + taskId);
        return FSMMetaManager.setFlowControl(taskId, rpsLimit);
    }

    @POST
    @Path("service/parallelcount/{fsmId}")
    public ResultCode setParallelCount(@PathParam("fsmId") Long fsmId, @QueryParam("count") Integer parallelCount) {
        logger.warn("receive set parallel count request for fsm: " + fsmId);
        return FSMMetaManager.setParallelCount(fsmId, parallelCount);
    }

    @POST
    @Path("service/eventbuffersize/{fsmId}")
    public ResultCode setEventBufferSize(@PathParam("fsmId") Long fsmId, @QueryParam("size") Integer size) {
        logger.warn("receive set buffer size request for fsm: " + fsmId);
        return FSMMetaManager.setEventBufferSize(fsmId, size);
    }


    @POST
    @Path("task/memory/{taskId}")
    public ResultCode setMemory(@PathParam("taskId") Long taskId, @QueryParam("memory") Integer memory) {
        logger.warn("receive set memory request for task: " + taskId);
        return FSMMetaManager.setMemory(taskId, memory);
    }

    @POST
    @Path("task/stop/{taskId}")
    public ResultCode stopTask(@PathParam("taskId") Long taskId) {
        logger.warn("receive stopTask task request " + taskId);
        return FSMMetaManager.stopTask(taskId);
    }

    @POST
    @Path("task/start/{taskId}")
    public ResultCode startTask(@PathParam("taskId") Long taskId) {
        logger.warn("receive startTask task request " + taskId);
        return FSMMetaManager.startTask(taskId);
    }

    @POST
    @Path("task/reset/{taskId}")
    public ResultCode resetTask(@PathParam("taskId") Long taskId) {
        logger.warn("receive resetTask task request " + taskId);
        return FSMMetaManager.resetTask(taskId);
    }

    @GET
    @Path("task/get/config/{taskId}")
    public ResultCode getTaskConfig(@PathParam("taskId") Long taskId) {
        logger.warn("receive getTaskConfig task request " + taskId);
        return FSMMetaManager.getTaskDetail(taskId);
    }

    @Data
    public static class JDBCURLParser {
        private String jdbcUrl;
        private String ip;
        private Integer port;

        public JDBCURLParser(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
            this.parse();
        }

        private void parse() {
            String[] args = jdbcUrl.split("/");
            String[] ipPort = args[2].split(":");
            this.ip = ipPort[0];
            this.port = NumberUtils.createInteger(ipPort[1]);
        }
    }
}
