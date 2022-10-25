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
package com.aliyun.polardbx.rpl.validation;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import com.aliyun.polardbx.rpl.taskmeta.ValidationExtractorConfig;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import com.aliyun.polardbx.rpl.validation.repository.ValTaskRepository;
import com.aliyun.polardbx.rpl.validation.repository.impl.ValTaskRepositoryImpl;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validation context for a physical db
 *
 * @author siyu.yusi
 */
@Data
@Slf4j
public class ValidationContext {
    /**
     * One record of rpl_state_machine table. One state machine id represents one drds upgrade task
     */
    String stateMachineId;
    /**
     * One record of rpl_service table. A service represents one logical step of upgrade process such as data sync, validation etc.
     */
    String serviceId;
    /**
     * One record of rpl_task table. One task represents one type of task that should execute
     * in one physical container. One rpl_task record corresponds to one RDS instance.
     */
    String taskId;
    /**
     * Source physical db name
     */
    String srcPhyDB;
    /**
     * Source physical table list
     */
    List<TableInfo> srcPhyTableList;
    /**
     * src physical table to dst logical table
     */
    Map<String, TableInfo> mappingTable;
    /**
     * Destination logical db name
     */
    String dstLogicalDB;
    /**
     * source datasource
     */
    DruidDataSource srcDs;
    /**
     * destination datasource
     */
    DruidDataSource dstDs;
    /**
     * Chunk size
     */
    int chunkSize;
    /**
     * Current context type
     */
    ValidationTypeEnum type;
    /**
     * Validation task repository
     */
    ValTaskRepository repository;
    /**
     * Validation SQL generator
     */
    ValSQLGenerator valSQLGenerator;

    private final static String POLARX_DEFAULT_SCHEMA = "polardbx";
    private final static String SET_POLARX_SERVER_ID = "set polardbx_server_id=%d";

    private final static TaskContext taskContext = TaskContext.getInstance();
    private final static RplService rplService = taskContext.getService();
    private final static DataImportMeta.PhysicalMeta meta = taskContext.getPhysicalMeta();
    private final static ValidationExtractorConfig config = JSON.parseObject(taskContext.getTaskConfig()
        .getExtractorConfig(), ValidationExtractorConfig.class);

    private static CtxFactory factory;
    public static CtxFactory getFactory() {
        if (factory == null) {
            factory = new CtxFactory();
            return factory;
        } else {
            return factory;
        }
    }

    public static class CtxFactory {
        public List<ValidationContext> createCtxList(HostInfo srcHost, HostInfo dstHost) {
            List<ValidationContext> contextList = new ArrayList<>();

            // add server id to make sure backflow knows how to filter events
            List<String> connectionInitSQLs = new ArrayList<>();
            String setServerIdSql = String.format(SET_POLARX_SERVER_ID, Math.abs(new Long(dstHost.getServerId()).intValue()));
            log.info("set polarx_server_id: {}", setServerIdSql);
            connectionInitSQLs.add(setServerIdSql);

            Set<String> dbs;

            log.info("rplService id: {}", rplService.getId());

            /*
            In Polardbx to Drds situation, srcHost is polarx, and dstHost is drds.
            In Drds to Polardbx scenario, however, srcHost is drds, and dstHost is polarx.
            In both cases, DataImportMeta.PhysicalMeta points srcHost to physical dbs of Drds, and dst info will be Polarx.
            Thus, we have to retrieve info from different objects in different scenario.
             */
            String dstLogicalDB;
            if (isPolarxToDrds()) {
                dbs = new HashSet<>();
                dbs.add(srcHost.getSchema());
                dstLogicalDB = dstHost.getSchema();
            } else {
                dbs = meta.getSrcDbList();
                dstLogicalDB = meta.getDstDb();
            }

            for (String db : dbs) {
                ValidationContext context = new ValidationContext();
                context.setStateMachineId(Long.toString(taskContext.getStateMachineId()));
                context.setServiceId(Long.toString(taskContext.getServiceId()));
                context.setTaskId(Long.toString(taskContext.getTaskId()));
                context.setSrcPhyDB(db);
                context.setDstLogicalDB(dstLogicalDB);
                context.setType(getType());
                // TODO : make this argument configurable
                context.setChunkSize(1000);
                context.setType(getType());
                // filter existing tables
                DruidDataSource srcDs;
                DruidDataSource dstDs;
                try {
                    Map<String, String> connParams = new HashMap<>();
                    srcDs = DataSourceUtil.createDruidMySqlDataSource(
                        isPolarxToDrds(),
                        srcHost.getHost(),
                        srcHost.getPort(),
                        db,
                        srcHost.getUserName(),
                        srcHost.getPassword(),
                        "",
                        1,
                        4,
                        connParams,
                        null);
                    dstDs = DataSourceUtil.createDruidMySqlDataSource(
                        !isPolarxToDrds(),
                        dstHost.getHost(),
                        dstHost.getPort(),
                        dstHost.getSchema(),
                        dstHost.getUserName(),
                        dstHost.getPassword(),
                        "",
                        1,
                        4,
                        connParams,
                        connectionInitSQLs);
                    ValTaskRepository repository = new ValTaskRepositoryImpl(context);
                    context.setRepository(repository);
                    List<TableInfo> tableList = new ArrayList<>();
                    List<String> srcTableList;
                    if (isPolarxToDrds()) {
                        srcTableList = meta.getLogicalTableList();
                    } else {
                        srcTableList = new ArrayList<>(meta.getAllowTableList().get(db));
                    }
                    for (String name : srcTableList) {
                        log.info("init validation context table list1. db: {}, table: {}", db, name);
                        TableInfo tableInfo;
                        if (isPolarxToDrds()) {
                            // src is polardbx
                            tableInfo = DbMetaManager.getTableInfo(srcDs, db, name, HostType.POLARX2);
                        } else {
                            // src is rds
                            tableInfo = DbMetaManager.getTableInfo(srcDs, db, name, HostType.RDS);
                        }
                        log.info("init validation context table list2. db: {}, table: {}", db, tableInfo.getName());
                        tableList.add(tableInfo);
                    }
                    // in polarx to drds scenario, the list is actually logical table list which should be identical
                    // for both polarx and drds
                    context.setSrcPhyTableList(tableList);
                    context.setSrcDs(srcDs);
                    context.setDstDs(dstDs);
                    context.setValSQLGenerator(ValSQLGenerator.builder().
                        ctx(context).
                        convertToByte(config.isConvertToByte()).
                        build());
                    // set up dst mapping table
                    context.setMappingTable(getMappingTableMap(tableList, context.getDstLogicalDB(), dstDs, meta));

                    if (shouldCreateTask()) {
                        repository.createValTasks(getType());
                    }
                } catch (Exception e) {
                    log.error("Initializing ValidationExtractor exception. src db: {}", db, e);
                    MonitorManager.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR,
                        context.getTaskId(), e.getMessage());
                    Runtime.getRuntime().halt(1);
                }
                contextList.add(context);
                // update task status. heartbeat has 300s timeout setting
                StatisticalProxy.getInstance().heartbeat();
            }
            return contextList;
        }

