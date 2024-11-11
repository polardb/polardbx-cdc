/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.base;

import com.alibaba.druid.pool.DruidDataSource;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.ImmutableSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.isStrictType;
import static com.aliyun.polardbx.cdc.qatest.util.SqlUtils.printBytes;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

/**
 * created by ziyang.lb
 */
public class JdbcUtil {

    public static final String DEFAULT_PHY_DB = "mysql";
    public static final String DROP_TABLE_SQL = "drop table if exists %s";
    public static final String CREATE_TABLE_LIKE_SQL = "create table %s like %s";
    private static final Logger log = LoggerFactory.getLogger(JdbcUtil.class);
    private static final String URL_PATTERN = "jdbc:mysql://%s:%s/%s?%s";
    public static final String DEFAULT_CONN_PROPS =
        "useUnicode=true&characterEncoding=utf8&useSSL=false&connectTimeout=5000&socketTimeout=12000";

    /**
     * 关闭statment
     */
    public static void close(Statement statement) {
        try {
            if (statement != null) {
                statement.close();
            }
        } catch (SQLException e) {
            String errorMs = "[Close statement] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
    }

    /**
     * 关闭resultset
     */
    public static void close(ResultSet resultSet) {
        try {
            if (resultSet != null) {
                resultSet.close();
            }
        } catch (SQLException e) {
            String errorMs = "[Close resultSet] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
    }

    /**
     * 关闭connection
     */
    public static void close(Connection conn) {
        try {
            if (conn != null) {
                conn.close();
            }
        } catch (SQLException e) {
            String errorMs = "[Close conn] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
    }

    /**
     * createStatement, 如果失败则退出
     */
    public static Statement createStatement(Connection conn) {
        Statement statement = null;
        try {
            statement = conn.createStatement();
        } catch (SQLException e) {
            String errorMs = "[Create statement] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
        return statement;
    }

    /**
     * 执行sql,如果失败则assertError
     */
    public static void executeSuccess(Connection conn, String sql) {
        Statement statement = createStatement(conn);
        try {
            statement.execute(sql);
        } catch (SQLException e) {
            String errorMs = "[Statement execute] failed:" + sql;
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        } finally {
            close(statement);
        }
    }

    /**
     * 执行sql,如果失败则assertError
     */
    public static void executeSuccess(Statement statement, String sql) {
        try {
            statement.execute(sql);
        } catch (SQLException e) {
            String errorMs = "[Statement executeSuccess] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        } finally {
            close(statement);
        }
    }

    /**
     * 执行sql,如果失败则assertError
     */
    public static void executeSuccess(PreparedStatement preparedStatement) {
        try {
            preparedStatement.execute();
        } catch (SQLException e) {
            String errorMs = "[PreparedStatement executeSuccess] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
    }

    /**
     * 执行查询,如果失败则assertError
     */
    public static ResultSet executeQuery(String sql, Statement statement) {
        ResultSet rs = null;
        try {
            rs = statement.executeQuery(sql.trim());
        } catch (SQLException e) {
            String errorMs = "[Execute statement query] failed and sql is : " + sql;
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
        return rs;
    }

    /**
     * 执行查询,如果失败则assertError
     */
    public static ResultSet executeQuery(String sql, PreparedStatement ps) {
        ResultSet rs = null;
        try {
            rs = ps.executeQuery();
        } catch (SQLException e) {
            String errorMs = "[Execute preparedStatement query] failed! sql is: " + sql;
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n" + e);
        }
        return rs;
    }

    /**
     * 执行查询,如果失败则assertError
     */
    public static ResultSet executeQuery(String sql, Connection c) {
        Statement ps = createStatement(c);
        ResultSet rs = null;
        try {
            rs = ps.executeQuery(sql);
        } catch (SQLException e) {
            String errorMs = "[Execute preparedStatement query] failed! sql is: " + sql;
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n" + e);
        }
        return rs;
    }

    /**
     * 执行查询,拿到第一列String类型的结果
     */
    public static String executeQueryAndGetFirstStringResult(String sql, Connection c) {
        return executeQueryAndGetStringResult(sql, c, 1);
    }

    /**
     * 执行查询,拿到第一列String类型的结果
     */
    public static String executeQueryAndGetStringResult(String sql, Connection c, int columnIndex) {
        Statement ps = createStatement(c);
        ResultSet rs = null;
        String result = "";
        try {
            rs = ps.executeQuery(sql);
            if (rs.next()) {
                result = rs.getString(columnIndex);
            }
        } catch (SQLException e) {
            String errorMs = "[Execute preparedStatement query] failed! sql is: " + sql;
            log.error(errorMs, e);
//            Assert.fail(errorMs + " \n" + e);
        }
        return result;
    }

    public static List<String> executeQueryAndGetStringList(String sql, Connection c, int columnIndex) {
        Statement ps = createStatement(c);
        ResultSet rs = null;
        List<String> result = new ArrayList<>();
        try {
            rs = ps.executeQuery(sql);
            while (rs.next()) {
                result.add(rs.getString(columnIndex));
            }
        } catch (SQLException e) {
            String errorMs = "[Execute preparedStatement query] failed! sql is: " + sql;
            log.error(errorMs, e);
//            Assert.fail(errorMs + " \n" + e);
        }
        return result;
    }

    /**
     * 执行更新
     */
    public static int executeUpdate(String sql, PreparedStatement ps) {
        try {
            int i = ps.executeUpdate();
            return i;
        } catch (SQLException e) {
            String errorMs = "[Execute preparedStatement query] failed! sql is: " + sql;
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n" + e);
        }
        return -1;
    }

    /**
     * 执行executeUpdate
     */
    public static int executeUpdate(PreparedStatement preparedStatement) {
        int effectCount = 0;
        try {
            effectCount = preparedStatement.executeUpdate();
        } catch (SQLException e) {
            String errorMs = "[PreparedStatement executeSuccess] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
        return effectCount;
    }

    /**
     * 更新数据
     */
    public static int updateData(Connection conn, String sql, List<Object> param) {
        PreparedStatement preparedStatement = preparedStatementSet(sql, param, conn);
        int updateCount = executeWithUpdateCount(preparedStatement);
        close(preparedStatement);
        return updateCount;
    }

    /**
     * 更新数据
     */
    public static int updateData(PreparedStatement preparedStatement, String sql, List<Object> param) {
        preparedStatementSet(preparedStatement, param);
        int updateCount = executeWithUpdateCount(preparedStatement);
        return updateCount;
    }

    /**
     * 批量更新数据
     */
    public static int[] updateDataBatch(Connection conn, String sql, List<List<Object>> params) {
        int[] effectCount = new int[10];
        PreparedStatement ps = preparedStatementBatch(sql, conn);
        if (params == null) {
            int affect = executeUpdate(ps);
            close(ps);
            return new int[] {affect};
        } else {
            for (List<Object> param : params) {
                ps = preparedStatementSet(ps, param);
                preparedStatementAddBatch(ps);
            }

            try {
                effectCount = preparedStatementExecuteBatch(ps);
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
                Assert.fail("sql 执行失败: " + sql);

            }
            close(ps);
            return effectCount;
        }
    }

    /**
     * 批量更新数据
     */
    public static int[] updateDataBatchIgnoreErr(Connection conn, String sql, List<List<Object>> params) {
        int[] effectCount = new int[10];
        PreparedStatement ps = preparedStatementBatch(sql, conn);
        if (params == null) {
            int affect = executeUpdate(ps);
            close(ps);
            return new int[] {affect};
        } else {
            for (List<Object> param : params) {
                ps = preparedStatementSet(ps, param);
                preparedStatementAddBatch(ps);
            }

            try {
                effectCount = preparedStatementExecuteBatch(ps);
            } catch (SQLException e) {
            }
            close(ps);
            return effectCount;
        }
    }

    public static int[] updateDataBatchReturnKeys(Connection conn, String sql, List<List<Object>> params,
                                                  List<Long> keys) {
        int[] effectCount = new int[10];
        PreparedStatement ps = preparedStatementBatch(sql, conn);
        if (params == null) {
            int affect = executeUpdate(ps);
            close(ps);
            return new int[] {affect};
        } else {
            for (List<Object> param : params) {
                ps = preparedStatementSet(ps, param);
                preparedStatementAddBatch(ps);
            }

            try {
                effectCount = preparedStatementExecuteBatch(ps);
                ResultSet rs = ps.getGeneratedKeys();
                while (rs.next()) {
                    long key = rs.getLong(1);
                    keys.add(key);
                }
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
                Assert.fail("sql 执行失败: " + sql);

            }
            close(ps);
            return effectCount;
        }
    }

    /**
     * 批量更新数据
     */
    public static void updateDataBatchFailed(Connection conn, String sql, List<List<Object>> params, String errMsg) {
        PreparedStatement ps = preparedStatementBatch(sql, conn);
        if (params == null) {
            try {
                ps.executeUpdate();
                Assert.fail("sql语句应该实行失败: " + sql);
            } catch (Exception e) {
                // log.error(e.getMessage(), e);
                Assert.assertTrue(e.getMessage().contains(errMsg));
            }
            close(ps);
        } else {
            for (List<Object> param : params) {
                ps = preparedStatementSet(ps, param);
                preparedStatementAddBatch(ps);
            }

            try {
                preparedStatementExecuteBatch(ps);
                Assert.fail("sql语句应该实行失败: " + sql);
            } catch (Exception e) {
                // log.error(e.getMessage(), e);
                Assert.assertTrue(e.getMessage().contains(errMsg));
            }
            close(ps);
        }
    }

    /**
     * 批量更新数据
     */
    public static int[] updateDataBatch(PreparedStatement ps, String sql, List<List<Object>> params) {
        int[] effectCount = new int[10];
        if (params == null) {
            int affect = executeUpdate(ps);
            return new int[] {affect};
        } else {
            for (List<Object> param : params) {
                ps = preparedStatementSet(ps, param);
                preparedStatementAddBatch(ps);
            }
            try {
                effectCount = preparedStatementExecuteBatch(ps);
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
                Assert.fail("sql 执行失败: " + sql);

            }
            return effectCount;
        }
    }

    /**
     * 更新数据，获取更新数量
     */
    private static int executeWithUpdateCount(PreparedStatement preparedStatement) {
        executeUpdate(preparedStatement);
        int updateCount = 0;
        try {
            updateCount = preparedStatement.getUpdateCount();
        } catch (SQLException e) {
            String errorMs = "[preparedStatement getUpdateCount] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
        return updateCount;
    }

    /**
     * tddl 更新数据
     */
    public static int updateDataTddl(Connection conn, String sql, List<Object> param) {
        PreparedStatement preparedStatement = preparedStatementSet(sql, param, conn, true);
        int updateCount = executeWithUpdateCount(preparedStatement);
        close(preparedStatement);
        return updateCount;
    }

    /**
     * Test if preparedStatement(sql, columnNames) passes RETURN_GENERATED_KEYS
     * to server.
     *
     * @return first result of getGeneratedKeys()
     */
    public static long updateDataTddlAutoGen(Connection conn, String sql, List<Object> param, String colName) {
        PreparedStatement preparedStatement = preparedStatementAutoGen(sql, param, conn, colName);
        executeWithUpdateCount(preparedStatement);
        long genValue = 0;
        try {
            ResultSet rs = preparedStatement.getGeneratedKeys();
            Assert.assertTrue("get generated keys null", rs.next());

            genValue = rs.getLong(1);
        } catch (Exception e) {
            String errorMs = "[preparedStatement getGeneratedKeys] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
        close(preparedStatement);
        return genValue;
    }

    public static String getSqlMode(Connection mysqlConnection) {
        String sql = "select @@sql_mode";
        String originMySqlMode = null;
        ResultSet mysqlRs = null;
        PreparedStatement mysqlPs = null;

        try {
            mysqlPs = preparedStatementSet(sql, null, mysqlConnection);
            mysqlRs = executeQuery(sql, mysqlPs);
            mysqlRs.next();
            originMySqlMode = mysqlRs.getString(1);
//            log.info("*********SQLMODE*********" + originMySqlMode);

        } catch (Exception e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        } finally {
            close(mysqlPs);
            close(mysqlRs);
        }
        return originMySqlMode;
    }

    public static String getTimeZone(Connection mysqlConnection) {
        String sql = "show variables like \"time_zone\"";
        return executeQueryAndGetStringResult(sql, mysqlConnection, 2);
    }

    /**
     * preparedStatement Set
     */
    public static PreparedStatement preparedStatementSet(String sql, List<Object> param, Connection conn) {
        return preparedStatementSet(sql, param, conn, false);
    }

    /**
     * preparedStatement Set
     */
    public static PreparedStatement preparedStatementSet(String sql, List<Object> param, Connection conn,
                                                         boolean isTddl) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.prepareStatement(sql);
            preparedStatementSet(preparedStatement, param, isTddl);

        } catch (SQLException e) {
            log.error("[Create preparedStatement] failed, begin to rooback , sql is " + sql, e);
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
            } catch (SQLException e1) {
                String errorMs = "[Create preparedStatement] failed and [Transaction roolback] failed, sql is: " + sql;
                log.error(errorMs, e1);
                Assert.fail(errorMs + " \n " + e1);
            }
            Assert.fail("[Create preparedStatement] failed, sql is :" + sql);
        }

        return preparedStatement;
    }

    /**
     * preparedStatement Set
     */
    private static PreparedStatement preparedStatementSet(PreparedStatement preparedStatement, List<Object> param,
                                                          boolean isTddl) {
        if (param != null) {
            for (int i = 0; i < param.size(); i++) {
                try {
                    if (isTddl && param.get(i) == null) {
                        preparedStatement.setObject(i + 1, java.sql.Types.NULL);

                    } else {
                        preparedStatement.setObject(i + 1, param.get(i));
                    }
                } catch (Exception ex) {
                    String errorMs = "[preparedStatement] failed ";
                    log.error(errorMs, ex);
                    Assert.fail(errorMs + " \n " + ex);
                }
            }
        }
        return preparedStatement;
    }

    /**
     * preparedStatement,Batch
     */
    public static PreparedStatement preparedStatementBatch(String sql, Connection conn) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.prepareStatement(sql, 1);
        } catch (SQLException e) {
            log.error("[Create preparedStatement] failed, begin to rooback , sql is " + sql, e);
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
            } catch (SQLException e1) {
                String errorMs = "[Create preparedStatement] failed and [Transaction roolback] failed, sql is: " + sql;
                log.error(errorMs, e1);
                Assert.fail(errorMs + " \n " + e);
            }
            Assert.fail("[Create preparedStatement] failed, sql is :" + sql);
        }

        return preparedStatement;
    }

    public static PreparedStatement preparedStatementAutoGen(String sql, List<Object> param, Connection conn,
                                                             String autoGenCol) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.prepareStatement(sql, new String[] {autoGenCol});
            preparedStatementSet(preparedStatement, param, true);

        } catch (SQLException e) {
            log.error("[Create preparedStatement] failed, begin to rooback , sql is " + sql, e);
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
            } catch (SQLException e1) {
                String errorMs = "[Create preparedStatement] failed and [Transaction roolback] failed, sql is: " + sql;
                log.error(errorMs, e1);
                Assert.fail(errorMs + " \n " + e1);
            }
            Assert.fail("[Create preparedStatement] failed, sql is :" + sql);
        }

        return preparedStatement;
    }

