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
package com.aliyun.polardbx.rpl.validation.reconciliation;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.ValidationDiffMapper;
import com.aliyun.polardbx.binlog.domain.po.ValidationDiff;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.common.ThreadPoolUtil;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.extractor.full.ExtractorUtil;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.validation.SqlContextBuilder;
import com.aliyun.polardbx.rpl.validation.ValSQLGenerator;
import com.aliyun.polardbx.rpl.validation.ValidationTaskRepository;
import com.aliyun.polardbx.rpl.validation.common.DiffRecord;
import com.aliyun.polardbx.rpl.validation.common.DiffStateEnum;
import com.aliyun.polardbx.rpl.validation.common.ValidationTypeEnum;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * @author yudong
 * @since 2024/2/21 16:37
 **/
@Slf4j
public class Repairer {
    private final DataImportMeta.ValidationMeta meta;
    private final ValidationTypeEnum type;
    private final Map<String, DruidDataSource> srcDs;
    private final Map<String, DruidDataSource> dstDs;
    private final ValidationDiffMapper diffMapper = SpringContextHolder.getObject(ValidationDiffMapper.class);
    private ExecutorService repairThreadPool;

    private static final int parallelism =
        DynamicApplicationConfig.getInt(ConfigKeys.RPL_REPAIR_PARALLELISM);

    public Repairer(DataImportMeta.ValidationMeta meta) {
        this.meta = meta;
        this.type = meta.getType();
        this.srcDs = new HashMap<>();
        this.dstDs = new HashMap<>();
    }

    public void start() throws Exception {
        initThreadPool();

        initDataSource();

        startRepair();
    }

    private void initDataSource() throws Exception {
        for (String srcLogicalDb : meta.getSrcLogicalDbList()) {
            srcDs.put(srcLogicalDb, createDataSourceHelper(meta.getSrcLogicalConnInfo(), srcLogicalDb));
            String dstDb = meta.getDbMapping().get(srcLogicalDb);
            dstDs.put(dstDb, createDataSourceHelper(meta.getDstLogicalConnInfo(), dstDb));
        }
    }

    private DruidDataSource createDataSourceHelper(DataImportMeta.ConnInfo connInfo, String dbName)
        throws Exception {
        return DataSourceUtil.createDruidMySqlDataSource(connInfo.getHost(), connInfo.getPort(), dbName,
            connInfo.getUser(), connInfo.getPassword(), "", parallelism, parallelism, null,
            null);
    }

    private void startRepair() {
        Set<String> srcDbList = meta.getSrcLogicalDbList();
        Map<String, String> dbMapping = meta.getDbMapping();
        Map<String, Set<String>> srcDbToTables = meta.getSrcDbToTables();
        for (String srcDb : srcDbList) {
            String dstDb = dbMapping.get(srcDb);
            Set<String> tables = srcDbToTables.get(srcDb);
            for (String table : tables) {
                try {
                    repairTable(srcDb, dstDb, table);
                } catch (Exception e) {
                    log.error("error while repair table, src db:{}, table:{}", srcDb, table, e);
                    StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR,
                        TaskContext.getInstance().getTaskId(), e.getMessage());
                }
            }
        }
    }

    private void repairTable(String srcDbName, String dstDbName, String tableName) throws Exception {
        log.info("start repairing table:{}.{}", srcDbName, tableName);
        List<Future<Void>> futures = new ArrayList<>();
        TableInfo srcTableInfo =
            DbMetaManager.getTableInfo(srcDs.get(srcDbName), srcDbName, tableName, HostType.POLARX1, false);
        while (true) {
            List<ValidationDiff> diffList = ValidationTaskRepository.getValDiffListWithLimit(srcDbName, tableName);
            if (diffList.isEmpty()) {
                break;
            }
            for (ValidationDiff diff : diffList) {
                final Callable<Void> task = () -> {
                    repairOneRecord(dstDbName, tableName, srcTableInfo, diff);
                    return null;
                };
                futures.add(repairThreadPool.submit(task));
            }
            for (Future<Void> future : futures) {
                try {
                    future.get();
                } catch (Throwable e) {
                    log.error("error while repair table, src db:{}, table:{}", srcDbName, tableName, e);
                    StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR,
                        TaskContext.getInstance().getTaskId(), e.getMessage());
                }
            }
            futures.clear();
        }
    }

    private void repairOneRecord(String dstDbName, String tableName, TableInfo tableInfo, ValidationDiff diff) {
        try {
            DiffRecord.DiffType diffType = DiffRecord.DiffType.valueOf(diff.getType());

            List<Object> keyVal = JSONObject.parseArray(diff.getSrcKeyColVal(), Object.class);
            List<Object> row = selectFromSource(tableInfo, keyVal);
            SqlContextBuilder.SqlContext sqlContext = null;
            switch (diffType) {
            case DIFF:
            case MISS:
                sqlContext = ValSQLGenerator.getReplaceIntoSql(dstDbName, tableName, tableInfo, row);
                break;
            case ORPHAN:
                sqlContext = ValSQLGenerator.getDeleteFromSql(dstDbName, tableName, tableInfo, keyVal);
                break;
            }

            int affectedRows = 0;
            try (Connection conn = dstDs.get(dstDbName).getConnection();
                PreparedStatement stmt = conn.prepareStatement(sqlContext.getSql())) {
                int i = 1;
                for (Object v : sqlContext.getParams()) {
                    stmt.setObject(i, v);
                    i++;
                }
                affectedRows = stmt.executeUpdate();
            }

            logRepairResult(dstDbName, tableName, diff.getSrcKeyColVal(), affectedRows);
            fakeDeleteDiff(diff);
        } catch (Exception e) {
            log.error("failed to execute repair stmt", e);
            StatisticalProxy.getInstance().triggerAlarmSync(MonitorType.IMPORT_VALIDATION_ERROR,
                TaskContext.getInstance().getTaskId(), e.getMessage());
        }
    }

    private List<Object> selectFromSource(TableInfo tableInfo, List<Object> keyVal) throws SQLException {
        List<Object> res = null;

        SqlContextBuilder.SqlContext sqlContext = ValSQLGenerator.getPointSelectSql(tableInfo, keyVal);
        try (Connection conn = srcDs.get(tableInfo.getSchema()).getConnection();
            PreparedStatement stmt = conn.prepareStatement(sqlContext.getSql())) {
            int i = 1;
            for (Object v : sqlContext.getParams()) {
                stmt.setObject(i, v);
                i++;
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    res = new ArrayList<>();
                    for (ColumnInfo columnInfo : tableInfo.getColumns()) {
                        Object val = ExtractorUtil.getColumnValue(rs, columnInfo.getName(), columnInfo.getType());
                        res.add(val);
                    }
                }
            }
        }

        return res;
    }

    private void logRepairResult(String dstDb, String dstTable, String srcKeyColVal, int affectedRows) {
        log.info("repair db: {}, table:{}, key:{}, affected rows:{}", dstDb, dstTable, srcKeyColVal, affectedRows);
    }

    private void fakeDeleteDiff(ValidationDiff d) {
        ValidationDiff diff = new ValidationDiff();
        diff.setId(d.getId());
        diff.setState(DiffStateEnum.FIXED.name());
        diff.setDeleted(true);
        diffMapper.updateByPrimaryKeySelective(diff);
    }

    private void initThreadPool() {
        repairThreadPool = ThreadPoolUtil.createExecutorWithFixedNum(parallelism, "src-dst-repair-thread");
    }

}
