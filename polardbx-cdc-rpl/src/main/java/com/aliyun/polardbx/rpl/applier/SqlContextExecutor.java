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

import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.RplConstants;
import lombok.extern.slf4j.Slf4j;

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

            try (PreparedStatement stmt = conn.prepareStatement(sqlContext.getSql())) {
                if (sqlContext.getParams() != null) {
                    // set value
                    int i = 1;
                    for (Serializable dataValue : sqlContext.getParams()) {
                        stmt.setObject(i, dataValue);
                        i++;
                    }
                }
                logExecUpdateDebug(sqlContext);
                return stmt.executeUpdate();
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
                        stmt.setObject(i, dataValue);
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
