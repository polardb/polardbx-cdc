/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.rpl.common;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.druid.pool.vendor.MySqlExceptionSorter;
import com.alibaba.druid.pool.vendor.MySqlValidConnectionChecker;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.support.JdbcUtils;

/**
 * @author shicai.xsc 2020/12/1 22:36
 * @since 5.0.0.0
 */
@Slf4j
public class DataSourceUtil {

    /**
     * in seconds. default 2 hours
     */
    private static int QUERY_TIMEOUT = 7200;
    public static final List<String> CONSTRAINT_TYPE_LIST = new ArrayList<>();
    public static Map<String, String> DEFAULT_MYSQL_CONNECTION_PROPERTIES = Maps.newHashMap();

    static {
        CONSTRAINT_TYPE_LIST.add("PRIMARY KEY");
        CONSTRAINT_TYPE_LIST.add("UNIQUE");
        // CONSTRAINT_TYPE_LIST.add("__#alibaba_rds_row_id#__");

        // 开启多语句能力
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("allowMultiQueries", "true");
        // 全量目标数据源加上这个批量的参数
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("rewriteBatchedStatements", "true");
        // 关闭每次读取read-only状态,提升batch性能
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("readOnlyPropagatesToServer", "false");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("connectTimeout", "1000");
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("autoReconnect", "true");
        // 将0000-00-00的时间类型返回null
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("zeroDateTimeBehavior", "convertToNull");
        // 直接返回字符串，不做year转换date处理
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("yearIsDateType", "false");
        // 返回时间类型的字符串,不做时区处理
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("noDatetimeStringSync", "true");
        // 不处理tinyint转为bit
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("tinyInt1isBit", "false");
        // 16MB，兼容一下ADS不支持mysql，5.1.38+的server变量查询为大写的问题，人肉指定一下最大包大小
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("maxAllowedPacket", "1073741824");
        // net_write_timeout
        DEFAULT_MYSQL_CONNECTION_PROPERTIES.put("netTimeoutForStreamingResults", "72000");
    }

    @FunctionalInterface
    public static interface ResultSetMapper<T> {
        T process(ResultSet rs) throws SQLException;
    }

//    public static String getCharset(String ip, int port, String dbName, String user, String passwd) throws Exception {
//        DruidDataSource dataSource = null;
//        PreparedStatement stmt = null;
//        Connection conn = null;
//        ResultSet rs = null;
//        String sql = "show variables like 'character_set_database'";
//
//        try {
//            dataSource = createDruidMySqlDataSource(ip, port, dbName, user, passwd, "", 1, 1, null, null);
//            conn = dataSource.getConnection();
//            stmt = conn.prepareStatement(sql);
//            rs = stmt.executeQuery(sql);
//            if (rs.next()) {
//                return rs.getString("Value");
//            }
//            return "";
//        } catch (SQLException e) {
//            log.error("failed in getCharset: {}", sql, e);
//            throw e;
//        } finally {
//            DataSourceUtil.closeQuery(rs, stmt, conn);
//            dataSource.close();
//        }
//    }

