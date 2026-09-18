/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.JdbcParameterBinder;
import com.aliyun.polardbx.rpl.common.RplConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;

import javax.sql.DataSource;
import java.io.Serializable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-12-04 13:51
 **/
@Slf4j
public class SqlContextExecutor {

    private static final String SET_SQL_MODE = "set sql_mode='%s'";
    private static final String QUERY_SQL_MODE = "show variables like 'sql_mode'";

    public static int execUpdate(Connection conn, SqlContext sqlContext) throws SQLException {
        return executeWithContext(conn, sqlContext, false);
    }

    /**
     * DDL and administrative statements (for example ANALYZE TABLE) may return a result set.
     * Do not use executeUpdate here, or interpret execute's result-set flag as execution success.
     * DML callers must keep using execUpdate so their affected-row checks retain their semantics.
     */
    public static void execDdl(Connection conn, SqlContext sqlContext) throws SQLException {
        executeWithContext(conn, sqlContext, true);
    }

    private static int executeWithContext(Connection conn, SqlContext sqlContext, boolean ddl) throws SQLException {
        String holdingSqlMode = null;
        try {
            if (null != sqlContext.getSqlMode()) {
                holdingSqlMode = querySqlMode(conn);
                try (Statement statement = conn.createStatement()) {
                    statement.execute(String.format(SET_SQL_MODE, sqlContext.getSqlMode()));
                }
            }

            if (null != sqlContext.getFpOverrideNow()) {
                try (Statement statement = conn.createStatement()) {
                    statement.execute(
                        String.format("set @FP_OVERRIDE_NOW='%s'", sqlContext.getFpOverrideNow()));
                }
            }

            if (CollectionUtils.isEmpty(sqlContext.getParams())) {
                try (Statement statement = conn.createStatement()) {
                    logExecUpdateDebug(sqlContext);
                    if (ddl) {
                        statement.execute(sqlContext.getSql());
                        return 0;
                    }
                    return statement.executeUpdate(sqlContext.getSql());
                }
            } else {
                try (PreparedStatement stmt = conn.prepareStatement(sqlContext.getSql())) {
                    int i = 1;
                    for (Serializable dataValue : sqlContext.getParams()) {
                        JdbcParameterBinder.bind(stmt, i, dataValue);
                        i++;
                    }
                    logExecUpdateDebug(sqlContext);
                    if (ddl) {
                        stmt.execute();
                        return 0;
                    }
                    return stmt.executeUpdate();
                }
            }
        } finally {
            if (null != sqlContext.sqlMode && holdingSqlMode != null) {
                try (Statement statement = conn.createStatement()) {
                    statement.execute(String.format(SET_SQL_MODE, holdingSqlMode));
                }
            }

            if (null != sqlContext.getFpOverrideNow()) {
                try (Statement statement = conn.createStatement()) {
                    statement.execute("set @FP_OVERRIDE_NOW=null");
                }
            }

        }
    }

    public static void execSqlContextsV2(DataSource dataSource, List<SqlContextV2> sqlContexts) throws SQLException {
        if (sqlContexts == null || sqlContexts.isEmpty()) {
            return;
        }
        for (SqlContextV2 sqlContext : sqlContexts) {
            long startTime = System.currentTimeMillis();
            execUpdate(dataSource, sqlContext);
            long endTime = System.currentTimeMillis();
            StatMetrics.getInstance().addApplyCount(1);
            StatMetrics.getInstance().addRt(endTime - startTime);
        }
    }

    public static void execUpdate(DataSource dataSource, SqlContextV2 sqlContext) throws SQLException {
        try (Connection conn = dataSource.getConnection();
            PreparedStatement stmt = conn.prepareStatement(sqlContext.getSql())) {
            if (sqlContext.getParamsList() != null) {
                for (List<Serializable> values : sqlContext.getParamsList()) {
                    int i = 1;
                    for (Serializable dataValue : values) {
                        JdbcParameterBinder.bind(stmt, i, dataValue);
                        i++;
                    }
                    stmt.addBatch();
                }
            }
            // int[] results = stmt.executeBatch();
            stmt.executeBatch();
        }
    }

    public static void logExecUpdateDebug(SqlContext sqlContext) {
        if (!log.isDebugEnabled()) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (sqlContext.getParams() == null) {
            log.debug("execUpdate, sql: {}, params: null", sqlContext.getSql());
            return;
        }
        for (Serializable p : sqlContext.getParams()) {
            if (p == null) {
                sb.append("null-value").append(RplConstants.COMMA);
            } else {
                sb.append(p).append(RplConstants.COMMA);
            }
        }
        log.debug("execUpdate, sql: {}, params: {}", sqlContext.getSql(), sb);
    }

    private static String querySqlMode(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(QUERY_SQL_MODE)) {
                if (resultSet.next()) {
                    return resultSet.getString(2);
                }
            }
        }
        throw new PolardbxException("query sql mode failed!");
    }
}
