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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.rpl.common.DataCompareUtil;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ConflictStrategy;
import com.aliyun.polardbx.rpl.taskmeta.ConflictType;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getBoolean;
import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;
import static com.aliyun.polardbx.rpl.applier.SqlContextExecutor.execUpdate;
import static com.aliyun.polardbx.rpl.applier.SqlContextExecutor.logExecUpdateDebug;
import static com.aliyun.polardbx.rpl.taskmeta.ConflictType.DUPLICATED;
import static com.aliyun.polardbx.rpl.taskmeta.ConflictType.UPDATE_MISSED;

/**
 * @author shicai.xsc 2020/12/1 21:09
 * @since 5.0.0.0
 */
@Slf4j
@Data
public class DmlApplyHelper {
    /*
     * CONSTANT variables
     */
    private static final String INSERT_UPDATE_SQL = "INSERT INTO `%s`.`%s`(%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s";
    private static final String SIMPLE_INSERT_SQL = "INSERT INTO `%s`.`%s`(%s) VALUES %s";
    private static final String REPLACE_SQL = "REPLACE INTO `%s`.`%s`(%s) VALUES %s";
    private static final String INSERT_IGNORE_SQL = "INSERT IGNORE INTO `%s`.`%s`(%s) VALUES %s";
    private static final String DELETE_SQL = "DELETE FROM `%s`.`%s` WHERE %s";
    private static final String UPDATE_SQL = "UPDATE `%s`.`%s` SET %s WHERE %s";
    private static final String SELECT_SQL = "SELECT * FROM `%s`.`%s` WHERE %s";

    /*
     * need initialize by another component
     */
    private static final boolean randomCompareAll = getBoolean(ConfigKeys.RPL_RANDOM_COMPARE_ALL);
    private static final boolean isLabEnv = getBoolean(ConfigKeys.IS_LAB_ENV);
    private static boolean compareAll = false;
    private static boolean insertOnUpdateMiss = true;
    private static DbMetaCache dbMetaCache;

    public static void setCompareAll(boolean compareAll) {
        DmlApplyHelper.compareAll = compareAll;
    }

    public static void setInsertOnUpdateMiss(boolean insertOnUpdateMiss) {
        DmlApplyHelper.insertOnUpdateMiss = insertOnUpdateMiss;
    }

    public static void setDbMetaCache(DbMetaCache dbMetaCache) {
        DmlApplyHelper.dbMetaCache = dbMetaCache;
    }

    public static boolean isFiltered(DBMSColumn column) {
        return column.isGenerated() || column.isRdsImplicitPk();
    }

    public static SqlContext getInsertSqlExecContext(DefaultRowChange rowChange, TableInfo dstTbInfo,
                                                     int insertMode) {
        // WHERE {column1} = {value1} AND {column2} = {value2}
        // REPLACE INTO t1(column1, column2) VALUES (value1, value2)
        StringBuilder nameSqlSb = new StringBuilder();
        StringBuilder valueSqlSb = new StringBuilder();
        List<Serializable> params = new ArrayList<>();
        generateSql(nameSqlSb, valueSqlSb, rowChange, params, false);
        String sql;
        switch (insertMode) {
        case RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE:
            sql = SIMPLE_INSERT_SQL;
            break;
        case RplConstants.INSERT_MODE_INSERT_IGNORE:
            sql = INSERT_IGNORE_SQL;
            break;
        default:
            sql = REPLACE_SQL;
            break;
        }
        String insertSql = String
            .format(sql,
                CommonUtils.escape(dstTbInfo.getSchema()),
                CommonUtils.escape(dstTbInfo.getName()),
                nameSqlSb,
                valueSqlSb);
        return new SqlContext(insertSql, dstTbInfo.getSchema(), dstTbInfo.getName(), params);
    }

