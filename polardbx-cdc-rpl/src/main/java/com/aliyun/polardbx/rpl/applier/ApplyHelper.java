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
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DdlResult;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DruidDdlParser;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.format.utils.SqlModeUtil;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;

/**
 * @author shicai.xsc 2020/12/1 21:09
 * @since 5.0.0.0
 */
@Slf4j
@Data
public class ApplyHelper {

    private static final String INSERT_UPDATE_SQL = "INSERT INTO `%s`.`%s`(%s) VALUES (%s) ON DUPLICATE KEY UPDATE %s";
    private static final String BATCH_INSERT_SQL = "INSERT INTO `%s`.`%s`(%s) VALUES %s";
    private static final String REPLACE_SQL = "REPLACE INTO `%s`.`%s`(%s) VALUES %s";
    private static final String INSERT_IGNORE_SQL = "INSERT IGNORE INTO `%s`.`%s`(%s) VALUES %s";
    private static final String DELETE_SQL = "DELETE FROM `%s`.`%s` WHERE %s";
    private static final String UPDATE_SQL = "UPDATE `%s`.`%s` SET %s WHERE %s";
    private static final String SELECT_SQL = "SELECT * FROM `%s`.`%s` WHERE %s";
    private static final String IF_NOT_EXISTS = " IF NOT EXISTS ";
    private static final String IF_EXISTS = " IF EXISTS ";
    private static final String DDL_TOKEN_PATTERN = "DDL_SUBMIT_TOKEN='%s'";
    private static final String DDL_TOKEN_HINT = "/*+TDDL:cmd_extra(" + DDL_TOKEN_PATTERN + ")*/";
    private static final String SHOW_FULL_DDL = "SHOW FULL DDL";
    private static final String CHECK_DDL_STATE =
        "select * from metadb.ddl_engine_archive where state = '%s' and ddl_stmt like '%%%s%%' and gmt_created >= %s";
    private static final String DDL_STMT = "DDL_STMT";
    private static final String DDL_STATE = "STATE";
    private static final String DDL_STATE_PENDING = "PENDING";
    private static final String SET_SQL_MODE = "set sql_mode='%s'";

    public static boolean isDdl(DBMSEvent dbmsEvent) {
        switch (dbmsEvent.getAction()) {
        case CREATE:
        case ERASE:
        case ALTER:
        case RENAME:
        case TRUNCATE:
        case CREATEDB:
        case DROPDB:
        case CINDEX:
        case DINDEX:
            return true;
        default:
            return false;
        }
    }

    public static void tranExecUpdate(DataSource dataSource, List<SqlContext> sqlContexts) throws Exception {
        List<PreparedStatement> stmts = new ArrayList<>();
        SqlContext nowSqlContext = null;

        try (Connection conn = dataSource.getConnection()) {
            try {
                // start transaction
                conn.setAutoCommit(false);
                for (SqlContext sqlContext : sqlContexts) {
                    nowSqlContext = sqlContext;
                    PreparedStatement stmt = conn.prepareStatement(sqlContext.getSql());
                    stmts.add(stmt);
                    int j = 1;
                    for (Serializable dataValue : sqlContext.getParams()) {
                        stmt.setObject(j, dataValue);
                        j++;
                    }
                    stmt.executeUpdate();
                    logExecUpdateDebug(sqlContext);
                }
                conn.commit();
            } catch (Exception e) {
                try {
                    if (conn != null) {
                        conn.rollback();
                    }
                } catch (Exception e1) {
                    log.error("failed in tranExecUpdate, rollback failed", e1);
                }
                if (nowSqlContext != null) {
                    throw new PolardbxException(truncateString(nowSqlContext.getSql()), e);
                }
                throw new PolardbxException(e);
            }
        } finally {
            for (PreparedStatement stmt : stmts) {
                DataSourceUtil.closeQuery(null, stmt, null);
            }
        }
    }