    /**
     * preparedStatement Set
     */
    private static PreparedStatement preparedStatementSet(PreparedStatement preparedStatement, List<Object> param) {
        return preparedStatementSet(preparedStatement, param, false);
    }

    /**
     * preparedStatement
     */
    public static PreparedStatement preparedStatement(String sql, Connection conn) {
        PreparedStatement preparedStatement = null;
        try {
            preparedStatement = conn.prepareStatement(sql);
        } catch (SQLException e) {
            log.error("[Create preparedStatement] failed, begin to rooback , sql is " + sql, e);
            try {
                if (!conn.getAutoCommit()) {
                    conn.rollback();
                }
            } catch (SQLException e1) {
                String errorMs = "[Create preparedStatement] failed and [Transaction roolback] failed, sql is: " + sql;
                log.error(errorMs, e1);
                Assert.fail(errorMs + " \n " + e);
            }
            Assert.fail("[Create preparedStatement] failed, sql is :" + sql);
        }

        return preparedStatement;
    }

    private static int[] preparedStatementExecuteBatch(PreparedStatement ps) throws SQLException {
        int[] effectCount = new int[10];
        effectCount = ps.executeBatch();
        return effectCount;
    }

    private static void preparedStatementAddBatch(PreparedStatement ps) {
        try {
            ps.addBatch();
        } catch (SQLException e) {
            String errorMs = "[preparedStatement add batch] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
    }

    public static List<String> getColumnNameList(ResultSet rs) {
        ResultSetMetaData metaData = getMetaData(rs);
        int columnCount = getColumnCount(metaData);
        List<String> cloumnNameList = new ArrayList<String>();

        for (int i = 1; i < columnCount + 1; i++) {
            String cloumnName = null;
            cloumnName = getColumnLabel(metaData, i);
            cloumnNameList.add(cloumnName.toLowerCase());
        }

        return cloumnNameList;
    }

    public static List<String> getColumnNameListToLowerCase(ResultSet rs) {
        ResultSetMetaData metaData = getMetaData(rs);
        int columnCount = getColumnCount(metaData);
        List<String> cloumnNameList = new ArrayList<String>();

        for (int i = 1; i < columnCount + 1; i++) {
            String cloumnName = null;
            cloumnName = getColumnLabel(metaData, i);
            cloumnNameList.add(cloumnName.toLowerCase());
        }

        return cloumnNameList;
    }

    public static String getColumnLabel(ResultSetMetaData metaData, int i) {
        String cloumnName = null;
        try {
            cloumnName = metaData.getColumnLabel(i);
        } catch (SQLException e) {
            String errorMs = "[metaData getColumnLabel] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
        return cloumnName;
    }

    public static List<String> getListByColumnName(ResultSet resultSet, String columnName) throws SQLException {
        List<String> list = new ArrayList<>();
        while (resultSet.next()) {
            list.add(resultSet.getString(columnName));
        }
        return list;
    }

    public static String getColumnType(ResultSetMetaData metaData, int i) {
        String cloumnType = null;
        try {
            cloumnType = metaData.getColumnTypeName(i);
        } catch (SQLException e) {
            String errorMs = "[metaData getColumnType] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
        return cloumnType;

    }

    public static int getColumnCount(ResultSetMetaData metaData) {
        int columnLength = 0;
        try {
            columnLength = metaData.getColumnCount();
        } catch (SQLException e) {
            String errorMs = "[metaData getColumnCount] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }
        return columnLength;
    }

    public static ResultSetMetaData getMetaData(ResultSet rs) {
        ResultSetMetaData metaData = null;
        if (rs != null) {
            try {
                metaData = rs.getMetaData();
            } catch (SQLException e) {
                String errorMs = "[getMetaData] failed";
                log.error(errorMs, e);
                Assert.fail(errorMs + " \n " + e);
            }
        }
        return metaData;
    }

    public static List<List<Object>> getAllResult(ResultSet rs) {
        return getAllResult(rs, false, null, true, isStrictType());
    }

    public static List<List<Object>> getAllResult(ResultSet rs, boolean ignoreException) {
        return getAllResult(rs, ignoreException, null, true, isStrictType());
    }

    public static List<String> getAllTypeName(ResultSet rs, boolean ignoreException) {
        List<String> typeNames = new ArrayList<>();
        ResultSetMetaData resultSetMetaData;
        try {
            resultSetMetaData = rs.getMetaData();
            int colCount = resultSetMetaData.getColumnCount();
            for (int i = 0; i < colCount; i++) {
                typeNames.add(resultSetMetaData.getColumnTypeName(i + 1));
            }
        } catch (SQLException e) {
        }
        return typeNames;
    }

    public static List<List<String>> getStringResult(ResultSet rs, boolean ignoreException) {
        return getAllStringResult(rs, ignoreException, null);
    }

    public static List<List<Object>> getAllResultIgnoreColumn(ResultSet rs, List<Integer> ignoreColumn) {
        return getAllResult(rs, false, ignoreColumn, true, false);
    }

    public static List<List<Object>> getAllResult(ResultSet rs, boolean ignoreException, List<Integer> ignoreColumn,
                                                  boolean assertDateTypeMatch, boolean strictType) {
        List<List<Object>> allResults = new ArrayList<List<Object>>();
        ResultSetMetaData metaData = getMetaData(rs);
        int columnCount = getColumnCount(metaData);
        try {
            while (rs.next()) {
                List<Object> oneResult = new ArrayList<Object>();
                for (int i = 1; i < columnCount + 1; i++) {
                    try {
                        if (ignoreColumn == null || ignoreColumn.size() == 0 || !ignoreColumn.contains(i)) {
                            if (metaData.getColumnTypeName(i).equalsIgnoreCase("tinyint")) {
                                if (rs.getObject(i) == null) {
                                    oneResult.add(null);
                                } else {
                                    oneResult.add(dataConversion(rs.getInt(i), assertDateTypeMatch, strictType));
                                }
                            } else {
                                oneResult.add(getObject(rs, i, assertDateTypeMatch, strictType));
                            }
                        } else {
                            log.info("Ignore Column :" + metaData.getColumnName(i));
                        }

                        // }
                    } catch (Exception ex) {
                        if (ignoreException) {
                            oneResult.add("null");
                        } else {
                            oneResult.add(ex.getMessage());
                        }
                    }
                }
                allResults.add(oneResult);
            }
        } catch (SQLException e) {
            String errorMs = "[ResultSet next] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }

        return allResults;
    }

    public static List<List<String>> getAllStringResultWithColumnNames(ResultSet rs, boolean ignoreException,
                                                                       List<Integer> ignoreColumn) {
        List<List<String>> allResults = new ArrayList<>();
        ResultSetMetaData metaData = getMetaData(rs);
        int columnCount = getColumnCount(metaData);
        try {
            List<String> columnName = new ArrayList<>();
            for (int i = 1; i < columnCount + 1; i++) {
                if (ignoreColumn == null || ignoreColumn.size() == 0 ||
                    !ignoreColumn.contains(i)) {
                    columnName.add(metaData.getColumnName(i));
                }
            }
            allResults.add(columnName);

            while (rs.next()) {
                List<String> oneResult = new ArrayList<>();
                for (int i = 1; i < columnCount + 1; i++) {

                    try {
                        if (ignoreColumn == null || ignoreColumn.size() == 0 || !ignoreColumn.contains(i)) {
                            oneResult.add(rs.getString(i));
                        }
                    } catch (Exception ex) {
                        if (ignoreException) {
                            oneResult.add("null");
                        } else {
                            oneResult.add(ex.getMessage());
                        }
                    }
                }
                allResults.add(oneResult);
            }
        } catch (SQLException e) {
            String errorMs = "[ResultSet next] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }

        return allResults;
    }

    public static List<List<String>> getAllStringResult(ResultSet rs, boolean ignoreException,
                                                        List<Integer> ignoreColumn) {
        List<List<String>> allResults = new ArrayList<>();
        ResultSetMetaData metaData = getMetaData(rs);
        int columnCount = getColumnCount(metaData);
        try {
            while (rs.next()) {
                List<String> oneResult = new ArrayList<>();
                for (int i = 1; i < columnCount + 1; i++) {

                    try {
                        if (ignoreColumn == null || ignoreColumn.size() == 0 || !ignoreColumn.contains(i)) {
                            oneResult.add(rs.getString(i));
                        }
                    } catch (Exception ex) {
                        if (ignoreException) {
                            oneResult.add("null");
                        } else {
                            oneResult.add(ex.getMessage());
                        }
                    }
                }
                allResults.add(oneResult);
            }
        } catch (SQLException e) {
            String errorMs = "[ResultSet next] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }

        return allResults;
    }

    public static List<List<Object>> getAllLowerCaseStringResult(ResultSet rs) {
        List<List<Object>> allResults = new ArrayList<List<Object>>();
        ResultSetMetaData metaData = getMetaData(rs);
        int columnCount = getColumnCount(metaData);
        try {
            while (rs.next()) {
                List<Object> oneResult = new ArrayList<Object>();
                for (int i = 1; i < columnCount + 1; i++) {
                    try {
                        if (metaData.getColumnTypeName(i).equalsIgnoreCase("tinyint")) {
                            oneResult.add(dataConversion(rs.getInt(i)));
                        } else if (metaData.getColumnTypeName(i).equalsIgnoreCase("VARCHAR")) {
                            oneResult.add(dataConversion(rs.getString(i)).toString().toLowerCase());
                        } else if (metaData.getColumnTypeName(i).equalsIgnoreCase("CHAR")) {
                            oneResult.add(dataConversion(rs.getString(i)).toString().toLowerCase());
                        } else {
                            oneResult.add(getObject(rs, i));
                        }
                        // }
                    } catch (Exception ex) {
                        oneResult.add(ex.getMessage());
                    }
                }
                allResults.add(oneResult);
            }
        } catch (SQLException e) {
            String errorMs = "[ResultSet next] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n " + e);
        }

        return allResults;
    }

    public static Object getObject(ResultSet rs, int i) throws SQLException {
        return getObject(rs, i, true);
    }

    public static Object getObject(ResultSet rs, int i, boolean assertDateTypeMatch) throws SQLException {
        Object data = null;
        try {
            data = rs.getObject(i);
        } catch (Exception e) {
            try {
                data = rs.getString(i);
            } catch (Exception e1) {
                data = rs.getBytes(i);
            }
        }
        data = dataConversion(data, assertDateTypeMatch, isStrictType());
        return data;
    }

    public static Object getObject(ResultSet rs, int i, boolean assertDateTypeMatch, boolean strictTypeMatch)
        throws SQLException {
        Object data = null;
        try {
            data = rs.getObject(i);
        } catch (Exception e) {
            try {
                data = rs.getString(i);
            } catch (Exception e1) {
                data = rs.getBytes(i);
            }
        }
        data = dataConversion(data, assertDateTypeMatch, strictTypeMatch);
        return data;
    }

    public static Object getObject(ResultSet rs, String columnName) throws SQLException {
        if (rs.next()) {
            Object data = rs.getObject(columnName);
            data = dataConversion(data);
            return data;
        }
        return null;
    }

    private static Object dataConversion(Object data) {
        return dataConversion(data, true);
    }

    private static Object dataConversion(Object data, boolean assertDateTypeMatch) {
        return dataConversion(data, assertDateTypeMatch, false);
    }

    private static Object dataConversion(Object data, boolean assertDateTypeMatch, boolean strictTypeMatch) {
        if (strictTypeMatch) {
            if (data instanceof Double) {
                data = new BigDecimal((Double) data);
            }
            return data;
        }
        if (data instanceof Long) {
            data = new BigDecimal((Long) data);
        } else if (data instanceof Short) {
            data = new BigDecimal((Short) data);
        } else if (data instanceof Integer) {
            data = new BigDecimal((Integer) data);
        } else if (data instanceof Float) {
            data = new BigDecimal((Float) data);
        } else if (data instanceof Double) {
            data = new BigDecimal((Double) data);
        } else if (data instanceof BigDecimal) {
            // data = data;
        } else if (data instanceof Timestamp) {
            data = new MyDate((Date) data, assertDateTypeMatch);
        } else if (data instanceof Date) {
            // data = ((Date) data).getTime() / 1000;
            data = new MyDate((Date) data, assertDateTypeMatch);
        } else if (data instanceof byte[]) {
            data = printBytes((byte[]) data);
        } else if (data instanceof BigInteger) {
            data = new BigDecimal((BigInteger) data);
        }

        if (data instanceof BigDecimal) {
            data = new MyNumber((BigDecimal) data);
        }
        return data;
    }

    public static List<List<Object>> loadDataFromPhysical(final Connection tddlConnection, String tableName,
                                                          Function<Pair<String, String>, String> sqlBuilder) {
        final List<Pair<String, String>> physicalTables = getTopology(tddlConnection, tableName);

        List<List<Object>> result = new ArrayList<>();
        physicalTables.forEach(physical -> {
            final ResultSet resultSet = executeQuery(sqlBuilder.apply(physical), tddlConnection);
            result.addAll(getAllResult(resultSet, false, null, false, false));
        });

        return result;
    }

    public static List<Pair<String, String>> getTopology(Connection tddlConnection, String tableName) {
        final List<Pair<String, String>> physicalTables = new ArrayList<>();
        try (ResultSet topology = executeQuery("SHOW TOPOLOGY FROM `" + tableName + "`", tddlConnection)) {
            while (topology.next()) {
                physicalTables.add(Pair.of(topology.getString(2), topology.getString(3)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot get topology for " + tableName, e);
        }
        return physicalTables;
    }

    public static List<Pair<String, String>> getTopologyWithHint(Connection tddlConnection, String tableName,
                                                                 String hint) {
        final List<Pair<String, String>> physicalTables = new ArrayList<>();
        try (ResultSet topology = executeQuery(hint + "SHOW TOPOLOGY FROM `" + tableName + "`", tddlConnection)) {
            while (topology.next()) {
                physicalTables.add(Pair.of(topology.getString(2), topology.getString(3)));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Cannot get topology for " + tableName, e);
        }
        return physicalTables;
    }

    /**
     * 获取三节点的LeaderIP
     */
    public static String getLeaderIP(Connection conn) {
        log.info("获取Leader IP");
        if (conn != null) {
            ResultSet resultSet =
                executeQuerySuccess(conn, "SELECT * FROM information_schema.ALISQL_CLUSTER_GLOBAL");
            try {
                while (resultSet.next()) {
                    if (resultSet.getString("ROLE").equalsIgnoreCase("Leader")) {
                        return resultSet.getString("IP_PORT");
                    }

                }
            } catch (SQLException e) {
                log.error(e.getMessage(), e);

            } finally {
                close(resultSet);
            }

        }
        log.error("获取不到Leader IP，请检查环境是否正常");
        return null;
    }

    public static void setShareReadView(boolean shareReadView, Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(String.format("set share_read_view = %s", shareReadView ? "ON" : "OFF"));
        }
    }

    public static boolean supportShareReadView(Connection conn) throws SQLException {
        if (isShareReadView(conn)) {
            return true;
        }
        try {
            conn.setAutoCommit(false);
            // 尝试开启来确认DN是否支持共享ReadView
            setShareReadView(true, conn);
            return true;
        } catch (SQLException e) {
            return false;
        } finally {
            conn.commit();
            conn.setAutoCommit(true);
        }
    }

    public static boolean isShareReadView(Connection conn) throws SQLException {
        final ResultSet rs = executeQuerySuccess(conn, "show variables like 'share_read_view'");
        Assert.assertTrue(rs.next());
        boolean res = rs.getBoolean(2);
        Assert.assertFalse(rs.next());
        return res;
    }

    public static boolean supportXA(Connection tddlConnection) throws SQLException {
        final ResultSet rs = executeQuerySuccess(tddlConnection, "SELECT @@version");
        rs.next();
        String version = rs.getString(1);

        return !version.startsWith("5.6") && !version.startsWith("5.5");
    }

    public static void assertRouteCorrectness(String hint, String tableName, List<List<Object>> selectedData,
                                              List<String> columnNames, List<String> shardingKeys,
                                              Connection connection) throws SQLException {
        List<Integer> shardingColumnIndexes = shardingKeys.stream()
            .map(columnNames::indexOf)
            .collect(Collectors.toList());
        List<List<Object>> shardingValues = new ArrayList<>(selectedData.size());
        for (List<Object> row : selectedData) {
            List<Object> shardingValuesInRow = new ArrayList<>(shardingColumnIndexes.size());
            for (int index : shardingColumnIndexes) {
                Object value = row.get(index);
                if (value instanceof MyDate) {
                    value = ((MyDate) value).getDate();
                } else if (value instanceof MyNumber) {
                    value = ((MyNumber) value).getNumber();
                }
                shardingValuesInRow.add(value);
            }
            shardingValues.add(shardingValuesInRow);
        }

        for (List<Object> row : shardingValues) {
            StringBuilder sql = new StringBuilder(
                String.format(Optional.ofNullable(hint).orElse("") + "select * from %s where ", tableName));
            final List<Object> values = new ArrayList<>();
            for (int i = 0; i < shardingKeys.size(); i++) {
                String shardingKeyName = shardingKeys.get(i);
                final Object value = row.get(i);
                if (null == value) {
                    sql.append(shardingKeyName).append(" is null");
                } else {
                    sql.append(shardingKeyName).append("=?");
                    values.add(value);
                }
                if (i != shardingKeys.size() - 1) {
                    sql.append(" and ");
                }
            }
            PreparedStatement tddlPs = preparedStatementSet(sql.toString(), values, connection);
            ResultSet tddlRs = executeQuery(sql.toString(), tddlPs);
            Assert.assertTrue(tddlRs.next());
        }
    }

    /**
     * 关闭连接
     */
    public static void closeConnection(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
            } catch (SQLException e) {
                log.error(e.getMessage(), e);
            }
        }
    }

    /**
     * 本应该放到最基础类里的方法,但由于框架要整体更换,写在这区分一下
     */
    public static void executeUpdateSuccess(Connection conn, String sql) {
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
//            log.info("Executing update: " + sql);
            stmt.execute(sql);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            assertWithMessage("语句并未按照预期执行成功:" + sql + e.getMessage()).fail();
        } finally {
            close(stmt);
        }

    }

    /**
     * 执行 update, 忽略指定异常
     *
     * @param errIgnored 忽略包含此信息的异常
     * @return 是否有异常被忽略
     */
    public static boolean executeUpdateSuccessIgnoreErr(Connection conn, String sql, Set<String> errIgnored) {
        boolean result = false;

        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            stmt.execute(sql);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);

            for (String err : errIgnored) {
                if (e.getMessage().contains(err)) {
                    result = true;
                    break;
                }
            }

            assertWithMessage(sql + "\n语句并未按照预期执行成功: " + e.getMessage()).that(result).isTrue();
        } finally {
            close(stmt);
        }

        return result;
    }

    public static void executeUpdateSuccessWithWarning(Connection conn, String sql, String expectedWarning) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            assertWithMessage("语句并未按照预期执行成功:" + e.getMessage()).fail();
        }
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("show warnings")) {
            String warning = "";
            if (rs.next()) {
                warning = rs.getString("Message");
            }
            Assert.assertTrue(warning.toLowerCase().contains(expectedWarning.toLowerCase()));
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            assertWithMessage("语句并未按照预期执行成功:" + e.getMessage()).fail();
        }
    }

    public static void executeUpdate(Connection conn, String sql) {
        executeUpdate(conn, sql, false, false);
    }

    public static void executeUpdate(Connection conn, String sql, boolean liteLog, boolean ignoreError) {
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            stmt.execute(sql);
        } catch (SQLException e) {
            if (liteLog) {
                log.error(e.getMessage());
            } else {
                log.error(e.getMessage(), e);
            }
            if (!ignoreError) {
                Assert.fail(e.getMessage());
            }
        } finally {
            close(stmt);
        }

    }

