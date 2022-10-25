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

import com.aliyun.polardbx.binlog.domain.po.ValidationTask;
import com.aliyun.polardbx.rpl.applier.SqlContext;
import com.aliyun.polardbx.rpl.applier.StatisticalProxy;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.extractor.full.ExtractorUtil;
import com.aliyun.polardbx.rpl.validation.common.Record;
import com.aliyun.polardbx.rpl.validation.common.ValidationStateEnum;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Table data validator
 *
 * @author siyu.yusi
 */
@Slf4j
public class TableValidator implements Validator {
    private static final String CHECKSUM = "checksum";

    private final ValidationContext ctx;
    private ValSQLGenerator valSQLGenerator;

    public TableValidator(final ValidationContext context) {
        this.ctx = context;
        this.valSQLGenerator = context.getValSQLGenerator();
    }

    @Override
    public void findDiffRows() throws Exception {
        List<TableInfo> tableList = ctx.getSrcPhyTableList();
        for (TableInfo srcTable : tableList) {
            try {
                ValidationTask task = ctx.getRepository().getValTaskRecord(srcTable.getName());
                if (task.getState() == ValidationStateEnum.DONE.getValue()) {
                    log.info("skip done src table: {}", srcTable.getName());
                    continue;
                }
                List<Record> diffRowList = findDiffRecords(srcTable);
                // persist diff row list
                ctx.getRepository().persistDiffRows(srcTable, diffRowList);
                ctx.getRepository().updateValTaskState(srcTable, ValidationStateEnum.DONE);
                // task status heartbeat
                StatisticalProxy.getInstance().heartbeat();
            } catch (Exception e) {
                log.error("Find diff rows exception. src table: {}", srcTable.getName(), e);
                ctx.getRepository().updateValTaskState(srcTable, ValidationStateEnum.ERROR);
            }
        }
    }