        /**
         * src physical table -> dst logical table
         * @param srcTableList
         * @param dstDb
         * @param dstDs
         * @param meta
         * @return
         * @throws Exception
         */
        private Map<String, TableInfo> getMappingTableMap(List<TableInfo> srcTableList, String dstDb, DruidDataSource dstDs, DataImportMeta.PhysicalMeta meta) throws Exception {
            Map<String, TableInfo> dstTableMap = new HashMap<>();
            Map<String, TableInfo> srcToDstTblMap = new HashMap<>();
            // srcTable --- n:1 ---> dstTable
            for (TableInfo tableInfo : srcTableList) {
                if (isPolarxToDrds()) {
                    // in polarx to drds situation, logical table should be identical
                    TableInfo dstTable = DbMetaManager.getTableInfo(dstDs, dstDb, tableInfo.getName(), HostType.POLARX1);
                    srcToDstTblMap.put(tableInfo.getName(), dstTable);
                } else {
                    String dstTblName = meta.getRewriteTableMapping().get(tableInfo.getName());
                    if (dstTblName == null) {
                        log.error("dst mapping table is null. dst db: {}, src phy table: {}", dstDb,
                            tableInfo.getName());
                        continue;
                    }
                    if (!dstTableMap.containsKey(dstTblName)) {
                        TableInfo dstTable = DbMetaManager.getTableInfo(dstDs, dstDb, dstTblName, HostType.POLARX2);
                        dstTableMap.put(dstTblName, dstTable);
                    }
                    srcToDstTblMap.put(tableInfo.getName(), dstTableMap.get(dstTblName));
                }
            }
            return srcToDstTblMap;
        }

        private boolean isPolarxToDrds() {
            if (rplService.getServiceType() == ServiceType.FULL_VALIDATION_CROSSCHECK.getValue()
                || rplService.getServiceType() == ServiceType.RECONCILIATION_CROSSCHECK.getValue()) {
                return true;
            } else {
                return false;
            }
        }

        private boolean shouldCreateTask() {
            switch (ServiceType.from(rplService.getServiceType())) {
            case FULL_VALIDATION:
            case FULL_VALIDATION_CROSSCHECK:
                return true;
            case RECONCILIATION:
            case RECONCILIATION_CROSSCHECK:
                return false;
            }
            return false;
        }
        private ValidationTypeEnum getType() {
            switch (ServiceType.from(rplService.getServiceType())) {
            case FULL_VALIDATION:
            case RECONCILIATION:
                return ValidationTypeEnum.FORWARD;
            case FULL_VALIDATION_CROSSCHECK:
            case RECONCILIATION_CROSSCHECK:
                return ValidationTypeEnum.BACKWARD;
            }
            return ValidationTypeEnum.FORWARD;
        }
    }
}