    public static int executeUpdateAndGetEffectCount(Connection conn, String sql) {
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            return stmt.executeUpdate(sql);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        } finally {
            close(stmt);
        }

        return -1;

    }

    /**
     * 本应该放到最基础类里的方法,但由于框架要整体更换,写在这区分一下
     */
    public static void executeUpdateFailed(Connection conn, String sql, String errMessage, String errMessage2) {
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            stmt.execute(sql);
            assertWithMessage("语句并未按照预期执行失败,sql is :" + sql).fail();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            final ImmutableSet.Builder<String> builder = ImmutableSet.<String>builder().add(errMessage.toLowerCase());
            if (StringUtils.isNotBlank(errMessage2)) {
                builder.add(errMessage2.toLowerCase());
            }
            final ImmutableSet<String> errMsgSet = builder.build();
            Assert.assertTrue(e.getMessage(), errMsgSet.stream().anyMatch(e.getMessage().toLowerCase()::contains));
        } finally {
            close(stmt);
        }
    }

    public static void executeUpdateFailed(Connection conn, String sql, String errMessage) {
        executeUpdateFailed(conn, sql, errMessage, null);
    }

    /**
     * 执行错误，返回错误信息
     */
    public static String executeUpdateFailedReturn(Connection conn, String sql) {
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            stmt.execute(sql);
            assertWithMessage("语句并未按照预期执行失败,sql is :" + sql).fail();
        } catch (Exception e) {
            return e.getMessage();
        } finally {
            close(stmt);
        }
        return "";
    }

    /**
     *
     */
    public static void havePrivToExecute(Connection conn, String sql) {
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            stmt.execute(sql);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            if (e.getMessage().contains("Doesn't have")) {
                assertWithMessage(String.format("没有权限执行语句%s", sql)).fail();
            }
            if (e.getMessage().contains("You have an error in your SQL syntax")) {
                assertWithMessage(String.format("语法错误%s", sql)).fail();
            }
        } finally {
            close(stmt);
        }

    }

    /**
     * 查询语句执行成功
     */
    public static ResultSet executeQuerySuccess(Connection conn, String sql) {
        Statement stmt = null;
        ResultSet resultSet = null;
        try {
            stmt = conn.createStatement();
            resultSet = stmt.executeQuery(sql);
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            assertWithMessage("语句并未按照预期执行成功:" + e.getMessage()).fail();
        }
        return resultSet;
    }

    /**
     * 查询语句执行失败
     */
    public static void executeQueryFaied(Connection conn, String sql, String[] errorMsg) {
        Statement stmt = null;
        ResultSet resultSet = null;
        try {
            stmt = conn.createStatement();
            resultSet = stmt.executeQuery(sql);
            assertWithMessage("语句并未按照预期执行失败").fail();
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
            String message = e.getMessage().toLowerCase();
            boolean contains = false;
            for (String s : errorMsg) {
                if (StringUtils.isNotBlank(s)) {
                    contains = message.contains(s.toLowerCase());
                    if (contains) {
                        break;
                    }
                }
            }
            Assert.assertTrue(contains);
        } finally {
            close(resultSet);
            close(stmt);
        }
    }

    public static void executeQueryFaied(Connection conn, String sql, String errorMsg) {
        executeQueryFaied(conn, sql, new String[] {errorMsg});
    }

    public static DruidDataSource getDruidDataSource(String url, String user, String password) {
        DruidDataSource druidDs = new DruidDataSource();
        druidDs.setUrl(url);
        druidDs.setUsername(user);
        druidDs.setPassword(password);
        druidDs.setRemoveAbandoned(false);
        druidDs.setMaxActive(30);
        // //druidDs.setRemoveAbandonedTimeout(18000);
        try {
            druidDs.init();
            druidDs.getConnection();
        } catch (SQLException e) {
            String errorMs = "[DruidDataSource getConnection] failed";
            log.error(errorMs, e);
            Assert.fail(errorMs);
        }
        return druidDs;
    }

    /**
     * 返回结果集的条数
     */
    public static int resultsSize(ResultSet rs) {
        int row = 0;
        try {
            while (rs.next()) {
                row++;
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        return row;

    }

    /**
     * 选择出自增列
     */
    public static List<Long> selectIds(String sql, String columnName, Connection conn) {
        ResultSet rs = null;
        try {
            rs = executeQuerySuccess(conn, sql);
            List<Long> resultId = new ArrayList<Long>();
            while (rs.next()) {
                resultId.add(rs.getLong(columnName));
            }
            return resultId;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        } finally {
            close(rs);
        }

        return null;

    }

    /**
     * 判断select出的Id是否一致
     */
    public static List<Double> selectDoubleIds(String sql, String columnName, Connection conn) throws Exception {
        ResultSet rs = null;
        try {
            rs = executeQuerySuccess(conn, sql);
            List<Double> resultId = new ArrayList<Double>();
            while (rs.next()) {
                resultId.add(rs.getDouble(columnName));
            }

            return resultId;
        } finally {
            if (rs != null) {
                close(rs);
            }

        }

    }

    public static String resultsStr(ResultSet rs) {
        StringBuilder sb = new StringBuilder();
        try {
            while (rs.next()) {
                sb.append(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        return sb.toString();

    }

    public static Long resultLong(ResultSet rs) {
        Long ret = null;
        try {
            while (rs.next()) {
                ret = (Long) rs.getObject(1);
            }
        } catch (SQLException e) {
            log.error(e.getMessage(), e);
        }
        return ret;
    }

    public static String showFullCreateTable(Connection conn, String tbName) throws Exception {
        String sql = "show full create table `" + tbName + "`";

        ResultSet rs = executeQuerySuccess(conn, sql);
        try {
            assertThat(rs.next()).isTrue();
            return rs.getString("Create Table");
        } finally {
            close(rs);
        }
    }

    public static String showCreateTable(Connection conn, String tbName) throws Exception {
        String sql = "show create table `" + tbName + "`";

        ResultSet rs = executeQuerySuccess(conn, sql);
        try {
            assertThat(rs.next()).isTrue();
            return rs.getString(2);
        } finally {
            close(rs);
        }
    }

    public static List<String> showTables(Connection conn) throws Exception {
        List<String> tables = new ArrayList<>();
        ResultSet resultSet = executeQuery("show tables", conn);
        try {
            while (resultSet.next()) {
                String table = resultSet.getString(1);
                tables.add(table);
            }
        } finally {
            close(resultSet);
        }

        return tables;
    }

    public static String selectVersion(Connection conn) {
        ResultSet rs = executeQuerySuccess(conn, "select version()");
        try {
            while (rs.next()) {
                return rs.getString(1);
            }
        } catch (Exception ex) {
            Assert.fail(ex.getMessage());
        } finally {
            close(rs);
        }
        return null;
    }

    public static void useDb(Connection connection, String db) {
        executeQuery("use `" + db + "`", connection);
    }

    public static String createLikeTable(
        String tableName, String polardbxOneDB, String mysqlOneDB, String suffixName, boolean bMysql)
        throws SQLException {
        String originTableName = tableName;
        String tempTableName = originTableName + suffixName;

        try (Connection tddlConnection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            useDb(tddlConnection, polardbxOneDB);
            executeUpdate(tddlConnection, String.format(DROP_TABLE_SQL, tempTableName), false, false);
            executeUpdate(
                tddlConnection, String.format(CREATE_TABLE_LIKE_SQL, tempTableName, originTableName), false, false);
        }

        try (Connection mysqlConnection = ConnectionManager.getInstance().getDruidDataNodeConnection()) {
            if (bMysql) {
                useDb(mysqlConnection, mysqlOneDB);
                executeUpdate(mysqlConnection, String.format(DROP_TABLE_SQL, tempTableName), false, false);
                executeUpdate(
                    mysqlConnection, String.format(CREATE_TABLE_LIKE_SQL, tempTableName, originTableName), false,
                    false);
            }
        }
        return tempTableName;
    }

    public static void dropTable(
        String tableName, String polardbxOneDB, String mysqlOneDB, String suffixName, boolean bMysql)
        throws SQLException {
        String originTableName = tableName;
        String tempTableName = originTableName + suffixName;

        try (Connection tddlConnection = ConnectionManager.getInstance().getDruidPolardbxConnection()) {
            useDb(tddlConnection, polardbxOneDB);
            executeUpdate(tddlConnection, String.format(DROP_TABLE_SQL, tempTableName), false, false);
        }

        try (Connection mysqlConnection = ConnectionManager.getInstance().getDruidDataNodeConnection()) {
            if (bMysql) {
                useDb(mysqlConnection, mysqlOneDB);
                executeUpdate(mysqlConnection, String.format(DROP_TABLE_SQL, tempTableName), false, false);
            }
        }
    }

    public static void createPartDatabase(Connection polarxConn, String logDb) {
        String createDbSql =
            String.format("/*+TDDL:AUTO_PARTITION_PARTITIONS=3*/create database if not exists %s mode='auto';", logDb);
        executeUpdate(polarxConn, createDbSql);
        useDb(polarxConn, logDb);
    }

    public static void createDatabase(Connection polarxConn, String logDb, String hint) {
        String createDbSql = String.format("%s create database if not exists %s ;", hint, logDb);
        executeUpdate(polarxConn, createDbSql);
        useDb(polarxConn, logDb);
    }

    public static void dropDatabase(Connection polarxConn, String logDb) {
        String dropDbSql = "drop database if exists " + logDb;
        executeUpdate(polarxConn, dropDbSql);
    }

    public static void dropTable(Connection polarxConn, String table) {
        String dropTableSql = "drop table if exists " + table;
        executeUpdate(polarxConn, dropTableSql);
    }

    public static void analyzeTable(Connection tddlConnection, String tableName) {
        try (Statement stmt = tddlConnection.createStatement()) {
            stmt.executeUpdate("analyze table " + tableName);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    public static int getDriverMajorVersion(Connection tddlConnection) {
        try {
            String driverVersion = tddlConnection.getMetaData().getDriverVersion();
            Pattern compile = Pattern.compile("\\d\\.\\d\\.\\d+");
            Matcher matcher = compile.matcher(driverVersion);
            if (!matcher.find()) {
                Assert.fail("unrecognized driver version: " + driverVersion);
            }
            String version = matcher.group();
            return Integer.parseInt(version.substring(0, 1));
        } catch (Exception e) {
            log.error("Failed to get driver version");
            Assert.fail(e.getMessage());
        }
        return -1;
    }

    public static void createUser(Connection tddlConnection, String username, String host, String password) {
        String sql = String.format("create user %s@'%s' identified by '%s'", username, host, password);
        executeUpdateSuccess(tddlConnection, sql);
    }

    public static void grantAllPrivOnDb(Connection tddlConnection, String username, String host, String db) {
        String sql = String.format("grant all on %s.* to '%s'@'%s';", db, username, host);
        executeUpdateSuccess(tddlConnection, sql);
    }

    public static void dropUser(Connection tddlConnection, String username, String host) {
        String sql = String.format("drop user if exists %s@'%s'", username, host);
        executeUpdateSuccess(tddlConnection, sql);
    }

    public static Connection buildJdbcConnection(String host, int port, String dbName, String user, String passwdEnc,
                                                 String connProps) {
        String passwd = PasswdUtil.decrypt(passwdEnc);
        String url = createUrl(host, port, dbName, connProps);
        Connection conn = createConnection(url, user, passwd);
        return conn;
    }

    public static String createUrl(String host, Integer port, String dbName, String props) {
        String url = String.format(URL_PATTERN, host, port, dbName, props);
        return url;
    }

    public static Connection createConnection(String url, String username, String password) {
        try {
            Class.forName("com.mysql.jdbc.Driver");
            return DriverManager.getConnection(url, username, password);
        } catch (Throwable ex) {
            throw new PolardbxException(String.format("Failed to create connection to [%s]", url), ex);
        }
    }

    public static Connection createConnection(String host, int port, String db, String props, String username,
                                              String password) {
        String url = String.format(URL_PATTERN, host, port, db, props);
        try {
            Class.forName("com.mysql.jdbc.Driver");
            return DriverManager.getConnection(url, username, password);
        } catch (Throwable ex) {
            throw new PolardbxException(
                String.format("Failed to create connection to [%s], err is %s", url, ex.getMessage()), ex);
        }
    }

    /**
     * 替换str中的所有单个`为``
     * 为了防止库名或者表名或者列名是被``包裹的，通过format(如DESC `%s`.`%s`)后，库名或者列名或者表名会
     * 变成被两对``包裹，从而出现SQL出错
     */
    public static String escape(String str) {
        String regex = "(?<!`)`(?!`)";
        return str.replaceAll(regex, "``");
    }

    /**
     * 获得表中所有列的列名
     */
    public static List<Pair<String, String>> getColumnNamesByDesc(Connection conn, String dbName, String tbName)
        throws SQLException {
        List<Pair<String, String>> columns = new ArrayList<>();
        String sql = String.format("DESC `%s`.`%s`", escape(dbName), escape(tbName));
        try (ResultSet rs = executeQuerySuccess(conn, sql)) {
            while (rs.next()) {
                String column = rs.getString(1);
                String columnType = rs.getString(2);
                Pair<String, String> pair = new ImmutablePair<>(column, columnType);
                columns.add(pair);
            }
        }
        return columns;
    }

    /**
     * 获得表中各列的列名到类型的映射
     */
    public static Map<String, ColumnType> getColumnTypesByDesc(Connection conn, String dbName, String tbName)
        throws SQLException {
        Map<String, ColumnType> name2Type = new HashMap<>();
        String sql = String.format("DESC `%s`.`%s`", escape(dbName), escape(tbName));
        try (ResultSet rs = executeQuerySuccess(conn, sql)) {
            while (rs.next()) {
                ColumnType columnType = new ColumnType();
                String columnName = rs.getString(1);
                String type = rs.getString(2);
                String canNull = rs.getString(3);
                String defaultValue = rs.getString(5);
                columnType.setType(type);
                columnType.setDefaultValue(defaultValue);
                columnType.setCanNull(StringUtils.equalsAnyIgnoreCase(canNull, "YES"));

                name2Type.put(columnName, columnType);
            }
        }
        return name2Type;
    }

    public static Map<String, String> getColumnCharsetMap(Connection conn, String db, String tb) throws SQLException {
        Map<String, String> charsetMap = new HashMap<>();
        String sql = String.format("SELECT column_name, character_set_name FROM `INFORMATION_SCHEMA`.`COLUMNS`" +
            "WHERE table_schema='%s' AND table_name='%s'", db, tb);
        try (ResultSet rs = executeQuerySuccess(conn, sql)) {
            while (rs.next()) {
                String column = rs.getString(1);
                String type = rs.getString(2);
                //https://dev.mysql.com/doc/refman/8.0/en/charset-unicode-utf8mb3.html
                if ("utf8mb3".equalsIgnoreCase(type)) {
                    type = "utf8";
                }
                charsetMap.put(column, type);
            }
        }
        return charsetMap;
    }

    public static String getTableCharset(Connection conn, String db, String table) throws SQLException {
        String sql = String.format("SELECT TABLE_COLLATION " +
            "FROM `INFORMATION_SCHEMA`.`TABLES` " +
            "WHERE table_schema = '%s' " +
            "AND table_name = '%s'", db, table);
        String charset = null;
        try (ResultSet rs = executeQuerySuccess(conn, sql)) {
            while (rs.next()) {
                String tableCollation = rs.getString(1);
                int pos = tableCollation.indexOf('_');
                charset = tableCollation.substring(0, pos);
            }
        }
        return charset;
    }

    public static String getDatabaseCharset(Connection conn, String db) throws SQLException {
        String sql = String.format("SELECT default_character_set_name FROM `INFORMATION_SCHEMA`.`SCHEMATA` " +
            "WHERE schema_name = '%s'", db);
        String charset = null;
        try (ResultSet rs = executeQuerySuccess(conn, sql)) {
            while (rs.next()) {
                charset = rs.getString(1);
            }
        }
        return charset;
    }

    /**
     * 获得表的主键名
     * 如果是联合主键，主键的顺序按照它们表中的左右顺序有序
     */
    public static List<String> getPrimaryKeyNames(Connection conn, String dbName, String tbName) throws SQLException {
        Set<String> keys = new HashSet<>();
        String sql = String.format("SELECT column_name FROM `INFORMATION_SCHEMA`.`KEY_COLUMN_USAGE`" +
            "WHERE table_schema='%s' AND table_name='%s' AND constraint_name='PRIMARY'", dbName, tbName);
        try (Statement stmt = conn.createStatement();
            ResultSet rs = executeQuery(sql, stmt)) {
            while (rs.next()) {
                String name = rs.getString(1);
                keys.add(name);
            }
        }
        List<String> res = new ArrayList<>();
        List<Pair<String, String>> columns = getColumnNamesByDesc(conn, dbName, tbName);
        for (Pair<String, String> p : columns) {
            String c = p.getLeft();
            if (keys.contains(c)) {
                res.add(c);
            }
        }
        return res;
    }

    public static List<String> showTables(Connection conn, String database) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (ResultSet rs = executeQuery(
            "select TABLE_NAME from information_schema.TABLES where TABLE_SCHEMA = '" + database
                + "' and TABLE_TYPE='BASE TABLE'",
            conn)) {
            while (rs.next()) {
                String table = rs.getString(1);
                tables.add(table.toLowerCase());
            }
        }
        return tables;
    }

    public static List<String> showDatabases(Connection conn) throws SQLException {
        List<String> databases = new ArrayList<>();
        try (ResultSet rs = executeQuery("show databases", conn)) {
            while (rs.next()) {
                String database = rs.getString(1);
                databases.add(database);
            }
        }
        return databases;
    }

    public static class MyNumber {

        BigDecimal number;

        public MyNumber(BigDecimal number) {
            super();
            this.number = number;
        }

        public BigDecimal getNumber() {
            return number;
        }

        public String toString() {
            return this.number == null ? null : this.number.toString();
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                String errorMS = "[类型不一致]" + this.getClass() + "  " + obj.getClass();
                log.error(errorMS);
                Assert.fail(errorMS);
            }
            MyNumber other = (MyNumber) obj;
            if (number == null) {
                return other.number == null;
            } else {
                BigDecimal o = this.number;
                BigDecimal o2 = other.number;

                return o.subtract(o2).abs().compareTo(new BigDecimal(0.0001)) < 0;
            }
        }
    }

    // 时间类型的比较不需要特别准确，
    public static class MyDate {

        public final boolean assertTypeMatch;
        public Date date;

        public MyDate(Date date) {
            this(date, true);
        }

        public MyDate(Date date, boolean assertTypeMatch) {
            super();
            this.date = date;
            this.assertTypeMatch = assertTypeMatch;
        }

        public Date getDate() {
            return date;
        }

        public String toString() {
            return this.date == null ? null : String.valueOf(this.date.getTime());
        }

        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                String errorMS = "[类型不一致]" + this.getClass() + "  " + obj.getClass();
                log.error(errorMS);
                if (assertTypeMatch) {
                    Assert.fail(errorMS);
                } else {
                    return false;
                }
            }
            MyDate other = (MyDate) obj;
            if (date == null) {
                if (other.date != null) {
                    return false;
                }
            } else {
                Date o = this.date;
                Date o2 = other.date;

                return ((o.getTime() - o2.getTime()) / 1000 >= -10) && ((o.getTime() - o2.getTime()) / 1000 <= 10);
            }
            return false;
        }
    }

}