    public static List<SqlContext> getDeleteThenReplaceSqlExecContext(DefaultRowChange rowChange, TableInfo dstTbInfo) {
        List<SqlContext> contexts = Lists.newArrayListWithCapacity(2);
        // WHERE {column1} = {value1} AND {column2} = {value2}
        if (!getWhereColumns(dstTbInfo).isEmpty()) {
            StringBuilder whereSqlSb = new StringBuilder();
            List<Serializable> whereParams = new ArrayList<>();
            getWhereSql(rowChange, 1, dstTbInfo, whereSqlSb, whereParams);
            String deleteSql = String.format(DELETE_SQL, CommonUtils.escape(dstTbInfo.getSchema()),
                CommonUtils.escape(dstTbInfo.getName()), whereSqlSb);
            SqlContext context1 =
                new SqlContext(deleteSql, dstTbInfo.getSchema(), dstTbInfo.getName(), whereParams);
            contexts.add(context1);
        }
        // REPLACE INTO t1(column1, column2) VALUES(value1, value2)
        StringBuilder nameSqlSb = new StringBuilder();
        StringBuilder valueSqlSb = new StringBuilder();
        List<Serializable> params = new ArrayList<>();
        generateSql(nameSqlSb, valueSqlSb, rowChange, params, true);
        String insertSql = String
            .format(REPLACE_SQL,
                CommonUtils.escape(dstTbInfo.getSchema()),
                CommonUtils.escape(dstTbInfo.getName()),
                nameSqlSb,
                valueSqlSb);
        SqlContext context2 = new SqlContext(insertSql, dstTbInfo.getSchema(), dstTbInfo.getName(), params);
        contexts.add(context2);
        return contexts;
    }

    public static MergeDmlSqlContext getMergeInsertSqlExecContext(DefaultRowChange rowChange, TableInfo dstTbInfo,
                                                                  int insertMode) {
        List<? extends DBMSColumn> columns = rowChange.getColumns();
        StringBuilder nameSqlSb = new StringBuilder();
        StringBuilder valueSqlSb = new StringBuilder();
        List<Serializable> params = new ArrayList<>();
        for (int i = 1; i <= rowChange.getRowSize(); i++) {
            // INSERT INTO t1(column1, column2) VALUES(value1, value2),(value3, value4)
            // ON DUPLICATE KEY UPDATE column1=VALUES(column1),columns2=VALUES(column2)
            if (i > 1) {
                valueSqlSb.append(',');
            }
            valueSqlSb.append("(");
            Iterator<? extends DBMSColumn> it = columns.iterator();
            while (it.hasNext()) {
                DBMSColumn column = it.next();
                if (isFiltered(column)) {
                    continue;
                }
                if (i == 1) {
                    nameSqlSb.append(repairDMLName(column.getName()));
                }
                valueSqlSb.append("?");
                if (it.hasNext()) {
                    if (i == 1) {
                        nameSqlSb.append(',');
                    }
                    valueSqlSb.append(',');
                }
                Serializable columnValue = rowChange.getRowValue(i, column.getName());
                params.add(columnValue);
            }
            trimLastComma(valueSqlSb);
            valueSqlSb.append(")");
        }
        trimLastComma(nameSqlSb);
        String sql = null;
        switch (insertMode) {
        case RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE:
            sql = SIMPLE_INSERT_SQL;
            break;
        case RplConstants.INSERT_MODE_INSERT_IGNORE:
            sql = INSERT_IGNORE_SQL;
            break;
        case RplConstants.INSERT_MODE_REPLACE:
            sql = REPLACE_SQL;
            break;
        default:
            break;
        }
        String insertSql = String
            .format(sql, CommonUtils.escape(dstTbInfo.getSchema()),
                CommonUtils.escape(dstTbInfo.getName()), nameSqlSb, valueSqlSb);
        return new MergeDmlSqlContext(insertSql, dstTbInfo.getSchema(), dstTbInfo.getName(), params);
    }