    public static void execUpdate(DataSource dataSource, SqlContext sqlContext) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            // 设置sql mode
            if (null != sqlContext.getSqlMode()) {
                try (Statement statement = conn.createStatement()) {
                    statement.execute(String.format(SET_SQL_MODE, sqlContext.getSqlMode()));
                }
            }
            try (PreparedStatement stmt = conn.prepareStatement(sqlContext.getSql())) {
                if (sqlContext.getParams() != null) {
                    // set value
                    int i = 1;
                    for (Serializable dataValue : sqlContext.getParams()) {
                        stmt.setObject(i, dataValue);
                        i++;
                    }
                }
                stmt.executeUpdate();
                // 不再支持多语句！
                logExecUpdateDebug(sqlContext);
            }
        } catch (Exception e) {
            throw new PolardbxException(truncateString(sqlContext.getSql()), e);
        }
    }

    public static void execUpdate(DataSource dataSource, SqlContextV2 sqlContext) {
        try (Connection conn = dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sqlContext.getSql())) {
            if (sqlContext.getParamsList() != null) {
                for (List<Serializable> values : sqlContext.getParamsList()) {
                    int i = 1;
                    for (Serializable dataValue : values) {
                        stmt.setObject(i, dataValue);
                        i++;
                    }
                    stmt.addBatch();
                }
            }
            // int[] results = stmt.executeBatch();
            stmt.executeBatch();
        } catch (Exception e) {
            throw new PolardbxException(truncateString(sqlContext.getSql()), e);
        }
    }

    @SneakyThrows
    public static boolean checkIfDdlSucceed(DataSource dataSource, String token, Date ddlCreateTime) {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = dataSource.getConnection();
            stmt = conn.createStatement();
            String checkSql = String.format(CHECK_DDL_STATE, "COMPLETED", token, ddlCreateTime.getTime());

            rs = stmt.executeQuery(checkSql);
            while (rs.next()) {
                String ddlStmt = rs.getString(DDL_STMT);
                String tsoPattern = String.format(DDL_TOKEN_PATTERN, token);
                if (ddlStmt.contains(tsoPattern)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("failed in getAsyncDdlState, tso: {}", token, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
    }

    @SneakyThrows
    public static boolean checkIfDdlRunning(DataSource dataSource, String token) {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(SHOW_FULL_DDL);
            rs = stmt.executeQuery(SHOW_FULL_DDL);
            while (rs.next()) {
                String ddlStmt = rs.getString(DDL_STMT);
                String tsoPattern = String.format(DDL_TOKEN_PATTERN, token);
                if (ddlStmt.contains(tsoPattern)) {
                    return true;
                }
            }
            return false;
        } catch (Throwable e) {
            log.error("failed in getAsyncDdlState, tso: {}", token, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
    }

    public static SqlContext getDdlSqlContext(DefaultQueryLog queryLog, String token) {

        SqlContext sqlContext = new SqlContext(queryLog.getQuery(), "", "", new ArrayList<>(),
            SqlModeUtil.convertSqlMode(queryLog.getSqlMode()));
        String originSql = DdlHelper.getOriginSql(queryLog.getQuery());
        String sql = StringUtils.isNotBlank(originSql) ? originSql : queryLog.getQuery();
        String hint = String.format(DDL_TOKEN_HINT, token);
        sqlContext.setSql(hint + sql);
        // use actual schemaName
        return processDdlSql(sqlContext, queryLog.getSchema());
    }

    public static SqlContext processDdlSql(SqlContext sqlContext, String schema) {
        // use actual schemaName
        List<DdlResult> resultList = DruidDdlParser.parse(sqlContext.getSql(), schema);
        if (CollectionUtils.isEmpty(resultList)) {
            log.warn("DruidDdlParser parse error, sql: {}", sqlContext.getSql());
            return null;
        }
        DdlResult result = resultList.get(0);
        sqlContext.setDstSchema(result.getSchemaName());
        sqlContext.setDstTable(result.getTableName());
        switch (result.getType()) {
        case CREATEDB:
            if (!result.getHasIfExistsOrNotExists()) {
                sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)create database",
                    "create database if not exists"));
            }
            sqlContext.setDstSchema("");
            break;
        case DROPDB:
            if (!result.getHasIfExistsOrNotExists()) {
                sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)drop database",
                    "drop database if exists"));
            }
            sqlContext.setDstSchema("");
            break;
        case CREATE:
            if (!result.getHasIfExistsOrNotExists()) {
                sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)create table",
                    "create table if not exists"));
            }
            break;
        case ERASE:
            if (!result.getHasIfExistsOrNotExists()) {
                sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)drop table",
                    "drop table if exists"));
            }
            break;
        default:
            break;
        }
        return sqlContext;
    }

    public static boolean isFiltered(DBMSColumn column) {
        return column.isGenerated() || column.isRdsImplicitPk();
    }

    public static List<SqlContext> getInsertSqlExecContext(DBMSRowChange rowChange, TableInfo dstTbInfo,
                                                           int insertMode) {
        int rowCount = rowChange.getRowSize();
        List<SqlContext> contexts = Lists.newArrayListWithCapacity(rowCount);
        for (int i = 1; i <= rowCount; i++) {
            // WHERE {column1} = {value1} AND {column2} = {value2}
            // REPLACE INTO t1(column1, column2) VALUES (value1, value2)
            StringBuilder nameSqlSb = new StringBuilder();
            StringBuilder valueSqlSb = new StringBuilder();
            List<Serializable> params = new ArrayList<>();
            generateSql(nameSqlSb, valueSqlSb, rowChange, params, i);
            String sql;
            switch (insertMode) {
            case RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE:
                sql = BATCH_INSERT_SQL;
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
            SqlContext context = new SqlContext(insertSql, dstTbInfo.getSchema(), dstTbInfo.getName(), params);
            contexts.add(context);
        }

        return contexts;
    }

    public static List<SqlContext> getDeleteThenReplaceSqlExecContext(DBMSRowChange rowChange, TableInfo dstTbInfo) {
        int rowCount = rowChange.getRowSize();
        List<SqlContext> contexts = Lists.newArrayListWithCapacity(rowCount * 2);

        for (int i = 1; i <= rowCount; i++) {
            // WHERE {column1} = {value1} AND {column2} = {value2}
            if (!getWhereColumns(dstTbInfo).isEmpty()) {
                StringBuilder whereSqlSb = new StringBuilder();
                List<Serializable> whereParams = new ArrayList<>();
                getWhereSql(rowChange, i, dstTbInfo, whereSqlSb, whereParams);
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
            generateSql(nameSqlSb, valueSqlSb, rowChange, params, i);
            String insertSql = String
                .format(REPLACE_SQL,
                    CommonUtils.escape(dstTbInfo.getSchema()),
                    CommonUtils.escape(dstTbInfo.getName()),
                    nameSqlSb,
                    valueSqlSb);
            SqlContext context2 = new SqlContext(insertSql, dstTbInfo.getSchema(), dstTbInfo.getName(), params);
            contexts.add(context2);
        }

        return contexts;
    }

    public static MergeDmlSqlContext getMergeInsertSqlExecContext(DBMSRowChange rowChange, TableInfo dstTbInfo,
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
            sql = BATCH_INSERT_SQL;
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

    public static SqlContextV2 getMergeInsertSqlExecContextV2(DBMSRowChange rowChange, TableInfo dstTbInfo,
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
        if (StringUtils.isBlank(dstTbInfo.getSqlTemplate().get(insertMode))) {
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
                sql = BATCH_INSERT_SQL;
                break;
            }
            dstTbInfo.getSqlTemplate().put(insertMode,
                String.format(sql, CommonUtils.escape(dstTbInfo.getSchema()),
                    CommonUtils.escape(dstTbInfo.getName()), nameSqlSb, valueSqlSb));
        }
        return new SqlContextV2(dstTbInfo.getSqlTemplate().get(insertMode),
            dstTbInfo.getSchema(), dstTbInfo.getName(), paramsList);
    }

    public static List<SqlContext> getDeleteSqlExecContext(DBMSRowChange rowChange, TableInfo dstTbInfo) {
        // actually, only 1 row in a rowChange
        int rowCount = rowChange.getRowSize();
        List<SqlContext> contexts = Lists.newArrayListWithCapacity(rowCount);

        for (int i = 1; i <= rowCount; i++) {
            // WHERE {column1} = {value1} AND {column2} = {value2}
            StringBuilder whereSqlSb = new StringBuilder();
            List<Serializable> params = new ArrayList<>();
            getWhereSql(rowChange, i, dstTbInfo, whereSqlSb, params);

            String deleteSql = String.format(DELETE_SQL,
                CommonUtils.escape(dstTbInfo.getSchema()), CommonUtils.escape(dstTbInfo.getName()), whereSqlSb);
            SqlContext context = new SqlContext(deleteSql, dstTbInfo.getSchema(), dstTbInfo.getName(), params);
            contexts.add(context);
        }

        return contexts;
    }

    public static MergeDmlSqlContext getMergeDeleteSqlExecContext(DBMSRowChange rowChange, TableInfo dstTbInfo) {
        StringBuilder whereSqlSb = new StringBuilder();
        List<Serializable> params = new ArrayList<>();
        getWhereInSqlV2(rowChange, dstTbInfo, whereSqlSb, params);
        String deleteSql = String.format(DELETE_SQL, CommonUtils.escape(dstTbInfo.getSchema()),
            CommonUtils.escape(dstTbInfo.getName()), whereSqlSb);
        return new MergeDmlSqlContext(deleteSql, dstTbInfo.getSchema(), dstTbInfo.getName(), params);
    }

    public static List<SqlContext> getUpdateSqlExecContext(DBMSRowChange rowChange, TableInfo dstTbInfo) {
        // List<? extends DBMSColumn> changeColumns = rowChange.getChangeColumns();
        List<? extends DBMSColumn> changeColumns = rowChange.getColumns();

        int rowCount = rowChange.getRowSize();
        List<SqlContext> contexts = Lists.newArrayListWithCapacity(rowCount);

        for (int i = 1; i <= rowCount; i++) {
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
                Serializable changeColumnValue = rowChange.getChangeValue(i, changeColumn.getName());
                params.add(changeColumnValue);
            }
            trimLastComma(setSqlSb);

            // WHERE {column1} = {value1} AND {column2} = {value2}
            StringBuilder whereSqlSb = new StringBuilder();
            List<Serializable> whereColumnValues = new ArrayList<>();
            getWhereSql(rowChange, i, dstTbInfo, whereSqlSb, whereColumnValues);

            params.addAll(whereColumnValues);
            String updateSql = String
                .format(UPDATE_SQL, CommonUtils.escape(dstTbInfo.getSchema()),
                    CommonUtils.escape(dstTbInfo.getName()), setSqlSb, whereSqlSb);
            SqlContext context = new SqlContext(updateSql, dstTbInfo.getSchema(), dstTbInfo.getName(), params);
            contexts.add(context);
        }

        return contexts;
    }

    public static List<String> getWhereColumns(TableInfo tableInfo) {
        return tableInfo.getKeyList();
    }

    private static void getWhereSql(DBMSRowChange rowChange, int rowIndex, TableInfo tableInfo,
                                    StringBuilder whereSqlSb,
                                    List<Serializable> whereColumnValues) {
        List<String> whereColumns = getWhereColumns(tableInfo);

        for (int i = 0; i < whereColumns.size(); i++) {
            String columnName = whereColumns.get(i);

            // build where sql
            Serializable whereColumnValue = rowChange.getRowValue(rowIndex, columnName);
            String repairedName = repairDMLName(columnName);
            if (whereColumnValue == null) {
                // _drds_implicit_id_ should never be null
                whereSqlSb.append(repairedName).append(" IS NULL ");
            } else {
                whereSqlSb.append(repairedName).append("=?");
            }

            if (i < whereColumns.size() - 1) {
                whereSqlSb.append(" AND ");
            }

            // fill in where column values
            if (whereColumnValue != null) {
                whereColumnValues.add(whereColumnValue);
            }
        }
    }

    private static void getWhereInSql(DBMSRowChange rowChange, TableInfo tableInfo, StringBuilder whereSqlSb,
                                      List<Serializable> whereColumnValues) {
        List<String> whereColumns = getWhereColumns(tableInfo);

        // WHERE (column1, column2) in
        whereSqlSb.append("(");
        for (int i = 0; i < whereColumns.size(); i++) {
            String columnName = whereColumns.get(i);
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
                String columnName = whereColumns.get(j);
                whereColumnValues.add(rowChange.getRowValue(i, columnName));
            }
            whereSqlSb.append(")");
            if (i < rowChange.getRowSize()) {
                whereSqlSb.append(',');
            }
        }
        whereSqlSb.append(")");
    }

    private static void getWhereInSqlV2(DBMSRowChange rowChange, TableInfo tableInfo, StringBuilder whereSqlSb,
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

    private static void logExecUpdateDebug(SqlContext sqlContext) {
        if (!log.isDebugEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (Serializable p : sqlContext.getParams()) {
            if (p == null) {
                sb.append("null-value").append(RplConstants.COMMA);
            } else {
                sb.append(p).append(RplConstants.COMMA);
            }
        }
        log.debug("execUpdate, sql: {}, params: {}", sqlContext.getSql(), sb);
    }

    private static String repairDMLName(String name) {
        return "`" + escape(name) + "`";
    }

    private static void trimLastComma(StringBuilder builder) {
        if (builder.charAt(builder.length() - 1) == ',') {
            builder.setLength(builder.length() - 1);
        }
    }

    private static void generateSql(StringBuilder nameSqlSb, StringBuilder valueSqlSb, DBMSRowChange rowChange,
                                    List<Serializable> params, int rowIndex) {
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
            Serializable columnValue = rowChange.getRowValue(rowIndex, column.getName());
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
}
