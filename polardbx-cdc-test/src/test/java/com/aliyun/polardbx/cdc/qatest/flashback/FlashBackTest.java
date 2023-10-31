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
package com.aliyun.polardbx.cdc.qatest.flashback;

import com.alibaba.druid.pool.DruidDataSource;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.cdc.qatest.base.BaseTestCase;
import com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.jdbc.support.JdbcUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.POLARDBX_ADDRESS;
import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.POLARDBX_PASSWORD;
import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.POLARDBX_PORT;
import static com.aliyun.polardbx.cdc.qatest.base.ConfigConstant.POLARDBX_USER;
import static com.aliyun.polardbx.cdc.qatest.flashback.FlashBackConstants.CREATE_SQL;
import static com.aliyun.polardbx.cdc.qatest.flashback.FlashBackConstants.INSERT_SQL_1;
import static com.aliyun.polardbx.cdc.qatest.flashback.FlashBackConstants.INSERT_SQL_2;

@Slf4j
@Ignore
public class FlashBackTest extends BaseTestCase {
    private static final String DB_NAME = "transfer_test";
    private static final String TABLE_NAME_1 = "table_flashback1";
    private static final String TABLE_NAME_2 = "table_flashback2";
    private static DataSource datasource;

    @BeforeClass
    public static void beforeClass() {
        datasource = getDataSource();
    }