    public static SqlContextV2 getMergeInsertSqlExecContextV2(DefaultRowChange rowChange, TableInfo dstTbInfo,
                                                              int insertMode) {
        List<? extends DBMSColumn> columns = rowChange.getColumns();
        List<List<Serializable>> paramsList = new ArrayList<>();
        for (int i = 1; i <= rowChange.getRowSize(); i++) {
            List<Serializable> params = new ArrayList<>();
            for (DBMSColumn column : columns) {
                Serializable columnValue = rowChange.getRowValue(i, column.getName());
                params.add(columnValue);
            }
            paramsList.add(params);
        }
        StringBuilder nameSqlSb = new StringBuilder();
        StringBuilder valueSqlSb = new StringBuilder();
        valueSqlSb.append("(");
        Iterator<? extends DBMSColumn> it = columns.iterator();
        while (it.hasNext()) {
            DBMSColumn column = it.next();
            nameSqlSb.append(repairDMLName(column.getName()));
            valueSqlSb.append("?");
            if (it.hasNext()) {
                nameSqlSb.append(',');
                valueSqlSb.append(',');
            }
        }
        valueSqlSb.append(")");
        String sql;
        switch (insertMode) {
        case RplConstants.INSERT_MODE_INSERT_IGNORE:
            sql = INSERT_IGNORE_SQL;
            break;
        case RplConstants.INSERT_MODE_REPLACE:
            sql = REPLACE_SQL;
            break;
        default:
            sql = SIMPLE_INSERT_SQL;
            break;
        }
        return new SqlContextV2(String.format(sql, CommonUtils.escape(dstTbInfo.getSchema()),
            CommonUtils.escape(dstTbInfo.getName()), nameSqlSb, valueSqlSb), dstTbInfo.getSchema(), dstTbInfo.getName(),
            paramsList);
    }

    public static SqlContext getDeleteSqlExecContext(DefaultRowChange rowChange, TableInfo dstTbInfo) {
        // actually, only 1 row in a rowChange
        // WHERE {column1} = {value1} AND {column2} = {value2}
        StringBuilder whereSqlSb = new StringBuilder();
        List<Serializable> params = new ArrayList<>();
        getWhereSql(rowChange, 1, dstTbInfo, whereSqlSb, params);

        String deleteSql = String.format(DELETE_SQL,
            CommonUtils.escape(dstTbInfo.getSchema()), CommonUtils.escape(dstTbInfo.getName()), whereSqlSb);
        return new SqlContext(deleteSql, dstTbInfo.getSchema(), dstTbInfo.getName(), params);
    }

    public static MergeDmlSqlContext getMergeDeleteSqlExecContext(DefaultRowChange rowChange, TableInfo dstTbInfo) {
        StringBuilder whereSqlSb = new StringBuilder();
        List<Serializable> params = new ArrayList<>();
        getWhereInSqlV2(rowChange, dstTbInfo, whereSqlSb, params);
        String deleteSql = String.format(DELETE_SQL, CommonUtils.escape(dstTbInfo.getSchema()),
            CommonUtils.escape(dstTbInfo.getName()), whereSqlSb);
        return new MergeDmlSqlContext(deleteSql, dstTbInfo.getSchema(), dstTbInfo.getName(), params);
    }

    public static SqlContext getUpdateSqlExecContext(DefaultRowChange rowChange, TableInfo dstTbInfo) {
        // List<? extends DBMSColumn> changeColumns = rowChange.getChangeColumns();
        List<? extends DBMSColumn> changeColumns = rowChange.getColumns();
        // SET {column1} = {value1}, {column2} = {value2}
        StringBuilder setSqlSb = new StringBuilder();
        List<Serializable> params = new ArrayList<>();

        Iterator<? extends DBMSColumn> it = changeColumns.iterator();
        while (it.hasNext()) {
            DBMSColumn changeColumn = it.next();
            if (isFiltered(changeColumn)) {
                continue;
            }
            setSqlSb.append(repairDMLName(changeColumn.getName())).append("=?");
            if (it.hasNext()) {
                setSqlSb.append(',');
            }
            Serializable changeColumnValue = rowChange.getChangeValue(1, changeColumn.getName());
            params.add(changeColumnValue);
        }
        trimLastComma(setSqlSb);

        // WHERE {column1} = {value1} AND {column2} = {value2}
        StringBuilder whereSqlSb = new StringBuilder();
        List<Serializable> whereColumnValues = new ArrayList<>();
        getWhereSql(rowChange, 1, dstTbInfo, whereSqlSb, whereColumnValues);

        params.addAll(whereColumnValues);
        String updateSql = String
            .format(UPDATE_SQL, CommonUtils.escape(dstTbInfo.getSchema()),
                CommonUtils.escape(dstTbInfo.getName()), setSqlSb, whereSqlSb);
        return new SqlContext(updateSql, dstTbInfo.getSchema(), dstTbInfo.getName(), params);
    }

