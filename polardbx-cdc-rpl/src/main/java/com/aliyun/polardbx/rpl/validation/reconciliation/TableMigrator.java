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
package com.aliyun.polardbx.rpl.validation.reconciliation;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.ValidationDiffMapper;
import com.aliyun.polardbx.binlog.domain.po.ValidationDiff;
import com.aliyun.polardbx.binlog.domain.po.ValidationTask;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.SqlContext;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.extractor.full.ExtractorUtil;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.validation.ValidationContext;
import com.aliyun.polardbx.rpl.validation.common.Record;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Get pending migrating records from validation diff table
 *
 * @author siyu.yusi
 */
@Slf4j
@Data
public class TableMigrator implements Migrator {
    private final ValidationDiffMapper validationDiffMapper = SpringContextHolder.getObject(ValidationDiffMapper.class);
    private final ValidationContext ctx;
    private CacheLoader<String, TableInfo> loader;
    private LoadingCache<String, TableInfo> dstTableCache;

    public TableMigrator(ValidationContext ctx) {
        this.ctx = ctx;
        this.loader = new CacheLoader<String, TableInfo>() {
            @Override
            public TableInfo load(String tableName) throws Exception {
                return DbMetaManager.getTableInfo(ctx.getDstDs(), ctx.getDstLogicalDB(), tableName, HostType.POLARX2);
            }
        };
        this.dstTableCache = CacheBuilder.newBuilder().maximumSize(5000).build(this.loader);
    }

    @Override
    public void reSync() {
        List<ValidationTask> vTaskList = ctx.getRepository().getValTaskList();
        if (vTaskList.isEmpty()) {
            log.error("Empty ValidationTask record list. State machine id: {}, service id: {}, task id: {}, src db: {}",
                ctx.getStateMachineId(), ctx.getServiceId(), ctx.getTaskId(), ctx.getSrcPhyDB());
            return;
        }

        for (TableInfo srcTable : ctx.getSrcPhyTableList()) {
            try {
                migrate(srcTable);
            } catch (Exception e) {
                log.error("Re-sync exception.", e);
            }
        }
    }

    @Override
    public void migrate(TableInfo srcTable) throws Exception {
        List<ValidationDiff> diffList = ctx.getRepository().getValDiffList(srcTable);

        log.info("Start fixing inconsistent records. smid: {}, Src phy db: {}, Table: {}, Records number: {}",
                 ctx.getStateMachineId(), ctx.getSrcPhyDB(), srcTable.getName(), diffList.size());
        List<Record> recordList = new ArrayList<>();
        int startIndex = 0;
        int step = 200;
        for (int i = 0, len = diffList.size(); i < len; i++) {
            ValidationDiff diff = diffList.get(i);
            TableInfo dstTable = ctx.getMappingTable().get(srcTable.getName());
            // dst table contains partition keys
            List<String> colList = dstTable.getColumns().stream().map(ColumnInfo::getName).collect(Collectors.toList());
            List<Serializable> keyValList = JSONObject.parseArray(diff.getSrcKeyColVal(), Serializable.class);
            SqlContext selectSQLContext = ctx.getValSQLGenerator().formatSelectSQL(srcTable, keyValList);
            try (Connection conn = ctx.getSrcDs().getConnection();){
                log.info("Get 1.0 data. SQL: {}", selectSQLContext);
                List<Serializable> valList;
                valList = DataSourceUtil.query(conn, selectSQLContext, 1,1, rs -> {
                    List<Serializable> valListInner = new ArrayList<>();
                    while (rs.next()) {
                        for (ColumnInfo column : srcTable.getColumns()) {
                            Object val = ExtractorUtil.getColumnValue(rs, column.getName(), column.getType());
                            valListInner.add((Serializable)val);
                        }
                    }
                    return valListInner;
                });

                Record record;
                if (valList.isEmpty()) {
                    log.warn("Skipped. Source record not found. SQL: {}", selectSQLContext);
                } else {
                    record = Record.builder().columnList(colList).valList(valList).build();
                    recordList.add(record);
                }

                if (recordList.size() >= step || i == len - 1) {
                    persistToDstTable(diff, recordList);
                    recordList = new ArrayList<>();
                    for (int j = startIndex; j <= i; j++) {
                        // TODO : use a separate thread to delete?
                        ValidationDiff d = diffList.get(j);
                        validationDiffMapper.deleteByPrimaryKey(d.getId());
                    }
                    log.info("Remaining records: {}", diffList.size() - (i - startIndex + 1));
                    startIndex = i + 1;
                    // update task status. heartbeat has 300s timeout setting
                    StatisticalProxy.getInstance().heartbeat();
                }
            } catch (Exception e) {
                log.error("Validator migration phase exception");
                MonitorManager.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR, ctx.getTaskId(),
                    e.getMessage());
                throw e;
            }
        }
    }

    private void persistToDstTable(ValidationDiff diff, List<Record> recordList)
    throws Exception {
        Connection conn = null;
        PreparedStatement stmt = null;
        ValidationTask task = ctx.getRepository().getValTaskByRefId(diff.getValidationTaskId());
        TableInfo dstTable = dstTableCache.getUnchecked(task.getDstLogicalTable());
        try {
            conn = ctx.getDstDs().getConnection();
            stmt = ctx.getValSQLGenerator().formatInsertStatement(conn, dstTable, recordList.stream().toArray(Record[]::new));
            int ret = stmt.executeUpdate();
            log.info("Insert into destination table. Ret: {}", ret);
        } catch (Exception e) {
            log.error("Bulk persist records to destination table exception. Try insert one by one.", e);
            Connection newConn = null;
            PreparedStatement newStmt = null;
            try {
                for (Record r : recordList) {
                    newConn = ctx.getDstDs().getConnection();
                    newStmt = ctx.getValSQLGenerator().formatInsertStatement(newConn, dstTable, r);
                    int ret = newStmt.executeUpdate();
                    log.info("Replace into destination table. Ret: {}", ret);
                }
            } catch (Exception ee) {
                log.error("Replace into destination table failed. SQL: {}", newStmt.toString());
                throw new Exception(ee);
            } finally {
                DataSourceUtil.closeQuery(null, newStmt, newConn);
            }
        } finally {
            DataSourceUtil.closeQuery(null, stmt, conn);
        }
    }
}