    /**
     * Get key columns from db. It does NOT close data source.
     */
    public static Map<String, List<String>> getKeyColumnsInDB(List<String> tables, DruidDataSource dataSource) throws Exception {
        PreparedStatement stmt = null;
        Connection conn = null;
        ResultSet rs = null;
        Map<String, List<String>> ret;
        try {
            if (null == tables || tables.isEmpty()) {
                tables = getTables(dataSource);
            }
            ret = new HashMap<>();
            for (String table : tables) {
                List<String> keyColumns = getKeyColumnsInTable(dataSource, table);
                ret.put(table, keyColumns);
            }
        } catch (Exception e) {
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return ret;
    }

    /**
     * Check if table is empty
     */
    public static boolean isEmpty(DruidDataSource ds, String tableName) throws Exception {
        int cnt = getTableSize(ds, tableName);
        if (cnt > 0) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Get table size
     */
    public static int getTableSize(DruidDataSource ds, String tableName) throws Exception {
        Connection conn = ds.getConnection();
        String sql = String.format("SELECT COUNT(1) FROM `%s`.`%s`", conn.getCatalog(), tableName);
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = null;
        int count = 0;
        try {
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                count = rs.getInt(1);
            }
        } catch (SQLException e) {
            log.error("Exception in finding table size", sql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return count;
    }

    /**
     * Get key column out from one db table
     * NOTE: This method DOESN'T close data source
     * @param dataSource
     * @param tableName
     * @return
     * @throws SQLException
     */
    public static List<String> getKeyColumnsInTable(DruidDataSource dataSource, String tableName) throws Exception {
        Connection conn = dataSource.getConnection();
        PreparedStatement stmt = null;
        ResultSet rs = null;

        /**
         * This SQL should return result looks like this
         * +-----------------+-------------+
         * | CONSTRAINT TYPE | COLUMN_NAME |
         * +-----------------+-------------+
         * | PRIMARY KEY     | id          |
         * +-----------------+-------------+
         * | UNIQUE          | name        |
         * +-----------------+-------------+
         * | FOREIGN KEY     | order_id    |
         * +-----------------+-------------+
         */
        String sql = String.format("SELECT t.CONSTRAINT_TYPE, k.COLUMN_NAME\n"
            + "FROM information_schema.table_constraints t\n"
            + "         LEFT JOIN information_schema.key_column_usage k\n"
            + "                   USING(constraint_name,table_schema,table_name)\n"
            + "WHERE t.table_schema='%s'\n"
            + "  AND t.table_name='%s'", conn.getCatalog(), tableName);
        log.debug("Try retrieving keys from table. SQL: {}", sql);
        List<String> keyTypeList = new ArrayList<>();
        List<String> keyList = new ArrayList<>();
        try {
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery();
            while (rs.next()) {
                keyTypeList.add(rs.getString(1));
                keyList.add(rs.getString(2));
            }
        } catch (SQLException e) {
            log.error("failed in getTables: {}", sql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }

        List<String> keyColumns = new ArrayList<>();

        for (int i = 0; i < keyList.size(); i++) {
            String type = keyTypeList.get(i);
            String key = keyList.get(i);
            if (CONSTRAINT_TYPE_LIST.contains(type)) {
                keyColumns.add(key);
            }
        }

        return keyColumns;
    }

    /**
     * Get min value of column from a table. It does NOT close data source
     */
    public static String getMin(DruidDataSource dataSource, String tableName, String columnName) throws Exception {
        Connection conn = dataSource.getConnection();
        String sql = String.format("SELECT MIN(`%s`) FROM `%s`.`%s`", columnName, conn.getCatalog(), tableName);
        PreparedStatement stmt = conn.prepareStatement(sql);
        ResultSet rs = null;
        String minVal = null;
        try {
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                minVal = rs.getString(1);
            }
        } catch (SQLException e) {
            log.error("Exception in finding min value", sql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return minVal;
    }

    /**
     * Get max value of column from a table. It does NOT close data source
     */
    public static String getMax(DruidDataSource dataSource, String tableName, String columnName) throws Exception {
        String sql = null;
        String maxVal;
        try (Connection conn = dataSource.getConnection()) {
            sql = String.format("SELECT MAX(`%s`) FROM `%s`.`%s`", columnName, conn.getCatalog(), tableName);
            maxVal = query(conn, sql, 1, 3, rs -> {
                if (rs.next()) {
                    return rs.getString(1);
                }
                return "";
            });
        } catch (Exception e) {
            log.error("Exception in finding max value", sql, e);
            throw e;
        }
        return maxVal;
    }

    /**
     * Get columns from table. It does NOT close data source
     */
    public static List<String> getColumns(DruidDataSource dataSource, String tableName) throws Exception {
        PreparedStatement stmt = null;
        Connection conn = null;
        ResultSet rs = null;
        List<String> columns = new ArrayList<>();
        String sql = null;
        try {
            conn = dataSource.getConnection();
            sql = String.format(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'",
                conn.getCatalog(), tableName);
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("Exception in finding columns. SQL: {}", sql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return columns;
    }

    /**
     * Get all tables from db. It does NOT close data source.
     */
    public static List<String> getTables(DruidDataSource dataSource) throws Exception {
        String sql = "SHOW TABLES";
        List<String> tables;
        try (Connection conn = dataSource.getConnection()) {
            tables = query(conn, sql, Integer.MIN_VALUE, 3, rs -> {
                List<String> list = new ArrayList<>();
                while (rs.next()) {
                    list.add(rs.getString(1));
                }
                return list;
            });
        } catch (Exception e) {
            log.error("failed in getTables: {}", sql, e);
            throw e;
        }
        return tables;
    }

    /**
     * Execute query and process with mapper
     */
    public static <T> T query(Connection conn, String query, int fetchSize, int tryTimes, ResultSetMapper<T> mapper) throws SQLException {
        Objects.requireNonNull(mapper, "ResultSetMapper should not be null");
        for (int i = 0; i < tryTimes; i++) {
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY,
                                                       ResultSet.CONCUR_READ_ONLY)) {
                stmt.setFetchSize(fetchSize);
                log.debug("Executing query: {}", query);
                try (ResultSet rs = query(stmt, query)) {
                    return mapper.process(rs);
                }
            } catch (Exception e) {
                log.error("TryTimes: {}, current round: {}, Error query: {}", tryTimes, i, query);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e2) {
                    log.error("Query retry interrupted.", e2);
                }
                if (i == tryTimes - 1) {
                    throw e;
                }
            }
        }
        return null;
    }

    /**
     * SQL query helper
     */
    public static ResultSet query(Connection conn, String sql, int fetchSize)
        throws SQLException {
        return query(conn, sql, fetchSize, QUERY_TIMEOUT);
    }

    /**
     * SQL query helper
     */
    public static ResultSet query(Connection conn, String sql, int fetchSize, int queryTimeout)
        throws SQLException {
        Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
        stmt.setFetchSize(fetchSize);
        stmt.setQueryTimeout(queryTimeout);
        return query(stmt, sql);
    }

    /**
     * Query helper
     */
    public static ResultSet query(Statement stmt, String sql) throws SQLException {
        return stmt.executeQuery(sql);
    }

    public static void closeQuery(ResultSet rs, Statement stmt, Connection conn) {
        JdbcUtils.closeResultSet(rs);
        JdbcUtils.closeStatement(stmt);
        JdbcUtils.closeConnection(conn);
    }

    public static DruidDataSource createDruidMySqlDataSource(boolean usePolarxPoolCN, String ip, int port,
                                                             String dbName, String user,
                                                             String passwd, String encoding, int minPoolSize,
                                                             int maxPoolSize, Map<String, String> params,
                                                             List<String> newConnectionSQLs) throws Exception {
        if (usePolarxPoolCN) {
            DruidDataSourceWrapper dataSource =
                new DruidDataSourceWrapper(dbName, user, passwd, encoding, minPoolSize, maxPoolSize, params,
                    newConnectionSQLs);
            dataSource.init();
            return dataSource;
        } else {
            return createDruidMySqlDataSource(ip, port, dbName, user, passwd, encoding, minPoolSize, maxPoolSize,
                params, newConnectionSQLs);
        }
    }

    public static DruidDataSource createDruidMySqlDataSource(String ip, int port, String dbName, String user,
                                                             String passwd, String encoding, int minPoolSize,
                                                             int maxPoolSize, Map<String, String> params,
                                                             List<String> newConnectionSQLs) throws Exception {
        checkParams(ip, port, user, maxPoolSize, minPoolSize);
        DruidDataSource ds = new DruidDataSource();
        String url = "jdbc:mysql://" + ip + ":" + port;
        if (StringUtils.isNotBlank(dbName)) {
            url = url + "/" + dbName;
        }
        // remove warning msg
        url = url + "?useSSL=false";
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(passwd);
        ds.setTestWhileIdle(true);
        ds.setTestOnBorrow(false);
        ds.setTestOnReturn(false);
        ds.setNotFullTimeoutRetryCount(2);
        ds.setValidConnectionCheckerClassName(MySqlValidConnectionChecker.class.getName());
        ds.setExceptionSorterClassName(MySqlExceptionSorter.class.getName());
        ds.setValidationQuery("SELECT 1");
        ds.setInitialSize(minPoolSize);
        ds.setMinIdle(minPoolSize);
        ds.setMaxActive(maxPoolSize);
        ds.setMaxWait(10 * 1000);
        ds.setTimeBetweenEvictionRunsMillis(60 * 1000);
        ds.setMinEvictableIdleTimeMillis(50 * 1000);
        ds.setUseUnfairLock(true);
        Properties prop = new Properties();
        encoding = StringUtils.isNotBlank(encoding) ? encoding : "utf8mb4";
        if (StringUtils.isNotEmpty(encoding)) {
            if (StringUtils.equalsIgnoreCase(encoding, "utf8mb4")) {
                prop.put("characterEncoding", "utf8");
                if (newConnectionSQLs == null) {
                    newConnectionSQLs = new ArrayList<>();
                }
                newConnectionSQLs.add("set names utf8mb4");
            } else {
                prop.put("characterEncoding", encoding);
            }
        }

        prop.putAll(DEFAULT_MYSQL_CONNECTION_PROPERTIES);
        if (params != null) {
            prop.putAll(params);
        }
        ds.setConnectProperties(prop);
        if (newConnectionSQLs != null && newConnectionSQLs.size() > 0) {
            ds.setConnectionInitSqls(newConnectionSQLs);
            log.info("druid setConnectionInitSqls, {}", JSON.toJSONString(newConnectionSQLs));
        }
        try {
            ds.init();
        } catch (Exception e) {
            throw new Exception("create druid datasource occur exception, with url : " + url + ", user : " + user
                + ", passwd : " + passwd,
                e);
        }
        return ds;
    }

    private static void checkParams(String ip, int port, String user, int maxPoolSize, int minPoolSize) {
        if (StringUtils.isBlank(ip) || port == 0 || StringUtils.isBlank(user)) {
            throw new IllegalArgumentException("ip or port or schema or user is blank, check the pass params, ip:" + ip
                + ", port:" + port + ", user:" + user);
        }
        if (maxPoolSize < 0 || minPoolSize < 0) {
            throw new IllegalArgumentException("maxPoolSize and minPoolSize must be positive");
        }
    }
}