    private static List<ColumnInfo> getWhereColumns(TableInfo tableInfo) {
        // for lab test
        // random 优先级低于 compare all
        if (randomCompareAll) {
            return tableInfo.getWithTypeKeyList(compareAll || new Random().nextBoolean());
        }
        return tableInfo.getWithTypeKeyList(compareAll);
    }

    private static void getWhereSql(DefaultRowChange rowChange, int rowIndex, TableInfo tableInfo,
                                    StringBuilder whereSqlSb,
                                    List<Serializable> whereColumnValues) {
        List<ColumnInfo> whereColumns = getWhereColumns(tableInfo);

        for (int i = 0; i < whereColumns.size(); i++) {
            String columnName = whereColumns.get(i).getName();
            int type = whereColumns.get(i).getType();
            String typeName = whereColumns.get(i).getTypeName();
            // build where sql
            Serializable whereColumnValue = rowChange.getRowValue(rowIndex, columnName);
            String repairedName = repairDMLName(columnName);
            if (whereColumnValue == null) {
                // _drds_implicit_id_ should never be null
                whereSqlSb.append(repairedName).append(" IS NULL ");
            } else {
                switch (type) {
                case Types.FLOAT:
                case Types.REAL:
                case 100:
                case 101:
                    whereSqlSb.append(String.format("round(%s,6 - floor(log10(abs(%s))))", repairedName, repairedName))
                        .append("=round(?,6 - floor(log10(abs(?))))");
                    whereColumnValues.add(whereColumnValue);
                    whereColumnValues.add(whereColumnValue);
                    break;
                case Types.DOUBLE:
                    whereSqlSb.append(String.format("round(%s,15 - floor(log10(abs(%s))))", repairedName, repairedName))
                        .append("=round(?,15 - floor(log10(abs(?))))");
                    whereColumnValues.add(whereColumnValue);
                    whereColumnValues.add(whereColumnValue);
                    break;
                case Types.BIT:
                    whereSqlSb.append(String.format("hex(%s)", repairedName)).append("=?");
                    whereColumnValues.add(DataCompareUtil.bytesToHexString((byte[]) whereColumnValue));
                    break;
                case Types.BINARY:
                    whereSqlSb.append(repairedName).append("=?");
                    if (whereColumnValue instanceof byte[] && StringUtils.equalsIgnoreCase(typeName, "binary")) {
                        final int columnDefSize = whereColumns.get(i).getSize();
                        byte[] srcValue = (byte[]) whereColumnValue;
                        if (srcValue.length != columnDefSize) {
                            byte[] tempWhereColumnValue = new byte[whereColumns.get(i).getSize()];
                            System.arraycopy(srcValue, 0, tempWhereColumnValue, 0,
                                srcValue.length);
                            whereColumnValue = tempWhereColumnValue;
                        }
                    }
                    whereColumnValues.add(whereColumnValue);
                    break;
                default:
                    whereSqlSb.append(repairedName).append("=?");
                    whereColumnValues.add(whereColumnValue);
                }
            }

            if (i < whereColumns.size() - 1) {
                whereSqlSb.append(" AND ");
            }

//            // fill in where column values
//            if (whereColumnValue != null) {
//                whereColumnValues.add(whereColumnValue);
//            }
        }
    }

