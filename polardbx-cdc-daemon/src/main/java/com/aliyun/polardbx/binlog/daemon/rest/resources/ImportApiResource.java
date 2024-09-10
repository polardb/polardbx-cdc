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
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.TopologyManager;
import com.aliyun.polardbx.binlog.daemon.rest.ann.ACL;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ConnectionInfo;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ImportTaskConfig;
import com.aliyun.polardbx.binlog.daemon.rest.resources.request.ImportTaskConfigList;
import com.aliyun.polardbx.binlog.dao.ServerInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.ServerInfo;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.fsmutil.DataImportFSM;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMState;
import com.aliyun.polardbx.rpl.taskmeta.CdcExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FullExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.MetaManagerTranProxy;
import com.aliyun.polardbx.rpl.taskmeta.RdsExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.ReconExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.taskmeta.StateMachineStatus;
import com.aliyun.polardbx.rpl.taskmeta.ValidationExtractorConfig;
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
import java.util.ArrayList;
import java.util.HashMap;
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

    private final int REFRESH_ONLY_FULL_COPY = 1;
    private final int REFRESH_ONLY_NOT_FULL_COPY = 2;
    private final int REFRESH_ALL = 3;

    @POST
    @Path("/service/create")
    public ResultCode<?> create(ImportTaskConfigList config) {
        logger.warn("msg : " + JSON.toJSONString(config));
        try {
            DataImportMeta importMeta = new DataImportMeta();
            Map<String, List<String>> allTableList = new HashMap<>();
            importMeta.setRules(new HashMap<>());
            importMeta.setLogicalDbMappings(new HashMap<>());
            importMeta.setSrcLogicalTableList(new HashMap<>());
            for (ImportTaskConfig oneDbConfig : config.getImportTaskConfigs()) {
                importMeta.getSrcLogicalTableList().put(oneDbConfig.getSrcDbName(),
                    oneDbConfig.getTableList().stream().map(String::toLowerCase).collect(Collectors.toList()));
                importMeta.getRules().put(oneDbConfig.getSrcDbName(), oneDbConfig.getRules());
            }
            importMeta.setSupportXa(true);
            importMeta.setCdcClusterId(config.getClusterId());
            int drdsServerId = Math.abs(Objects.hash(config.getImportTaskConfigs().get(0).getRules()));
            int polarxServerId = Math.abs(new Long(ServerConfigUtil.getGlobalNumberVar("SERVER_ID")).intValue());
            if (drdsServerId == polarxServerId) {
                drdsServerId = (drdsServerId - 1) > 0 ? (drdsServerId - 1) : (drdsServerId + 1);
            }

            generatePhysicalMeta(config, importMeta, drdsServerId, polarxServerId);
            generateValidationMeta(config, importMeta, drdsServerId, polarxServerId);
            generateBackFlowMeta(config, importMeta, drdsServerId, polarxServerId);
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

    private void generatePhysicalMeta(ImportTaskConfigList config, DataImportMeta importMeta, int drdsServerId,
                                      int polarxServerId) {
        ServerInfoMapper serverInfoMapper = SpringContextHolder.getObject(ServerInfoMapper.class);
        List<ServerInfo> serverInfoList = serverInfoMapper.select(
            c -> c.where(instType, isEqualTo(0))//0:master, 1:read without htap, 2:read with htap
                .and(status, isEqualTo(0))//0: ready, 1: not_ready, 2: deleting
        );
        String dstIp = serverInfoList.get(0).getIp();
        Integer dstPort = serverInfoList.get(0).getPort();
        String dstUser = DynamicApplicationConfig.getString(ConfigKeys.POLARX_USERNAME);
        String dstPwd = DynamicApplicationConfig.getString(ConfigKeys.POLARX_PASSWORD);
        List<DataImportMeta.PhysicalMeta> metaList = new ArrayList<>();
        if (DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_MERGE_SAME_RDS_TASK)) {
            Map<String, DataImportMeta.PhysicalMeta> metaMap = new HashMap<>();
            for (ImportTaskConfig oneDbConfig : config.getImportTaskConfigs()) {
                ConnectionInfo srcConn = oneDbConfig.getSrcConn();
                TopologyManager topologyManager = new TopologyManager(srcConn.getIp(), srcConn.getPort(),
                    oneDbConfig.getSrcDbName(),
                    srcConn.getUser(), srcConn.getPwd());
                List<String> logicTableList = importMeta.getSrcLogicalTableList().get(oneDbConfig.getSrcDbName());
                for (ConnectionInfo connectionInfo : oneDbConfig.getSrcPhyConnList()) {
                    DataImportMeta.PhysicalMeta meta;
                    if (metaMap.containsKey(connectionInfo.getDbInstanceId())) {
                        meta = metaMap.get(connectionInfo.getDbInstanceId());
                    } else {
                        meta = new DataImportMeta.PhysicalMeta();
                        meta.setDstHost(dstIp);
                        meta.setDstPort(dstPort);
                        meta.setDstUser(dstUser);
                        meta.setDstPassword(dstPwd);
                        meta.setDstType(HostType.POLARX2);
                        meta.setSrcHost(connectionInfo.getIp());
                        meta.setSrcPort(connectionInfo.getPort());
                        meta.setSrcUser(connectionInfo.getUser());
                        meta.setSrcPassword(connectionInfo.getPwd());
                        meta.setSrcType(HostType.RDS);
                        meta.setIgnoreServerIds(polarxServerId + "");
                        meta.setDstServerId(drdsServerId);
                        meta.setRdsBid(config.getImportTaskConfigs().get(0).getRdsBid());
                        meta.setRdsUid(config.getImportTaskConfigs().get(0).getRdsUid());
                        meta.setRdsInstanceId(connectionInfo.getDbInstanceId());
                        meta.setDstDbMapping(new HashMap<>());
                        meta.setSrcDbList(new HashSet<>());
                        meta.setPhysicalDoTableList(new HashMap<>());
                        meta.setRewriteTableMapping(new HashMap<>());
                        metaList.add(meta);
                        metaMap.put(connectionInfo.getDbInstanceId(), meta);
                    }
                    for (String srcDbName : connectionInfo.getDbNameList()) {
                        meta.getDstDbMapping().put(srcDbName.toLowerCase(), oneDbConfig.getDstDbName().toLowerCase());
                    }
                    meta.getSrcDbList().addAll(connectionInfo.getDbNameList());
                    for (String dbName : connectionInfo.getDbNameList()) {
                        List<String> physicalTableList =
                            topologyManager.getAllPhyTableList(dbName, new HashSet<>(logicTableList));
                        physicalTableList =
                            physicalTableList.stream().map(String::toLowerCase).collect(Collectors.toList());
                        dbName = dbName.toLowerCase();
                        meta.getPhysicalDoTableList().put(dbName, new HashSet<>(physicalTableList));
                        // 按RDS合并任务之后，需要考虑不同逻辑库同名物理表的情况
                        // 由map改为map<dbname,map>
                        Map<String, String> dbRewriteTableMapping = new HashMap<>();
                        meta.getRewriteTableMapping().put(dbName, dbRewriteTableMapping);
                        for (String physicalTable : physicalTableList) {
                            physicalTable = physicalTable.toLowerCase();
                            dbRewriteTableMapping.put(physicalTable, topologyManager.getLogicTable(physicalTable));
                        }
                    }
                }
            }
        } else {
            for (ImportTaskConfig oneDbConfig : config.getImportTaskConfigs()) {
                ConnectionInfo srcConn = oneDbConfig.getSrcConn();
                importMeta.getLogicalDbMappings()
                    .put(oneDbConfig.getSrcDbName().toLowerCase(), oneDbConfig.getDstDbName().toLowerCase());
                TopologyManager topologyManager =
                    new TopologyManager(srcConn.getIp(), srcConn.getPort(), oneDbConfig.getSrcDbName(),
                        srcConn.getUser(),
                        srcConn.getPwd());
                List<String> logicTableList = importMeta.getSrcLogicalTableList().get(oneDbConfig.getSrcDbName());
                for (ConnectionInfo connectionInfo : oneDbConfig.getSrcPhyConnList()) {
                    DataImportMeta.PhysicalMeta meta = new DataImportMeta.PhysicalMeta();
                    metaList.add(meta);
                    meta.setDstHost(dstIp);
                    meta.setDstPort(dstPort);
                    meta.setDstUser(dstUser);
                    meta.setDstPassword(dstPwd);
                    meta.setDstType(HostType.POLARX2);
                    meta.setSrcHost(connectionInfo.getIp());
                    meta.setSrcPort(connectionInfo.getPort());
                    meta.setSrcUser(connectionInfo.getUser());
                    meta.setSrcPassword(connectionInfo.getPwd());
                    meta.setSrcType(HostType.RDS);
                    meta.setIgnoreServerIds(polarxServerId + "");
                    meta.setDstServerId(drdsServerId);
                    meta.setRdsBid(config.getImportTaskConfigs().get(0).getRdsBid());
                    meta.setRdsUid(config.getImportTaskConfigs().get(0).getRdsUid());
                    meta.setRdsInstanceId(connectionInfo.getDbInstanceId());
                    meta.setDstDbMapping(new HashMap<>());
                    meta.setSrcDbList(new HashSet<>());
                    meta.setPhysicalDoTableList(new HashMap<>());
                    meta.setRewriteTableMapping(new HashMap<>());
                    for (String srcPhysicalDbName : connectionInfo.getDbNameList()) {
                        meta.getDstDbMapping()
                            .put(srcPhysicalDbName.toLowerCase(), oneDbConfig.getDstDbName().toLowerCase());
                    }
                    meta.getSrcDbList().addAll(connectionInfo.getDbNameList());
                    for (String dbName : connectionInfo.getDbNameList()) {
                        List<String> physicalTableList =
                            topologyManager.getAllPhyTableList(dbName, new HashSet<>(logicTableList));
                        physicalTableList =
                            physicalTableList.stream().map(String::toLowerCase).collect(Collectors.toList());
                        dbName = dbName.toLowerCase();
                        meta.getPhysicalDoTableList().put(dbName, new HashSet<>(physicalTableList));
                        Map<String, String> dbRewriteTableMapping = new HashMap<>();
                        meta.getRewriteTableMapping().put(dbName, dbRewriteTableMapping);
                        for (String physicalTable : physicalTableList) {
                            physicalTable = physicalTable.toLowerCase();
                            dbRewriteTableMapping.put(physicalTable, topologyManager.getLogicTable(physicalTable));
                        }
                    }
                }
            }
        }
        importMeta.setMetaList(metaList);
    }

    private void generateValidationMeta(ImportTaskConfigList config,
                                        DataImportMeta importMeta, int drdsServerId, int polarxServerId) {
        DataImportMeta.ValidationMeta validationMeta = new DataImportMeta.ValidationMeta();

        // set src logical conn info
        ConnectionInfo drdsConn = config.getImportTaskConfigs().get(0).getSrcConn();
        DataImportMeta.ConnInfo srcConnInfo =
            new DataImportMeta.ConnInfo(drdsConn.getIp(), drdsConn.getPort(), drdsConn.getUser(), drdsConn.getPwd(),
                HostType.POLARX1);
        validationMeta.setSrcLogicalConnInfo(srcConnInfo);

        // set dst logical conn info
        ServerInfoMapper serverInfoMapper = SpringContextHolder.getObject(ServerInfoMapper.class);
        List<ServerInfo> serverInfoList = serverInfoMapper.select(c ->
            c.where(instType, isEqualTo(0))//0:master, 1:read without htap, 2:read with htap
                .and(status, isEqualTo(0))//0: ready, 1: not_ready, 2: deleting
        );
        String dstIp = serverInfoList.get(0).getIp();
        Integer dstPort = serverInfoList.get(0).getPort();
        String dstUser = DynamicApplicationConfig.getString(ConfigKeys.POLARX_USERNAME);
        String dstPwd = DynamicApplicationConfig.getString(ConfigKeys.POLARX_PASSWORD);
        DataImportMeta.ConnInfo dstConnInfo =
            new DataImportMeta.ConnInfo(dstIp, dstPort, dstUser, dstPwd, HostType.POLARX2);
        validationMeta.setDstLogicalConnInfo(dstConnInfo);

        // set server id
        validationMeta.setDstServerId(polarxServerId);
        validationMeta.setIgnoreServerIds(drdsServerId + "");

        // set validate db list， logical db mapping and logical table list to validate
        Set<String> srcLogicalDbList = new HashSet<>();
        Map<String, String> dbMapping = new HashMap<>();
        Map<String, Set<String>> srcDbToTables = new HashMap<>();
        for (ImportTaskConfig oneDbConfig : config.getImportTaskConfigs()) {
            String srcDb = oneDbConfig.getSrcDbName().toLowerCase();
            String dstDb = oneDbConfig.getDstDbName().toLowerCase();
            List<String> srcTableList = importMeta.getSrcLogicalTableList().get(oneDbConfig.getSrcDbName());
            srcLogicalDbList.add(srcDb);
            dbMapping.put(srcDb, dstDb);
            srcDbToTables.put(oneDbConfig.getSrcDbName(), new HashSet<>(srcTableList));
        }
        validationMeta.setSrcLogicalDbList(srcLogicalDbList);
        validationMeta.setDbMapping(dbMapping);
        validationMeta.setSrcDbToTables(srcDbToTables);
        importMeta.setValidationMeta(validationMeta);
    }

    private void generateBackFlowMeta(ImportTaskConfigList config, DataImportMeta importMeta, int drdsServerId,
                                      int polarxServerId) {
        ConnectionInfo drdsConn = config.getImportTaskConfigs().get(0).getSrcConn();
        DataImportMeta.PhysicalMeta backFlowMeta = new DataImportMeta.PhysicalMeta();
        backFlowMeta.setDstHost(drdsConn.getIp());
        backFlowMeta.setDstPort(drdsConn.getPort());
        backFlowMeta.setDstUser(drdsConn.getUser());
        backFlowMeta.setDstPassword(drdsConn.getPwd());
        backFlowMeta.setDstType(HostType.POLARX1);
        backFlowMeta.setSrcType(HostType.POLARX2);
        backFlowMeta.setDstServerId(polarxServerId);
        backFlowMeta.setIgnoreServerIds(drdsServerId + "");
        backFlowMeta.setSrcDbList(new HashSet<>());
        backFlowMeta.setDstDbMapping(new HashMap<>());
        backFlowMeta.setPhysicalDoTableList(new HashMap<>());
        for (ImportTaskConfig oneDbConfig : config.getImportTaskConfigs()) {
            backFlowMeta.getSrcDbList().add(oneDbConfig.getDstDbName().toLowerCase());
            backFlowMeta.getDstDbMapping()
                .put(oneDbConfig.getDstDbName().toLowerCase(), oneDbConfig.getSrcDbName().toLowerCase());
            List<String> logicTableList = importMeta.getSrcLogicalTableList().get(oneDbConfig.getSrcDbName());
            backFlowMeta.getPhysicalDoTableList().put(oneDbConfig.getDstDbName(), new HashSet<>(logicTableList));
        }
        backFlowMeta.setRewriteTableMapping(new HashMap<>());
        importMeta.setBackFlowMeta(backFlowMeta);
    }

    @POST
    @Path("/service/refresh/{fsmId}")
    public ResultCode<?> refreshImportMeta(@PathParam("fsmId") Long fsmId, ImportTaskConfigList config) {
        try {
            logger.warn("msg : " + JSON.toJSONString(config));
            RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
            logger.info("old fsm config: {}", stateMachine.getConfig());

            // 更新rule和allTableList
            DataImportMeta dataImportMeta = JSON.parseObject(stateMachine.getConfig(), DataImportMeta.class);
            dataImportMeta.setSrcLogicalTableList(new HashMap<>());
            for (ImportTaskConfig oneDbConfig : config.getImportTaskConfigs()) {
                dataImportMeta.getSrcLogicalTableList().put(oneDbConfig.getSrcDbName(),
                    oneDbConfig.getTableList().stream().map(String::toLowerCase).collect(Collectors.toList()));
                dataImportMeta.getRules().put(oneDbConfig.getSrcDbName(), oneDbConfig.getRules());
            }

            // 更新physical meta
            generatePhysicalMeta(config, dataImportMeta, (int) (dataImportMeta.getMetaList().get(0).getDstServerId()),
                Integer.parseInt(dataImportMeta.getMetaList().get(0).getIgnoreServerIds()));
            generateValidationMeta(config, dataImportMeta, (int) (dataImportMeta.getMetaList().get(0).getDstServerId()),
                Integer.parseInt(dataImportMeta.getMetaList().get(0).getIgnoreServerIds()));
            generateBackFlowMeta(config, dataImportMeta, (int) (dataImportMeta.getMetaList().get(0).getDstServerId()),
                Integer.parseInt(dataImportMeta.getMetaList().get(0).getIgnoreServerIds()));

            stateMachine.setConfig(JSON.toJSONString(dataImportMeta));
            DbTaskMetaManager.updateStateMachine(stateMachine);

            reGenerateSourceToTargetConfigV2(fsmId, REFRESH_ALL);
            return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(true).build();
        } catch (Exception e) {
            logger.error("create error!", e);
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg(e.getMessage()).data(false).build();
        }
    }

    public ResultCode<?> reGenerateSourceToTargetConfigV2(Long fsmId, int option)
        throws Exception {
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
        logger.info("old fsm config: {}", stateMachine.getConfig());

        // 全量 增量 校验 订正 需要修一下
        DataImportMeta dataImportMeta = JSON.parseObject(stateMachine.getConfig(), DataImportMeta.class);
        for (int i = 0; i < dataImportMeta.getMetaList().size(); ++i) {
            DataImportMeta.PhysicalMeta meta = dataImportMeta.getMetaList().get(i);
            String extractorConfigStr;
            if (option == REFRESH_ONLY_FULL_COPY || option == REFRESH_ALL) {
                RplService fullService = DbTaskMetaManager.getService(fsmId, ServiceType.FULL_COPY);
                List<RplTask> fullTasks = DbTaskMetaManager.listTaskByService(fullService.getId());
                RplTaskConfig config1 = DbTaskMetaManager.getTaskConfig(fullTasks.get(i).getId());
                logger.info("old full config1: {}", config1.getExtractorConfig());
                FullExtractorConfig fullExtractorConfig =
                    JSON.parseObject(config1.getExtractorConfig(), FullExtractorConfig.class);
                fullExtractorConfig.setPrivateMeta(JSON.toJSONString(meta));
                extractorConfigStr = JSON.toJSONString(fullExtractorConfig);
                DbTaskMetaManager.updateTaskConfig(fullTasks.get(i).getId(), extractorConfigStr, null, null, null);
            }
            if (option == REFRESH_ONLY_NOT_FULL_COPY || option == REFRESH_ALL) {
                stateMachine.setConfig(JSON.toJSONString(dataImportMeta));
                DbTaskMetaManager.updateStateMachine(stateMachine);

                RplService incService = DbTaskMetaManager.getService(fsmId, ServiceType.INC_COPY);
                List<RplTask> incTasks = DbTaskMetaManager.listTaskByService(incService.getId());
                RplTaskConfig config3 = DbTaskMetaManager.getTaskConfig(incTasks.get(i).getId());
                logger.info("old inc config3: {}", config3.getExtractorConfig());
                RdsExtractorConfig incExtractorConfig1 =
                    JSON.parseObject(config3.getExtractorConfig(), RdsExtractorConfig.class);
                incExtractorConfig1.setPrivateMeta(JSON.toJSONString(meta));
                extractorConfigStr = JSON.toJSONString(incExtractorConfig1);
                DbTaskMetaManager.updateTaskConfig(incTasks.get(i).getId(), extractorConfigStr, null, null, null);
            }
        }

        String extractorConfigStr;

        RplService backFlowService = DbTaskMetaManager.getService(fsmId, ServiceType.CDC_INC);
        List<RplTask> backFlowTasks = DbTaskMetaManager.listTaskByService(backFlowService.getId());
        RplTaskConfig taskConfig = DbTaskMetaManager.getTaskConfig(backFlowTasks.get(0).getId());
        logger.info("old backFlow config: {}", taskConfig.getExtractorConfig());
        CdcExtractorConfig cdcExtractorConfig =
            JSON.parseObject(taskConfig.getExtractorConfig(), CdcExtractorConfig.class);
        cdcExtractorConfig.setPrivateMeta(JSON.toJSONString(dataImportMeta.getBackFlowMeta()));
        extractorConfigStr = JSON.toJSONString(cdcExtractorConfig);
        DbTaskMetaManager.updateTaskConfig(backFlowTasks.get(0).getId(), extractorConfigStr, null, null, null);

        RplService validCrossService = DbTaskMetaManager.getService(fsmId, ServiceType.FULL_VALIDATION);
        RplService reconCrossService = DbTaskMetaManager.getService(fsmId, ServiceType.RECONCILIATION);
        List<RplTask> validCrossTasks = DbTaskMetaManager.listTaskByService(validCrossService.getId());
        List<RplTask> reconCrossTasks = DbTaskMetaManager.listTaskByService(reconCrossService.getId());
        RplTaskConfig config1 = DbTaskMetaManager.getTaskConfig(validCrossTasks.get(0).getId());
        RplTaskConfig config2 = DbTaskMetaManager.getTaskConfig(reconCrossTasks.get(0).getId());
        logger.info("old validation config: {}", config1.getExtractorConfig());
        logger.info("old recon config: {}", config2.getExtractorConfig());
        ValidationExtractorConfig validExtractorConfig =
            JSON.parseObject(config1.getExtractorConfig(), ValidationExtractorConfig.class);
        ReconExtractorConfig reconExtractorConfig =
            JSON.parseObject(config2.getExtractorConfig(), ReconExtractorConfig.class);

        validExtractorConfig.setPrivateMeta(JSON.toJSONString(dataImportMeta.getValidationMeta()));
        reconExtractorConfig.setPrivateMeta(JSON.toJSONString(dataImportMeta.getValidationMeta()));
        extractorConfigStr = JSON.toJSONString(validExtractorConfig);
        DbTaskMetaManager.updateTaskConfig(validCrossTasks.get(0).getId(), extractorConfigStr, null, null, null);
        extractorConfigStr = JSON.toJSONString(reconExtractorConfig);
        DbTaskMetaManager.updateTaskConfig(reconCrossTasks.get(0).getId(), extractorConfigStr, null, null, null);

        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(fsmId).build();
    }

    @POST
    @Path("/service/start/{fsmId}")
    public ResultCode<?> start(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive start fsm request " + fsmId);
        return FSMMetaManager.startStateMachine(fsmId);
    }

    @POST
    @Path("/service/restart/{fsmId}")
    public ResultCode<?> restart(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive restart fsm request " + fsmId);
        return FSMMetaManager.restartStateMachine(fsmId);
    }

    @POST
    @Path("/service/stop/{fsmId}")
    public ResultCode<?> stop(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive stop fsm request " + fsmId);
        return FSMMetaManager.stopStateMachine(fsmId);
    }

    @POST
    @Path("/service/delete/{fsmId}")
    public ResultCode<?> delete(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive delete fsm request " + fsmId);
        MetaManagerTranProxy manager = SpringContextHolder.getObject(MetaManagerTranProxy.class);
        return manager.deleteStateMachine(fsmId);
    }

    @POST
    @Path("/service/backflow/start/{fsmId}")
    public ResultCode<?> startBackflow(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive start backflow fsm request, fsm id: {}", fsmId);
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
        if (stateMachine == null) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("fsm not exist").data(RplConstants.FAILURE)
                .build();
        }
        if (FSMState.valueOf(stateMachine.getState()) != FSMState.RECON_FINISHED_CATCH_UP
            && FSMState.valueOf(stateMachine.getState()) != FSMState.BI_DIRECTION) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("state not support this action")
                .data(RplConstants.FAILURE).build();
        }
        if (StateMachineStatus.valueOf(stateMachine.getStatus()) != StateMachineStatus.RUNNING) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("fsm not running")
                .data(RplConstants.FAILURE).build();
        }
        DataImportFSM.getInstance().updateState(fsmId, FSMState.BACK_FLOW);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @POST
    @Path("/service/bidirection/start/{fsmId}")
    public ResultCode startBiDirection(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive start bidirection fsm request, fsm id: {}", fsmId);
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
        if (stateMachine == null) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("fsm not exist").data(RplConstants.FAILURE)
                .build();
        }
        if (FSMState.valueOf(stateMachine.getState()) != FSMState.RECON_FINISHED_CATCH_UP
            && FSMState.valueOf(stateMachine.getState()) != FSMState.BACK_FLOW) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("state not support this action")
                .data(RplConstants.FAILURE).build();
        }
        if (StateMachineStatus.valueOf(stateMachine.getStatus()) != StateMachineStatus.RUNNING) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("fsm not running")
                .data(RplConstants.FAILURE).build();
        }
        DataImportFSM.getInstance().updateState(fsmId, FSMState.BI_DIRECTION);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @POST
    @Path("/service/backflow/stop/{fsmId}")
    public ResultCode<?> stopBackflow(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive stop backflow fsm request, fsm id: {}", fsmId);
        RplStateMachine stateMachine = DbTaskMetaManager.getStateMachine(fsmId);
        if (stateMachine == null) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("fsm not exist").data(RplConstants.FAILURE)
                .build();
        }
        if (FSMState.valueOf(stateMachine.getState()) != FSMState.BACK_FLOW
            && FSMState.valueOf(stateMachine.getState()) != FSMState.BACK_FLOW_CATCH_UP) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("state not support this action")
                .data(RplConstants.FAILURE).build();
        }
        if (StateMachineStatus.valueOf(stateMachine.getStatus()) != StateMachineStatus.RUNNING) {
            return ResultCode.builder().code(RplConstants.FAILURE_CODE).msg("fsm not running")
                .data(RplConstants.FAILURE).build();
        }
        DataImportFSM.getInstance().updateState(fsmId, FSMState.RECON_FINISHED_WAIT_CATCH_UP);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @GET
    @Path("/service/detail/{fsmId}")
    public ResultCode<?> detail(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive detail fsm request " + fsmId);
        return FSMMetaManager.getStateMachineDetail(fsmId);
    }

    @POST
    @Path("service/converttobyte/start/{fsmId}")
    public ResultCode<?> startConvertToByte(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive start convert to byte task request " + fsmId);
        return FSMMetaManager.setConvertToByte(fsmId, true);
    }

    @POST
    @Path("service/converttobyte/stop/{fsmId}")
    public ResultCode<?> stopConvertToByte(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive stop convert to byte task request " + fsmId);
        return FSMMetaManager.setConvertToByte(fsmId, false);
    }

    @POST
    @Path("/task/setossinstanceid/{taskId}/{instanceId}")
    public ResultCode<?> setOssInstanceId(@PathParam("taskId") Long taskId, @PathParam("instanceId") Long instanceId) {
        logger.warn("receive add rds info request for task" + taskId);
        RplTask task = DbTaskMetaManager.getTask(taskId);
        RplTaskConfig taskConfig = DbTaskMetaManager.getTaskConfig(task.getId());
        RdsExtractorConfig config = JSON.parseObject(taskConfig.getExtractorConfig(), RdsExtractorConfig.class);
        config.setFixHostInstanceId(instanceId);
        DbTaskMetaManager.updateTaskConfig(task.getId(), JSON.toJSONString(config), null, null, null);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @POST
    @Path("/task/removeossinstanceid/{taskId}")
    public ResultCode<?> removeOssInstanceId(@PathParam("taskId") Long taskId) {
        logger.warn("receive remove rds info request for task" + taskId);
        RplTask task = DbTaskMetaManager.getTask(taskId);
        RplTaskConfig taskConfig = DbTaskMetaManager.getTaskConfig(task.getId());
        RdsExtractorConfig config = JSON.parseObject(taskConfig.getExtractorConfig(), RdsExtractorConfig.class);
        config.setFixHostInstanceId(null);
        DbTaskMetaManager.updateTaskConfig(task.getId(), JSON.toJSONString(config), null, null, null);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @POST
    @Path("/task/modifyextractorhostinfo/{taskId}/{ip}/{port}")
    public ResultCode<?> modifyExtractorHostInfo(@PathParam("taskId") Long taskId, @PathParam("ip") String ip,
                                                 @PathParam("port") Integer port) {
        logger.warn("receive modify extractor host info request for task" + taskId);
        FSMMetaManager.modifyExtractorHostInfo(taskId, ip, port);
        return ResultCode.builder().code(RplConstants.SUCCESS_CODE).msg("success").data(RplConstants.SUCCESS).build();
    }

    @POST
    @Path("task/exception/skip/start/{taskId}")
    public ResultCode<?> startSkipException(@PathParam("taskId") Long taskId) {
        logger.warn("receive start skipException task request " + taskId);
        return FSMMetaManager.startSkipException(taskId);
    }

    @POST
    @Path("task/exception/skip/stop/{taskId}")
    public ResultCode<?> stopSkipException(@PathParam("taskId") Long taskId) {
        logger.warn("receive stop skipException task request " + taskId);
        return FSMMetaManager.stopSkipException(taskId);
    }

    @POST
    @Path("task/safemode/start/{taskId}")
    public ResultCode<?> startSafeMode(@PathParam("taskId") Long taskId) {
        logger.warn("receive start safe mode task request " + taskId);
        return FSMMetaManager.startSafeMode(taskId);
    }

    @POST
    @Path("task/safemode/stop/{taskId}")
    public ResultCode<?> stopSafeMode(@PathParam("taskId") Long taskId) {
        logger.warn("receive stop safe mode task request " + taskId);
        return FSMMetaManager.stopSafeMode(taskId);
    }

    @POST
    @Path("task/flowcontrol/{taskId}")
    public ResultCode<?> setFlowControl(@PathParam("taskId") Long taskId, @QueryParam("rpslimit") Integer rpsLimit) {
        logger.warn("receive set flow control request " + taskId);
        return FSMMetaManager.setFlowControl(taskId, rpsLimit);
    }

    @POST
    @Path("service/parallelcount/{fsmId}")
    public ResultCode<?> setParallelCount(@PathParam("fsmId") Long fsmId, @QueryParam("count") Integer parallelCount) {
        logger.warn("receive set parallel count request for fsm: " + fsmId);
        return FSMMetaManager.setParallelCount(fsmId, parallelCount);
    }

    @POST
    @Path("service/eventbuffersize/{fsmId}")
    public ResultCode<?> setEventBufferSize(@PathParam("fsmId") Long fsmId, @QueryParam("size") Integer size) {
        logger.warn("receive set buffer size request for fsm: " + fsmId);
        return FSMMetaManager.setEventBufferSize(fsmId, size);
    }

    @POST
    @Path("service/fullreadbatchsize/{fsmId}")
    public ResultCode<?> setFullReadBatchSize(@PathParam("fsmId") Long fsmId, @QueryParam("size") Integer size) {
        logger.warn("receive set full read batch size request for fsm: " + fsmId);
        return FSMMetaManager.setFullReadBatchSize(fsmId, size);
    }

    @POST
    @Path("service/writebatchsize/{fsmId}")
    public ResultCode<?> setWriteBatchSize(@PathParam("fsmId") Long fsmId, @QueryParam("size") Integer size) {
        logger.warn("receive set write batch size request for fsm: " + fsmId);
        return FSMMetaManager.setWriteBatchSize(fsmId, size);
    }

    @POST
    @Path("service/writeparallelcount/{fsmId}")
    public ResultCode setWriteParallelCount(@PathParam("fsmId") Long fsmId, @QueryParam("size") Integer size) {
        logger.warn("receive set write parallel count request for fsm: " + fsmId);
        return FSMMetaManager.setWriteParallelCount(fsmId, size);
    }

    @POST
    @Path("task/memory/{taskId}")
    public ResultCode<?> setMemory(@PathParam("taskId") Long taskId, @QueryParam("memory") Integer memory) {
        logger.warn("receive set memory request for task: " + taskId);
        return FSMMetaManager.setMemory(taskId, memory);
    }

    @POST
    @Path("task/stop/{taskId}")
    public ResultCode<?> stopTask(@PathParam("taskId") Long taskId) {
        logger.warn("receive stopTask task request " + taskId);
        return FSMMetaManager.stopTask(taskId);
    }

    @POST
    @Path("task/start/{taskId}")
    public ResultCode<?> startTask(@PathParam("taskId") Long taskId) {
        logger.warn("receive startTask task request " + taskId);
        return FSMMetaManager.startTask(taskId);
    }

    @POST
    @Path("task/reset/{taskId}")
    public ResultCode<?> resetTask(@PathParam("taskId") Long taskId) {
        logger.warn("receive resetTask task request " + taskId);
        return FSMMetaManager.resetTask(taskId);
    }

    @GET
    @Path("task/get/config/{taskId}")
    public ResultCode<?> getTaskConfig(@PathParam("taskId") Long taskId) {
        logger.warn("receive getTaskConfig task request " + taskId);
        return FSMMetaManager.getTaskDetail(taskId);
    }

    @POST
    @Path("service/markbackflow/{fsmId}")
    public ResultCode<?> markBackFlowPosition(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive mark back flow position request " + fsmId);
        return FSMMetaManager.markBackFlowPosition(fsmId);
    }

    @POST
    @Path("service/lock/{fsmId}")
    public ResultCode<?> lockDbs(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive lock dbs request " + fsmId);
        return FSMMetaManager.lockDbs(fsmId);
    }

    @POST
    @Path("service/unlock/{fsmId}")
    public ResultCode<?> unlockDbs(@PathParam("fsmId") Long fsmId) {
        logger.warn("receive unlock dbs request " + fsmId);
        return FSMMetaManager.unlockDbs(fsmId);
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