    /**
     * Find diff records of one chunk
     * @return List<key columns, value>
     * @throws Exception
     */
    private List<Record> findDiffRecords(TableInfo srcTable) throws Exception {
        List<Record> diffRecords = new ArrayList<>();
        Connection srcConn = null;
        PreparedStatement srcStmt = null;
        ResultSet srcRs = null;

        String srcSql;
        try {
            TableInfo dstTable = ctx.getMappingTable().get(srcTable.getName());
            srcConn = ctx.getSrcDs().getConnection();
            srcSql = valSQLGenerator.getSelectAllKeysChecksumSQL(srcTable);
            srcStmt = srcConn.prepareStatement(srcSql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            srcStmt.setFetchSize(Integer.MIN_VALUE);
            log.info("Select all from source. SQL: {}", srcSql);
            srcRs = srcStmt.executeQuery();

            // checksum, key1, key2, key3
            Map<String, List<Serializable>> keyValMap = new HashMap<>();
            // use dst table key list, which contains partition keys
            List<String> keyList = dstTable.getKeyList();
            keyList.forEach(k -> keyValMap.put(k, new ArrayList<>(ctx.getChunkSize())));
            keyValMap.put(CHECKSUM, new ArrayList<>(ctx.getChunkSize()));
            log.info("Trying to find diff records in chunk. Src SQL: {}", srcSql);
            while (srcRs.next()) {
                keyValMap.get(CHECKSUM).add(srcRs.getString(CHECKSUM));
                for (int i = 0; i < keyList.size(); i++) {
                    String keyCol = keyList.get(i);
                    ColumnInfo column = srcTable.getColumns().stream().filter(col -> col.getName().equals(keyCol)).findFirst().orElseThrow(
                            () -> new Exception(String.format("Error to find key column. keyCol: %s", keyCol))
                    );
                    Object val = ExtractorUtil.getColumnValue(srcRs, column.getName(), column.getType());
                    keyValMap.get(keyCol).add((Serializable)val);
                }
                // validate one batch
                if (keyValMap.get(CHECKSUM).size() >= ctx.getChunkSize()) {
                    if (!compareBatchChecksum(srcTable, keyValMap)) {
                        log.info("Finding inconsistent rows one by one for db: {}, src phy table: {}", ctx.getSrcPhyDB(), srcTable.getName());
                        diffRecords.addAll(findDiffOneByOne(dstTable, keyValMap));
                    }
                    keyValMap.put(CHECKSUM, new ArrayList<>(ctx.getChunkSize()));
                    keyList.forEach(k -> keyValMap.put(k, new ArrayList<>(ctx.getChunkSize())));
                }
            }
            if (keyValMap.get(CHECKSUM).size() > 0 && !compareBatchChecksum(srcTable, keyValMap)) {
                log.info("Last batch was inconsistent. Finding diff rows one by one for db: {}, phy src table: {}", ctx.getSrcPhyDB(), srcTable.getName());
                diffRecords.addAll(findDiffOneByOne(dstTable, keyValMap));
            }
            log.info("finish validation for db: {}, phy src table: {}", ctx.getSrcPhyDB(), srcTable.getName());

        } catch (Exception e) {
            log.error("Get diff rows exception");
            throw e;
        } finally {
            DataSourceUtil.closeQuery(srcRs, srcStmt, srcConn);
        }
        return diffRecords;
    }

    private boolean compareBatchChecksum(TableInfo table, Map<String, List<Serializable>> keyValMap) {
        if (keyValMap.get(CHECKSUM).isEmpty()) {
            log.warn("keyValMap checksum list is empty");
            return false;
        }
        SqlContext srcSQLContext = null;
        SqlContext dstSQLContext = null;
        try (Connection srcConn = ctx.getSrcDs().getConnection();
            Connection dstConn = ctx.getDstDs().getConnection()){
            srcSQLContext = ctx.getValSQLGenerator().generateChecksumSQL(table, keyValMap, false);
            String srcChecksum = DataSourceUtil.query(srcConn, srcSQLContext, 1, 3, rs -> {
                rs.next();
                return rs.getString(1);
            });

            dstSQLContext = ctx.getValSQLGenerator().generateChecksumSQL(table, keyValMap, true);

            String dstChecksum = DataSourceUtil.query(dstConn, dstSQLContext, 1, 3, rs -> {
                rs.next();
                return rs.getString(1);
            });
            // update task status. heartbeat has 300s timeout setting
            StatisticalProxy.getInstance().heartbeat();

            return StringUtils.equals(srcChecksum,dstChecksum);
        } catch (Exception e) {
            log.error("Compute batch checksum exception. Reduce to row by row comparison. src SQL: {} \n dst SQL: {} \n"
                , srcSQLContext, dstSQLContext, e);
            return false;
        }
    }

    private List<Record> findDiffOneByOne(TableInfo dstTable, Map<String, List<Serializable>> keyValMap) throws Exception {
        List<Record> diffList = new ArrayList<>();
        List<Serializable> checksumList = keyValMap.get(CHECKSUM);
        for (int i = 0; i < checksumList.size(); i++) {
            String srcChecksum = checksumList.get(i).toString();
            List<Serializable> keyValList = new ArrayList<>();
            for (String key : dstTable.getKeyList()) {
                keyValList.add(keyValMap.get(key).get(i));
            }
            SqlContext dstSqlContext = ctx.getValSQLGenerator().formatSingleRowChecksumSQL(dstTable, keyValList);

            String dstChecksum;

            try (Connection conn = ctx.getDstDs().getConnection()) {
                dstChecksum = DataSourceUtil.query(conn, dstSqlContext, 1, 3, rs -> {
                    if (rs.next()) {
                        return rs.getString(CHECKSUM);
                    }
                    return "";
                });
            }
            if (!srcChecksum.equals(dstChecksum)) {
                log.info("Found inconsistent records. srcChecksum: {}, dstChecksum: {}", srcChecksum, dstChecksum);
                log.info("Find diff ony by one. dst sql: {}", dstSqlContext);
                Record record = Record.builder().columnList(dstTable.getKeyList()).valList(keyValList).build();
                diffList.add(record);
            }
            // update task status. heartbeat has 300s timeout setting
            StatisticalProxy.getInstance().heartbeat();
        }
        log.info("Found inconsistent records in batch. Size: {}", diffList.size());
        return diffList;
    }
}