    private static void getWhereInSql(DefaultRowChange rowChange, TableInfo tableInfo, StringBuilder whereSqlSb,
                                      List<Serializable> whereColumnValues) {
        List<ColumnInfo> whereColumns = getWhereColumns(tableInfo);

        // WHERE (column1, column2) in
        whereSqlSb.append("(");
        for (int i = 0; i < whereColumns.size(); i++) {
            String columnName = whereColumns.get(i).getName();
            whereSqlSb.append(repairDMLName(columnName));
            if (i < whereColumns.size() - 1) {
                whereSqlSb.append(',');
            }
        }
        whereSqlSb.append(")");
        whereSqlSb.append(" in ");

        // ((column1_value1, column2_value1), (column1_value2, column2_value2))
        whereSqlSb.append("(");
        for (int i = 1; i <= rowChange.getRowSize(); i++) {
            whereSqlSb.append("(");
            for (int j = 0; j < whereColumns.size(); j++) {
                whereSqlSb.append("?");
                if (j < whereColumns.size() - 1) {
                    whereSqlSb.append(',');
                }
                String columnName = whereColumns.get(j).getName();
                whereColumnValues.add(rowChange.getRowValue(i, columnName));
            }
            whereSqlSb.append(")");
            if (i < rowChange.getRowSize()) {
                whereSqlSb.append(',');
            }
        }
        whereSqlSb.append(")");
    }

    private static void getWhereInSqlV2(DefaultRowChange rowChange, TableInfo tableInfo, StringBuilder whereSqlSb,
                                        List<Serializable> whereColumnValues) {
        // WHERE ({column1} = {value1} AND {column2} = {value2}) or (...) or
        for (int i = 1; i <= rowChange.getRowSize(); i++) {
            whereSqlSb.append("(");
            getWhereSql(rowChange, i, tableInfo, whereSqlSb, whereColumnValues);
            if (i == rowChange.getRowSize()) {
                whereSqlSb.append(")");
            } else {
                whereSqlSb.append(") or ");
            }
        }
    }

    private static String repairDMLName(String name) {
        return "`" + escape(name) + "`";
    }

    private static void trimLastComma(StringBuilder builder) {
        if (builder.charAt(builder.length() - 1) == ',') {
            builder.setLength(builder.length() - 1);
        }
    }

    private static void generateSql(StringBuilder nameSqlSb, StringBuilder valueSqlSb, DefaultRowChange rowChange,
                                    List<Serializable> params, boolean change) {
        List<? extends DBMSColumn> columns = rowChange.getColumns();
        valueSqlSb.append('(');
        Iterator<? extends DBMSColumn> it = columns.iterator();
        while (it.hasNext()) {
            DBMSColumn column = it.next();
            if (isFiltered(column)) {
                continue;
            }
            nameSqlSb.append(repairDMLName(column.getName()));
            valueSqlSb.append("?");
            if (it.hasNext()) {
                nameSqlSb.append(',');
                valueSqlSb.append(',');
            }
            Serializable columnValue;
            if (change) {
                columnValue = rowChange.getChangeValue(1, column.getName());
            } else {
                columnValue = rowChange.getRowValue(1, column.getName());
            }
            params.add(columnValue);
        }
        // 说明最后一列被filter
        trimLastComma(nameSqlSb);
        trimLastComma(valueSqlSb);
        valueSqlSb.append(')');
    }

    private static String truncateString(String rawString) {
        int length = DynamicApplicationConfig.getInt(ConfigKeys.RPL_ERROR_SQL_TRUNCATE_LENGTH);
        if (StringUtils.isEmpty(rawString)) {
            return "";
        }
        return rawString.length() <= length ? rawString : rawString.substring(0, length);
    }

    public static int execSqlContext(Connection conn, SqlContext sqlContext) throws Exception {
        if (sqlContext == null) {
            return 0;
        }
        long startTime = System.currentTimeMillis();
        int affectedRows = execUpdate(conn, sqlContext);
        long endTime = System.currentTimeMillis();
        StatMetrics.getInstance().addApplyCount(1);
        StatMetrics.getInstance().addRt(endTime - startTime);
        return affectedRows;
    }

