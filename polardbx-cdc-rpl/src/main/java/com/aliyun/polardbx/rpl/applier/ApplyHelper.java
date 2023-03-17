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

import com.aliyun.polardbx.binlog.CommonUtils;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DdlResult;
import com.aliyun.polardbx.binlog.canal.core.ddl.parser.DruidDdlParser;
import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.DdlState;
import com.google.common.collect.Lists;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import static com.aliyun.polardbx.binlog.CommonUtils.escape;

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
    private static final String CREATE_TABLE = "CREATE TABLE";
    private static final String DROP_TABLE = "DROP TALBE";
    private static final String ASYNC_DDL_HINT = "/*+TDDL:cmd_extra(PURE_ASYNC_DDL_MODE=TRUE,TSO=%s)*/";
    private static final String DDL_HINT = "/*+TDDL:cmd_extra(TSO=%s)*/";
    private static final String SHOW_FULL_DDL = "SHOW FULL DDL";
    private static final String CHECK_DDL = "select * from metadb.ddl_engine_archive where ddl_stmt like '%%%s%%';";
    private static final String TSO_PATTERN = "TSO=%s)";
    private static final String DDL_STMT = "DDL_STMT";
    private static final String DDL_STATE = "STATE";
    private static final String DDL_STATE_PENDING = "PENDING";

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

    public static boolean tranExecUpdate(DataSource dataSource, List<SqlContext> sqlContexts) {
        Connection conn = null;
        List<PreparedStatement> stmts = new ArrayList<>();
        SqlContext sqlContext = null;

        try {
            conn = dataSource.getConnection();
            // start transaction
            conn.setAutoCommit(false);

            for (int i = 0; i < sqlContexts.size(); i++) {
                sqlContext = sqlContexts.get(i);
                PreparedStatement stmt = conn.prepareStatement(sqlContext.getSql());
                stmts.add(stmt);
                // set value
                int j = 1;
                for (Serializable dataValue : sqlContext.getParams()) {
                    stmt.setObject(j, dataValue);
                    j++;
                }
                // execute
                stmt.executeUpdate();
                logExecUpdateDebug(sqlContext);
            }

            // commit
            conn.commit();
            return true;
        } catch (Throwable e) {
            logExecUpdateError(sqlContext, e);
            try {
                conn.rollback();
            } catch (Throwable e1) {
                log.error("failed in tranExecUpdate, rollback failed", e1);
            }
            return false;
        } finally {
            for (PreparedStatement stmt : stmts) {
                DataSourceUtil.closeQuery(null, stmt, null);
            }
            DataSourceUtil.closeQuery(null, null, conn);
        }
    }

    public static List<SqlContext> mergeSendBatchSqlContexts(List<SqlContext> sqlContexts, int sendBatchSize) {
        List<SqlContext> results = new ArrayList<>();
        // todo jiyue, using addbatch
        if (sendBatchSize <= 1) {
            return sqlContexts;
        }
        int i = 0;
        while (i < sqlContexts.size()) {
            StringBuilder sqlSb = new StringBuilder();
            List<Serializable> params = new ArrayList<>();
            for (int j = 0; j < sendBatchSize && i < sqlContexts.size(); j++) {
                SqlContext sqlContext = sqlContexts.get(i);
                sqlSb.append(sqlContext.getSql()).append(";");
                params.addAll(sqlContext.getParams());
                i++;
            }

            SqlContext batchSqlContext = new SqlContext(sqlSb.toString(), "", "", params);
            results.add(batchSqlContext);
        }

        return results;
    }

    public static boolean tranExecUpdateV1(DataSource dataSource, List<SqlContext> sqlContexts) {
        if (sqlContexts.size() == 0) {
            return true;
        }
        Connection conn = null;
        PreparedStatement stmt = null;
        StringBuilder sqlSb = new StringBuilder();
        // 合并多sql语句
        for (SqlContext sqlContext : sqlContexts) {
            sqlSb.append(sqlContext.getSql()).append(";");
        }
        String sql = sqlSb.toString();
        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sql);
            conn.setAutoCommit(false);
            int i = 1;
            for (SqlContext sqlContext : sqlContexts) {
                for (Serializable param : sqlContext.getParams()) {
                    stmt.setObject(i, param);
                    i++;
                }
            }
            // 执行SQL
            i = 0;
            stmt.execute();
            do {
                int affectRow = stmt.getUpdateCount();
                if (affectRow == -1) {
                    // 没有更多的结果了
                    break;
                }
                stmt.getMoreResults();
                i++;
            } while (true);
            conn.commit();
            return true;
        } catch (Exception e) {
            // logExecUpdateError(sqlContext, e);
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (Exception e1) {
                log.error("failed in tranExecUpdate, rollback failed", e1);
            }
        } finally {
            DataSourceUtil.closeQuery(null, stmt, conn);
        }
        return false;
    }

    public static boolean execUpdate(DataSource dataSource, SqlContext sqlContext) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sqlContext.getSql());
            if (sqlContext.getParams() != null) {
                // set value
                int i = 1;
                for (Serializable dataValue : sqlContext.getParams()) {
                    stmt.setObject(i, dataValue);
                    i++;
                }
            }
            stmt.executeUpdate();
            while (true) {
                int affectedRows = stmt.getUpdateCount();
                if (affectedRows == -1) {
                    // 没有更多的结果了
                    break;
                }
                stmt.getMoreResults();
            }
            if (log.isDebugEnabled()) {
                logExecUpdateDebug(sqlContext);
            }
            return true;
        } catch (Throwable e) {
            logExecUpdateError(sqlContext, e);
            return false;
        } finally {
            DataSourceUtil.closeQuery(null, stmt, conn);
        }
    }

    public static boolean execUpdate(DataSource dataSource, SqlContextV2 sqlContext) {
        Connection conn = null;
        PreparedStatement stmt = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(sqlContext.getSql());

            if (sqlContext.getParamsList() != null) {
                for (List<Serializable> values: sqlContext.getParamsList()) {
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
            return true;
        } catch (Throwable e) {
            logExecUpdateError(sqlContext, e);
            return false;
        } finally {
            DataSourceUtil.closeQuery(null, stmt, conn);
        }
    }


    public static void tryRepairRplDdlState(DataSource dataSource, String tso, RplDdl ddl) throws Exception{
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = dataSource.getConnection();
            stmt = conn.createStatement();
            String checkDdl = String.format(CHECK_DDL, tso);
            rs = stmt.executeQuery(checkDdl);
            while (rs.next()) {
                String ddlStmt = rs.getString(DDL_STMT);
                String tsoPattern = String.format(TSO_PATTERN, tso);
                if (ddlStmt.contains(tsoPattern)) {
                    // set ddl status to its true state
                    RplDdl newDdl = new RplDdl();
                    newDdl.setId(ddl.getId());
                    newDdl.setState(rs.getString(DDL_STATE).contains(DDL_STATE_PENDING) ? DdlState.FAILED.getValue()
                        : DdlState.RUNNING.getValue());
                    DbTaskMetaManager.updateDdl(newDdl);
                }
            }
            // if ddl done, should not change state of rplDdl
        } catch (Exception e) {
            log.error("failed in getAsyncDdlState, tso: {}", tso, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
    }

    public static DdlState getAsyncDdlState(DataSource dataSource, String tso) throws Throwable {
        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;

        try {
            conn = dataSource.getConnection();
            stmt = conn.prepareStatement(SHOW_FULL_DDL);
            rs = stmt.executeQuery(SHOW_FULL_DDL);
            while (rs.next()) {
                String ddlStmt = rs.getString(DDL_STMT);
                String tsoPattern = String.format(TSO_PATTERN, tso);
                if (ddlStmt.contains(tsoPattern)) {
                    return rs.getString(DDL_STATE).contains(DDL_STATE_PENDING) ? DdlState.FAILED : DdlState.RUNNING;
                }
            }
            // if ddl done, show full ddl will be empty
            return DdlState.SUCCEED;
        } catch (Throwable e) {
            log.error("failed in getAsyncDdlState, tso: {}", tso, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
    }

    public static SqlContext getDdlSqlContext(DefaultQueryLog queryLog, String tso, boolean useOriginalSql) {
        SqlContext sqlContext = new SqlContext(queryLog.getQuery(), "", "", new ArrayList<>());
        String originSql = DdlHelper.getOriginSql(queryLog.getQuery());
        String sql = (useOriginalSql && StringUtils.isNotBlank(originSql)) ? originSql: queryLog.getQuery();
        String hint = String.format(DDL_HINT, tso);
        sqlContext.setSql(hint + sql);
        // use actual schemaName
        return processDdlSql(sqlContext, queryLog.getSchema());
//        List<DdlResult> resultList = DruidDdlParser.parse(sql, queryLog.getSchema());
//        if (CollectionUtils.isEmpty(resultList)) {
//            log.warn("DruidDdlParser parse error, origin sql: {}, queryLog sql: {}", sql, queryLog.getQuery());
//            return null;
//        }
//        DdlResult result = resultList.get(0);
//        sqlContext.setDstSchema(result.getSchemaName());
//        switch (queryLog.getAction()) {
//        case CREATEDB:
//            if (!result.getHasIfExistsOrNotExists()) {
//                sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)create database",
//                    "create database if not exists"));
//            }
//            sqlContext.setDstSchema("");
//            break;
//        case DROPDB:
//            if (!result.getHasIfExistsOrNotExists()) {
//                sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)drop database",
//                    "drop database if exists"));
//            }
//            sqlContext.setDstSchema("");
//            break;
//        case CREATE:
//            if (!result.getHasIfExistsOrNotExists()) {
//                sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)create table",
//                    "create table if not exists"));
//            }
//            break;
//        case ERASE:
//            if (!result.getHasIfExistsOrNotExists()) {
//                sqlContext.setSql(sqlContext.getSql().replaceAll("(?i)drop table",
//                    "drop table if exists"));
//            }
//            break;
//        default:
//            break;
//        }
//        return sqlContext;
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

    public static boolean isFiltered(String columnName) {
        return StringUtils.equalsIgnoreCase(RplConstants.RDS_IMPLICIT_ID, columnName);
    }

    public static List<SqlContext> getInsertSqlExecContext(DBMSRowChange rowChange, TableInfo dstTbInfo,
                                                           int insertMode) {
        List<? extends DBMSColumn> columns = rowChange.getColumns();

        int rowCount = rowChange.getRowSize();
        List<SqlContext> contexts = Lists.newArrayListWithCapacity(rowCount);
        for (int i = 1; i <= rowCount; i++) {
            // WHERE {column1} = {value1} AND {column2} = {value2}
            // REPLACE INTO t1(column1, column2) VALUES (value1, value2)
            StringBuilder nameSqlSb = new StringBuilder();
            StringBuilder valueSqlSb = new StringBuilder();
            List<Serializable> parmas = new ArrayList<>();

            valueSqlSb.append("(");
            Iterator<? extends DBMSColumn> it = columns.iterator();
            while (it.hasNext()) {
                DBMSColumn column = it.next();
                if (isFiltered(column.getName())) {
                    nameSqlSb.setLength(nameSqlSb.length() - 1);
                    valueSqlSb.setLength(valueSqlSb.length() - 1);
                    continue;
                }
                nameSqlSb.append(repairDMLName(column.getName()));
                valueSqlSb.append("?");
                if (it.hasNext()) {
                    nameSqlSb.append(",");
                    valueSqlSb.append(",");
                }

                Serializable columnValue = rowChange.getRowValue(i, column.getName());
                parmas.add(columnValue);
            }
            valueSqlSb.append(")");
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
                .format(sql,
                    CommonUtils.escape(dstTbInfo.getSchema()),
                    CommonUtils.escape(dstTbInfo.getName()),
                    nameSqlSb,
                    valueSqlSb);
            SqlContext context = new SqlContext(insertSql, dstTbInfo.getSchema(), dstTbInfo.getName(), parmas);
            contexts.add(context);
        }

        return contexts;
    }

    public static List<SqlContext> getDeleteThenReplaceSqlExecContext(DBMSRowChange rowChange, TableInfo dstTbInfo) {
        List<? extends DBMSColumn> columns = rowChange.getColumns();

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
            List<Serializable> parmas = new ArrayList<>();

            valueSqlSb.append("(");
            Iterator<? extends DBMSColumn> it = columns.iterator();
            while (it.hasNext()) {
                DBMSColumn column = it.next();
                if (isFiltered(column.getName())) {
                    nameSqlSb.setLength(nameSqlSb.length() - 1);
                    valueSqlSb.setLength(valueSqlSb.length() - 1);
                    continue;
                }
                nameSqlSb.append(repairDMLName(column.getName()));
                valueSqlSb.append("?");
                if (it.hasNext()) {
                    nameSqlSb.append(",");
                    valueSqlSb.append(",");
                }
                Serializable columnValue = rowChange.getChangeValue(i, column.getName());
                parmas.add(columnValue);
            }
            valueSqlSb.append(")");
            String insertSql = String
                .format(REPLACE_SQL,
                    CommonUtils.escape(dstTbInfo.getSchema()),
                    CommonUtils.escape(dstTbInfo.getName()),
                    nameSqlSb,
                    valueSqlSb);
            SqlContext context2 = new SqlContext(insertSql, dstTbInfo.getSchema(), dstTbInfo.getName(), parmas);
            contexts.add(context2);
        }

        return contexts;
    }

    public static MergeDmlSqlContext getMergeInsertUpdateSqlExecContext(DBMSRowChange rowChange, TableInfo dstTbInfo) {
        List<? extends DBMSColumn> columns = rowChange.getColumns();

        StringBuilder nameSqlSb = new StringBuilder();
        StringBuilder valueSqlSb = new StringBuilder();
        List<Serializable> parmas = new ArrayList<>();

        for (int i = 1; i <= rowChange.getRowSize(); i++) {
            // INSERT INTO t1(column1, column2) VALUES(value1, value2),(value3, value4)
            // ON DUPLICATE KEY UPDATE column1=VALUES(column1),columns2=VALUES(column2)
            if (i > 1) {
                valueSqlSb.append(",");
            }
            valueSqlSb.append("(");
            Iterator<? extends DBMSColumn> it = columns.iterator();
            while (it.hasNext()) {
                DBMSColumn column = it.next();
                if (isFiltered(column.getName())) {
                    if (i == 1) {
                        nameSqlSb.setLength(nameSqlSb.length() - 1);
                    }
                    valueSqlSb.setLength(valueSqlSb.length() - 1);
                    continue;
                }
                if (i == 1) {
                    nameSqlSb.append(repairDMLName(column.getName()));
                }
                valueSqlSb.append("?");

                if (it.hasNext()) {
                    if (i == 1) {
                        nameSqlSb.append(",");
                    }
                    valueSqlSb.append(",");
                }

                Serializable columnValue = rowChange.getRowValue(i, column.getName());
                parmas.add(columnValue);
            }
            valueSqlSb.append(")");
        }

        String insertSql = String
            .format(REPLACE_SQL, CommonUtils.escape(dstTbInfo.getSchema()),
                CommonUtils.escape(dstTbInfo.getName()), nameSqlSb, valueSqlSb);
        return new MergeDmlSqlContext(insertSql, dstTbInfo.getSchema(), dstTbInfo.getName(), parmas);
    }

    public static MergeDmlSqlContext getMergeInsertSqlExecContext(DBMSRowChange rowChange, TableInfo dstTbInfo,
                                                                  int insertMode) {
        List<? extends DBMSColumn> columns = rowChange.getColumns();
        StringBuilder nameSqlSb = new StringBuilder();
        StringBuilder valueSqlSb = new StringBuilder();
        List<Serializable> parmas = new ArrayList<>();
        for (int i = 1; i <= rowChange.getRowSize(); i++) {
            // INSERT INTO t1(column1, column2) VALUES(value1, value2),(value3, value4)
            // ON DUPLICATE KEY UPDATE column1=VALUES(column1),columns2=VALUES(column2)
            if (i > 1) {
                valueSqlSb.append(",");
            }
            valueSqlSb.append("(");
            Iterator<? extends DBMSColumn> it = columns.iterator();
            while (it.hasNext()) {
                DBMSColumn column = it.next();
                if (i == 1) {
                    nameSqlSb.append(repairDMLName(column.getName()));
                }
                valueSqlSb.append("?");

                if (it.hasNext()) {
                    if (i == 1) {
                        nameSqlSb.append(",");
                    }
                    valueSqlSb.append(",");
                }

                Serializable columnValue = rowChange.getRowValue(i, column.getName());
                parmas.add(columnValue);
            }
            valueSqlSb.append(")");
        }
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
        return new MergeDmlSqlContext(insertSql, dstTbInfo.getSchema(), dstTbInfo.getName(), parmas);
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
                    nameSqlSb.append(",");
                    valueSqlSb.append(",");
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
            List<Serializable> parmas = new ArrayList<>();

            Iterator<? extends DBMSColumn> it = changeColumns.iterator();
            while (it.hasNext()) {
                DBMSColumn changeColumn = it.next();
                setSqlSb.append(repairDMLName(changeColumn.getName())).append("=?");
                if (it.hasNext()) {
                    setSqlSb.append(",");
                }

                Serializable changeColumnValue = rowChange.getChangeValue(i, changeColumn.getName());
                parmas.add(changeColumnValue);
            }

            // WHERE {column1} = {value1} AND {column2} = {value2}
            StringBuilder whereSqlSb = new StringBuilder();
            List<Serializable> whereColumnValues = new ArrayList<>();
            getWhereSql(rowChange, i, dstTbInfo, whereSqlSb, whereColumnValues);

            parmas.addAll(whereColumnValues);
            String updateSql = String
                .format(UPDATE_SQL, CommonUtils.escape(dstTbInfo.getSchema()),
                    CommonUtils.escape(dstTbInfo.getName()), setSqlSb, whereSqlSb);
            SqlContext context = new SqlContext(updateSql, dstTbInfo.getSchema(), dstTbInfo.getName(), parmas);
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
                whereSqlSb.append(",");
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
                    whereSqlSb.append(",");
                }
                String columnName = whereColumns.get(j);
                whereColumnValues.add(rowChange.getRowValue(i, columnName));
            }
            whereSqlSb.append(")");
            if (i < rowChange.getRowSize()) {
                whereSqlSb.append(",");
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

    private static void logExecUpdateError(SqlContext sqlContext, Throwable e) {
        log.error("failed in execUpdate, sql: {}, exception: {}",
            (StringUtils.isBlank(sqlContext.getSql()) || sqlContext.getSql().length() <= 200) ?
                sqlContext.getSql() : sqlContext.getSql().substring(0, 200),
            e.toString());
    }

    private static void logExecUpdateError(SqlContextV2 sqlContext, Throwable e) {
        log.error("failed in execUpdate, sql: {}, exception: {}",
            (StringUtils.isBlank(sqlContext.getSql()) || sqlContext.getSql().length() <= 200) ?
                sqlContext.getSql() : sqlContext.getSql().substring(0, 200),
            e.toString());
    }

    private static void logExecUpdateDebug(SqlContext sqlContext) {
        StringBuilder sb = new StringBuilder();
        for (Serializable p : sqlContext.getParams()) {
            if (p == null) {
                sb.append("null-value").append(RplConstants.COMMA);
            } else {
                sb.append(p).append(RplConstants.COMMA);
            }
        }
        log.info("execUpdate, sql: {}, params: {}", sqlContext.getSql(), sb);
    }


    private static String repairDMLName(String name) {
        return "`" + escape(name) + "`";
    }
}