    @Test
    public void testBigTxn() throws ExecutionException, InterruptedException {
        ExecutorService service = Executors.newCachedThreadPool();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 1; i++) {
            int finalI = i;
            Future<?> future = service.submit(() -> {
                try (Connection conn = datasource.getConnection()) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("drop table if exists big_txn_text_" + finalI);
                        stmt.execute(
                            "create table if not exists big_txn_text_" + finalI
                                + "(id bigint auto_increment,"
                                + "c1 bigint,"
                                + "c2 bigint,"
                                + "c3 bigint,"
                                + "c4 bigint,"
                                + "c5 mediumtext,"
                                + "primary key(id))"
                                + "dbpartition by hash(id) tbpartition by hash(id) tbpartitions 8");
                    }
                } catch (SQLException throwables) {
                    throw new RuntimeException(throwables);
                }

                try (Connection conn = datasource.getConnection()) {
                    //conn.setAutoCommit(false);
                    try (Statement stmt = conn.createStatement()) {
                        for (int j = 0; j < 8192; j++) {
                            stmt.execute(
                                "insert into big_txn_text_" + finalI
                                    + "(c1,c2,c3,c4,c5)"
                                    + "values"
                                    + "(10000,"
                                    + "10000,"
                                    + "10000,"
                                    + "10000,"
                                    + "repeat('a',1024*1024*12))");
                        }
                    }
                    //conn.commit();
                    System.out.println("one success:" + new Date());
                } catch (SQLException throwables) {
                    throwables.printStackTrace();
                }

            });
            futures.add(future);
        }

        for (
            Future future : futures) {
            future.get();
        }
    }

    @Test
    public void testPersist() throws SQLException {
        for (int i = 0; i < 1; i++) {
            try (Connection conn = datasource.getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("insert into bbb(name)values(repeat('a',1024*1024*500))");
                }
            }
        }
    }

    @Test
    public void testCreateTable1() throws SQLException {
        try (Connection conn = datasource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(String.format(CREATE_SQL, TABLE_NAME_1));
            }
        }
    }

    @Test
    public void testCreateTable2() throws SQLException {
        try (Connection conn = datasource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(String.format(CREATE_SQL, TABLE_NAME_2));
            }
        }
    }

    @Test
    public void testCheckSumSummary() throws SQLException {
        getTableList(DB_NAME, datasource).forEach(t -> {
            try {
                checksumForTableSummary(DB_NAME, t, datasource);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    @Test
    public void testChecksumDetail() throws Exception {
        Map<String, String> map1 = getTableDetailChecksum("transfer_test", "table_flashback1", datasource);
        Map<String, String> map2 = getTableDetailChecksum("transfer_test", "table_flashback2", datasource);
        Map<String, Pair<String, String>> diff = checkDiff(map1, map2);
        System.out.println(JSONObject.toJSONString(diff));

        Map<String, String> map11 = getDataMap("transfer_test.table_flashback1");
        Map<String, String> map22 = getDataMap("transfer_test.table_flashback2");
        Map<String, Pair<String, String>> diffDetail = new HashMap<>();
        for (Map.Entry<String, Pair<String, String>> entry : diff.entrySet()) {
            diffDetail.put(entry.getKey(), Pair.of(map11.get(entry.getKey()), map22.get(entry.getKey())));
        }
        System.out.println(JSONObject.toJSONString(diffDetail));
    }

    @Test
    public void testInsertOrigin() throws SQLException {
        try (Connection conn = datasource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(INSERT_SQL_1);
            }
        }
    }

    @Test
    public void testInsertRecovery() throws SQLException {
        try (Connection conn = datasource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(INSERT_SQL_2);
            }
        }
    }

    @Test
    public void testUpdate() throws SQLException {
        for (int i = 0; i < 10; i++) {
            try (Connection conn = datasource.getConnection()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("update xyz set balance2=" + i + " where id >0;");
                    System.out.println("update success for " + i);
                }
            }
        }
    }

    @Test
    public void testCompareDetail() throws SQLException {
        Map<String, String> map1 = getDataMap("transfer_test.table_flashback1");
        Map<String, String> map2 = getDataMap("transfer_test.table_flashback2");
        checkDiff(map1, map2);
    }

    @Test
    public void testBit() {
        for (int i = 0; i < 8; i++) {
            System.out.println(i << 3);
        }
    }

    private Map<String, Pair<String, String>> checkDiff(Map<String, String> map1, Map<String, String> map2) {
        Map<String, Pair<String, String>> diff = new HashMap<>();
        for (Map.Entry<String, String> entry : map1.entrySet()) {
            String value1 = entry.getValue();
            String value2 = map2.get(entry.getKey());
            if (!StringUtils.equals(value1, value2)) {
                diff.put(entry.getKey(), Pair.of(value1, value2));
            }
        }
        return diff;
    }

    private Map<String, String> getDataMap(String tableName) throws SQLException {
        Map<String, String> map = new HashMap<>();
        try (Connection conn = datasource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet set = stmt.executeQuery("select * from " + tableName);
                ResultSetMetaData metaData = set.getMetaData();
                while (set.next()) {
                    int count = metaData.getColumnCount();
                    for (int i = 1; i <= count; i++) {
                        String name = metaData.getColumnName(i);
                        String value = set.getString(i);
                        map.put(name, value);
                    }
                }
            }
        }
        return map;
    }

    private static DataSource getDataSource() {
        return getDruidDataSource(
            String.format(
                "jdbc:mysql://%s:%s/%s?allowMultiQueries=true&rewriteBatchedStatements=true&characterEncoding=utf-8",
                PropertiesUtil.configProp.getProperty(POLARDBX_ADDRESS),
                PropertiesUtil.configProp.getProperty(POLARDBX_PORT),
                DB_NAME), PropertiesUtil.configProp.getProperty(POLARDBX_USER),
            PropertiesUtil.configProp.getProperty(POLARDBX_PASSWORD));
    }

    private static DruidDataSource getDruidDataSource(String url, String user, String password) {
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

    private void checksumForTableSummary(String dbName, String tableName, DataSource ds) throws Exception {
        List<String> columns = getColumns(dbName, tableName, ds);
        String sourceSQL = generateChecksumSQL(dbName, tableName, columns);
        String countSQL = "select count(*) from " + tableName;
        Connection sourceConn = null;
        ResultSet sourceRs = null;
        ResultSet countRs = null;
        try {
            sourceConn = ds.getConnection();

            sourceRs = DataSourceUtil.query(sourceConn, sourceSQL, 0);
            while (sourceRs.next()) {
                String sourceCheckSum = sourceRs.getString(1);
                System.out.println("checksum for table " + tableName + " is " + sourceCheckSum);
            }

            countRs = DataSourceUtil.query(sourceConn, countSQL, 0);
            while (countRs.next()) {
                String sourceCheckSum = countRs.getString(1);
                // System.out.println("count for table " + tableName + " is " + sourceCheckSum);
            }

        } catch (Exception e) {
            log.error("Compute checksum error. SQL: {}", sourceSQL, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(sourceRs, null, sourceConn);
            DataSourceUtil.closeQuery(countRs, null, sourceConn);
        }
    }

    private Map<String, String> getTableDetailChecksum(String dbName, String tableName, DataSource ds)
        throws Exception {
        Map<String, String> result = new HashMap<>();
        List<String> columns = getColumns(dbName, tableName, ds);
        for (String column : columns) {
            String sourceSQL = generateChecksumSQL(dbName, tableName, Lists.newArrayList(column));
            Connection sourceConn = null;
            ResultSet sourceRs = null;
            ResultSet countRs = null;
            try {
                sourceConn = ds.getConnection();
                sourceRs = DataSourceUtil.query(sourceConn, sourceSQL, 0);
                while (sourceRs.next()) {
                    String sourceCheckSum = sourceRs.getString(1);
                    result.put(column, sourceCheckSum);
                }
            } catch (Exception e) {
                log.error("Compute checksum error. SQL: {}", sourceSQL, e);
                throw e;
            } finally {
                DataSourceUtil.closeQuery(sourceRs, null, sourceConn);
                DataSourceUtil.closeQuery(countRs, null, sourceConn);
            }
        }
        return result;
    }

    public List<String> getTableList(String database, DataSource ds) throws SQLException {
        List<String> tables = new ArrayList<>();
        PreparedStatement stmt = null;
        Connection conn = null;
        ResultSet rs = null;
        String sql = null;
        try {
            conn = ds.getConnection();
            conn.setCatalog(database);
            sql = "show tables;";
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                tables.add(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("Exception in finding columns. SQL: {}", sql, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return tables;
    }

    public List<String> getColumns(String database, String tableName, DataSource ds) throws Exception {
        PreparedStatement stmt = null;
        Connection conn = null;
        ResultSet rs = null;
        List<String> columns = new ArrayList<>();
        String sql = null;
        try {
            conn = ds.getConnection();
            sql = String.format(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = '%s' AND TABLE_NAME = '%s'",
                database, tableName);
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("Exception in finding columns. SQL: {}, database {}, table {}", sql, database, tableName, e);
            throw e;
        } finally {
            DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return columns;
    }

    public String generateChecksumSQL(String dbName, String tableName, List<String> columns) {
        // ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`)
        StringBuilder concatSb = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i == 0) {
                concatSb.append(String.format("ISNULL(`%s`)", columns.get(i)));
            } else {
                concatSb.append(String.format(", ISNULL(`%s`)", columns.get(i)));
            }
        }
        // ',', `id`, `name`, `order_od`, CONCAT(ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`)))
        StringBuilder concatWsSb = new StringBuilder();
        // ',' + space
        concatWsSb.append("',', ");
        for (String column : columns) {
            concatWsSb.append(String.format("`%s`, ", column));
        }
        concatWsSb.append(concatSb);
        // CONCAT_WS(',', `id`, `name`, `order_od`, CONCAT(ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`))))
        String concatWs = String.format("CONCAT_WS(%s)", concatWsSb);

        return String.format(
            "SELECT BIT_XOR(CAST(CRC32(%s) AS UNSIGNED)) AS checksum FROM `%s`.`%s`", concatWs, dbName, tableName);
    }

    public static class DataSourceUtil {
        private static final int QUERY_TIMEOUT = 7200;

        public static void closeQuery(ResultSet rs, Statement stmt, Connection conn) {
            JdbcUtils.closeResultSet(rs);
            JdbcUtils.closeStatement(stmt);
            JdbcUtils.closeConnection(conn);
        }

        public static ResultSet query(Connection conn, String sql, int fetchSize)
            throws SQLException {
            return query(conn, sql, fetchSize, QUERY_TIMEOUT);
        }

        public static ResultSet query(Connection conn, String sql, int fetchSize, int queryTimeout)
            throws SQLException {
            Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            stmt.setFetchSize(fetchSize);
            stmt.setQueryTimeout(queryTimeout);
            return query(stmt, sql);
        }

        public static ResultSet query(Statement stmt, String sql) throws SQLException {
            return stmt.executeQuery(sql);
        }
    }
}
