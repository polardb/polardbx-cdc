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
package com.aliyun.polardbx.rpl.validation;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.filter.DataImportFilter;
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

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final String POLARX_DEFAULT_SCHEMA = "polardbx";
    private static final String SET_POLARX_SERVER_ID = "set polardbx_server_id=%d";
    private static final TaskContext taskContext = TaskContext.getInstance();
    private static final RplService rplService = taskContext.getService();
    private static final DataImportMeta.PhysicalMeta meta = taskContext.getPhysicalMeta();
    private static final ValidationExtractorConfig config = JSON.parseObject(taskContext.getTaskConfig()
        .getExtractorConfig(), ValidationExtractorConfig.class);
    private static CtxFactory factory;
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
    DataSource srcDs;
    /**
     * destination datasource
     */
    DataSource dstDs;
    /**
     * Chunk size
     */
    int chunkSize = DynamicApplicationConfig.getInt(ConfigKeys.RPL_VALIDATION_CHUNK_SIZE);
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

    public static CtxFactory getFactory() {
        if (factory == null) {
            factory = new CtxFactory();
            return factory;
        } else {
            return factory;
        }
    }

    public static class CtxFactory {
        public List<ValidationContext> createCtxList(HostInfo srcHost, HostInfo dstHost, DataImportFilter filter)
            throws Exception {
            List<ValidationContext> contextList = new ArrayList<>();

            // add server id to make sure backflow knows how to filter events
            List<String> connectionInitSQLs = new ArrayList<>();
            String setServerIdSql =
                String.format(SET_POLARX_SERVER_ID, Math.abs(new Long(dstHost.getServerId()).intValue()));
            log.info("set polardbx_server_id: {}", setServerIdSql);
            connectionInitSQLs.add(setServerIdSql);

            Set<String> dbs = meta.getSrcDbList();
            for (String db : dbs) {
                ValidationContext context = new ValidationContext();
                context.setStateMachineId(Long.toString(taskContext.getStateMachineId()));
                context.setServiceId(Long.toString(taskContext.getServiceId()));
                context.setTaskId(Long.toString(taskContext.getTaskId()));
                context.setSrcPhyDB(db);
                context.setDstLogicalDB(filter.getRewriteDb(db, null));
                context.setType(getType());
                context.setType(getType());
                // filter existing tables
                DruidDataSource srcDs;
                DruidDataSource dstDs;
                try {
                    Map<String, String> connParams = new HashMap<>();
                    srcDs = DataSourceUtil.createDruidMySqlDataSource(
                        srcHost.isUsePolarxPoolCN(),
                        srcHost.getHost(),
                        srcHost.getPort(),
                        db,
                        srcHost.getUserName(),
                        srcHost.getPassword(),
                        "",
                        1,
                        20,
                        connParams,
                        null);
                    dstDs = DataSourceUtil.createDruidMySqlDataSource(
                        dstHost.isUsePolarxPoolCN(),
                        dstHost.getHost(),
                        dstHost.getPort(),
                        context.getDstLogicalDB(),
                        dstHost.getUserName(),
                        dstHost.getPassword(),
                        "",
                        1,
                        20,
                        connParams,
                        connectionInitSQLs);
                    ValTaskRepository repository = new ValTaskRepositoryImpl(context);
                    context.setRepository(repository);
                    List<TableInfo> tableList = new ArrayList<>();
                    List<String> srcTableList;
                    srcTableList = new ArrayList<>(meta.getPhysicalDoTableList().get(db));
                    for (String name : srcTableList) {
                        tableList.add(DbMetaManager.getTableInfo(srcDs, db, name, meta.getSrcType()));
                    }
                    context.setSrcPhyTableList(tableList);
                    context.setSrcDs(srcDs);
                    context.setDstDs(dstDs);
                    context.setValSQLGenerator(ValSQLGenerator.builder().
                        ctx(context).
                        convertToByte(config.isConvertToByte()).
                        build());
                    // set up dst mapping table
                    context.setMappingTable(getMappingTableMap(tableList, context.getDstLogicalDB(), dstDs, filter));

                    if (shouldCreateTask()) {
                        repository.createValTasks(getType());
                    }
                } catch (Exception e) {
                    log.error("Initializing ValidationExtractor exception. src db: {}", db, e);
                    StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR,
                        context.getTaskId(), e.getMessage());
                    throw e;
                }
                contextList.add(context);
                // update task status. heartbeat has 300s timeout setting
                StatisticalProxy.getInstance().heartbeat();
            }
            return contextList;
        }

        /**
         * src physical table -> dst logical table
         */
        private Map<String, TableInfo> getMappingTableMap(List<TableInfo> srcTableList, String dstDb,
                                                          DruidDataSource dstDs, DataImportFilter filter)
            throws Exception {
            Map<String, TableInfo> dstTableMap = new HashMap<>();
            Map<String, TableInfo> srcToDstTblMap = new HashMap<>();
            // srcTable --- n:1 ---> dstTable
            for (TableInfo tableInfo : srcTableList) {
                if (isPolarxToDrds()) {
                    // in polarx to drds situation, logical table should be identical
                    TableInfo dstTable =
                        DbMetaManager.getTableInfo(dstDs, dstDb, tableInfo.getName(), HostType.POLARX1);
                    srcToDstTblMap.put(tableInfo.getName(), dstTable);
                } else {
                    String dstTblName = filter.getRewriteTable(tableInfo.getName());
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
            return rplService.getServiceType() == ServiceType.FULL_VALIDATION_CROSSCHECK.getValue()
                || rplService.getServiceType() == ServiceType.RECONCILIATION_CROSSCHECK.getValue();
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