    public static void executeDML(Connection conn, List<DefaultRowChange> rowChanges, ConflictStrategy strategy)
        throws Exception {
        for (DefaultRowChange rowChange : rowChanges) {
            if (rowChange.getRowSize() > 1) {
                throw new PolardbxException("row change should not has more than 1 column here");
            }
            boolean overwrite = (strategy == ConflictStrategy.DIRECT_OVERWRITE);
            List<SqlContext> sqlContexts = getSqlContexts(rowChange, overwrite);
            try {
                for (SqlContext context : sqlContexts) {
                    int affectedRows = execSqlContext(conn, context);
                    // update -> insert
                    if (rowChange.getAction() == DBMSAction.UPDATE && !overwrite && affectedRows == 0) {
                        handleDupException(conn, rowChange, strategy, ConflictType.UPDATE_MISSED, null);
                    }
                }
            } catch (SQLException e) {
                handlerError(conn, rowChange, strategy, e);
            }
        }
    }

    public static void handlerError(Connection conn, DefaultRowChange rowChange, ConflictStrategy strategy,
                                    SQLException e)
        throws Exception {
        if (isDuplicateKeyException(e)) {
            handleDupException(conn, rowChange, strategy, DUPLICATED, e);
        } else {
            throw e;
        }
    }

    public static boolean isDuplicateKeyException(SQLException exception) {
        // 违反外键约束时也抛出这种异常，所以这里还要判断包含字符串Duplicate entry
        return exception.getMessage().contains("Duplicate entry") ||
            exception.getMessage().contains("Duplicate key");
    }

    public static void handleDupException(Connection conn, DefaultRowChange rowChange, ConflictStrategy strategy,
                                          ConflictType type,
                                          SQLException e) throws Exception {
        if (isLabEnv && type == DUPLICATED) {
            if (DmlApplyHelper.hasGeneratedUk(rowChange.getSchema(), rowChange.getTable())) {
                // direct ignore in lab because cn cannot deal with replace into when has generated uk
                log.error("ignore dup exception because of generated uk: row change: {}, dup exception:", rowChange, e);
                return;
            }
        }
        if (compareAll) {
            // 打开compareAll的时候，认为源端的数据更老，理所应当地 ignore insert dup 和 ignore update miss
            log.warn("in compare all mode, conflict type: {}, all conflicts will be ignored, {}", type, rowChange);
            return;
        }
        switch (strategy) {
        case IGNORE:
            log.warn("in ignore mode, all conflicts will be ignored, {}", rowChange);
            break;
        case INTERRUPT:
            log.warn("in interrupt mode, conflicts will interrupt write, {}", rowChange);
            throw e;
        case DIRECT_OVERWRITE:
            log.warn("dup exception should not be thrown here, {}", rowChange);
            throw new PolardbxException("dup exception should not be thrown here", e);
        case OVERWRITE:
            if (type == UPDATE_MISSED && !insertOnUpdateMiss) {
                break;
            }
            List<SqlContext> sqlContexts = getSqlContexts(rowChange, true);
            for (SqlContext context : sqlContexts) {
                execSqlContext(conn, context);
            }
        }
    }

    protected static List<SqlContext> getSqlContexts(DefaultRowChange rowChangeEvent, boolean overwrite) {
        try {
            List<SqlContext> sqlContexts = new ArrayList<>();
            TableInfo dstTableInfo = dbMetaCache.getTableInfo(rowChangeEvent.getSchema(), rowChangeEvent.getTable());

            if (overwrite) {
                switch (rowChangeEvent.getAction()) {
                case INSERT:
                    sqlContexts.add(DmlApplyHelper.getInsertSqlExecContext(rowChangeEvent, dstTableInfo,
                        RplConstants.INSERT_MODE_REPLACE));
                    break;
                case UPDATE:
                    sqlContexts = DmlApplyHelper.getDeleteThenReplaceSqlExecContext(rowChangeEvent, dstTableInfo);
                    break;
                case DELETE:
                    sqlContexts.add(DmlApplyHelper.getDeleteSqlExecContext(rowChangeEvent, dstTableInfo));
                    break;
                default:
                    log.error("receive " + rowChangeEvent.getAction().name() + " action message, action is "
                        + rowChangeEvent.getAction());
                    break;
                }
            } else {
                switch (rowChangeEvent.getAction()) {
                case INSERT:
                    sqlContexts.add(DmlApplyHelper.getInsertSqlExecContext(rowChangeEvent, dstTableInfo,
                        RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE));
                    break;
                case UPDATE:
                    sqlContexts.add(DmlApplyHelper.getUpdateSqlExecContext(rowChangeEvent, dstTableInfo));
                    break;
                case DELETE:
                    sqlContexts.add(DmlApplyHelper.getDeleteSqlExecContext(rowChangeEvent, dstTableInfo));
                    break;
                default:
                    log.error("receive " + rowChangeEvent.getAction().name() + " action message, action is "
                        + rowChangeEvent.getAction());
                    break;
                }
            }
            return sqlContexts;
        } catch (Exception e) {
            throw new PolardbxException("getSqlContexts failed, dstTbName:" + rowChangeEvent.getSchema()
                + "." + rowChangeEvent.getTable(), e);
        }
    }

    public static List<Integer> getIdentifyColumnsIndex(Map<String, List<Integer>> allTbIdentifyColumns,
                                                        String fullTbName,
                                                        DefaultRowChange rowChange) throws Exception {
        if (allTbIdentifyColumns.containsKey(fullTbName)) {
            return allTbIdentifyColumns.get(fullTbName);
        }

        TableInfo tbInfo = dbMetaCache.getTableInfo(rowChange.getSchema(), rowChange.getTable());
        List<String> identifyColumnNames = tbInfo.getIdentifyKeyList();
        List<Integer> identifyColumnsIndex = new ArrayList<>();
        for (String columnName : identifyColumnNames) {
            identifyColumnsIndex.add(rowChange.getColumnIndex(columnName));
        }
        allTbIdentifyColumns.put(fullTbName, identifyColumnsIndex);
        return identifyColumnsIndex;
    }

    public static boolean shouldSerialExecute(DefaultRowChange rowChange) throws Exception {
        TableInfo tbInfo = dbMetaCache.getTableInfo(rowChange.getSchema(), rowChange.getTable());
        // no pk && not only insert in this batch
        if (tbInfo.getPks().isEmpty()
            && (rowChange.getAction() == DBMSAction.UPDATE || rowChange.getAction() == DBMSAction.DELETE)) {
            log.warn("row change executing mode goes to serial for none-pk table {}, reason {}.",
                tbInfo.getSchema() + "." + tbInfo.getName(), rowChange.getAction());
            return true;
        }
        if (rowChange.getAction() == DBMSAction.DELETE) {
            return true;
        }
        // has gsi && start with omc_with
        return isLabEnv && tbInfo.getGsiNum() >= 1 && rowChange.getTable().startsWith("omc_with");
    }

    public static boolean hasGeneratedUk(String schema, String tableName) throws Exception {
        TableInfo tbInfo = dbMetaCache.getTableInfo(schema, tableName);
        return tbInfo.isHasGeneratedUk();
    }

    public static List<Integer> getWhereColumnsIndex(Map<String, List<Integer>> allTbWhereColumns,
                                                     String fullTbName,
                                                     DefaultRowChange rowChange) throws Exception {
        if (allTbWhereColumns.containsKey(fullTbName)) {
            return allTbWhereColumns.get(fullTbName);
        }

        TableInfo tbInfo = dbMetaCache.getTableInfo(rowChange.getSchema(), rowChange.getTable());
        List<String> whereColumnNames = tbInfo.getKeyList();
        List<Integer> whereColumns = new ArrayList<>();
        for (String columnName : whereColumnNames) {
            whereColumns.add(rowChange.getColumnIndex(columnName));
        }
        allTbWhereColumns.put(fullTbName, whereColumns);
        return whereColumns;
    }

    public static boolean isNoPkTable(DefaultRowChange rowChange) throws Exception {
        TableInfo tbInfo = dbMetaCache.getTableInfo(rowChange.getSchema(), rowChange.getTable());
        return tbInfo.isNoPkTable();
    }
}
