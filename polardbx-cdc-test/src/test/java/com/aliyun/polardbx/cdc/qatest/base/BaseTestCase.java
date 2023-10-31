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
package com.aliyun.polardbx.cdc.qatest.base;

import com.alibaba.polardbx.druid.sql.dialect.mysql.parser.MySqlExprParser;
import com.alibaba.polardbx.druid.sql.parser.ByteString;
import com.alibaba.polardbx.druid.sql.parser.Lexer;
import com.alibaba.polardbx.druid.sql.parser.Token;
import com.aliyun.polardbx.cdc.qatest.flashback.FlashBackTest;
import lombok.SneakyThrows;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.getConnectionProperties;

/**
 * created by ziyang.lb
 */
@RunWith(CommonCaseRunner.class)
public class BaseTestCase implements BaseTestMode {
    private static final Logger log = LoggerFactory.getLogger(BaseTestCase.class);

    private List<ConnectionWrap> polardbxConnections = new ArrayList<>();
    private List<ConnectionWrap> dnConnections = new ArrayList<>();
    private List<ConnectionWrap> dnConnectionsSecond = new ArrayList<>();
    private List<ConnectionWrap> metaDBConnections = new ArrayList<>();
    private List<ConnectionWrap> cdcSyncDbConnections = new ArrayList<>();
    private List<ConnectionWrap> cdcSyncDbConnectionsFirst = new ArrayList<>();
    private List<ConnectionWrap> cdcSyncDbConnectionsSecond = new ArrayList<>();
    private List<ConnectionWrap> cdcSyncDbConnectionsThird = new ArrayList<>();

    protected static void checkPhySqlOrder(List<List<String>> trace) {
        int currentPhySqlId = 0;
        for (List<String> traceRow : trace) {
            final String phySql = traceRow.get(11);
            final MySqlExprParser exprParser = new MySqlExprParser(ByteString.from(phySql), true);
            final Lexer lexer = exprParser.getLexer();
            final String headerHint = lexer.getComments().get(0);

            final EnumSet<Token> acceptToken =
                EnumSet.of(Token.SELECT, Token.INSERT, Token.REPLACE, Token.UPDATE, Token.DELETE);
            Token token = lexer.token();
            while (!acceptToken.contains(token)) {
                lexer.nextToken();
                token = lexer.token();
            }

            final String[] splited = StringUtils.split(headerHint, "/");
            Assert.assertEquals("Unexpected header hint for physical sql: " + headerHint, 6, splited.length);
            final int phySqlId = Integer.valueOf(splited[3]);
            Assert.assertTrue(currentPhySqlId <= phySqlId);
            currentPhySqlId = phySqlId;
        }
    }

    public static String escape(String str) {
        String regex = "(?<!`)`(?!`)";
        return str.replaceAll(regex, "``");
    }

    @Before
    public void beforeBaseTestCase() {
        this.polardbxConnections = new ArrayList<>();
        this.dnConnections = new ArrayList<>();
        this.dnConnectionsSecond = new ArrayList<>();
        this.metaDBConnections = new ArrayList<>();
        this.cdcSyncDbConnections = new ArrayList<>();
        this.cdcSyncDbConnectionsFirst = new ArrayList<>();
        this.cdcSyncDbConnectionsSecond = new ArrayList<>();
        this.cdcSyncDbConnectionsThird = new ArrayList<>();
    }

    public synchronized Connection getPolardbxConnection() {
        return getPolardbxConnection(PropertiesUtil.polardbXDBName1(usingNewPartDb()));
    }

    public synchronized Connection getPolardbxConnection(String db) {
        try {
            checkClosedPolardbxConnections();
            Connection connection = ConnectionManager.getInstance().getDruidPolardbxConnection();
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.polardbxConnections.add(connectionWrap);
            useDb(connectionWrap, db);
            setSqlMode(ConnectionManager.getInstance().getPolardbxMode(), connectionWrap);
            return connectionWrap;
        } catch (SQLException t) {
            log.error("get PolardbxConnection error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getPolardbxDirectConnection() {
        return getPolardbxDirectConnection(PropertiesUtil.polardbXDBName1(usingNewPartDb()));
    }

    public synchronized Connection getPolardbxDirectConnection(String db) {
        try {
            checkClosedPolardbxConnections();
            Connection connection = ConnectionManager.getInstance().newPolarDBXConnection();
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.polardbxConnections.add(connectionWrap);
            useDb(connectionWrap, db);
            setSqlMode(ConnectionManager.getInstance().getPolardbxMode(), connectionWrap);
            return connectionWrap;
        } catch (Throwable t) {
            log.error("get PolardbxDirectConnection error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getPolardbxConnectionWithExtraParams(String extraParams) {
        return getPolardbxDirectConnection(PropertiesUtil.polardbXDBName1(usingNewPartDb()), extraParams);
    }

    public synchronized Connection getPolardbxDirectConnection(String db, String extraParams) {
        try {
            checkClosedPolardbxConnections();
            Connection connection = ConnectionManager.getInstance().newPolarDBXConnectionWithExtraParams(extraParams);
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.polardbxConnections.add(connectionWrap);
            useDb(connectionWrap, db);
            setSqlMode(ConnectionManager.getInstance().getPolardbxMode(), connectionWrap);
            return connectionWrap;
        } catch (Throwable t) {
            log.error("get PolardbxDirectConnection with extra params error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getDataNodeConnection() {
        return getDataNodeConnection(PropertiesUtil.mysqlDBName1());
    }

    public synchronized Connection getDataNodeConnection(String db) {
        try {
            checkClosedDnConnections();
            Connection connection = ConnectionManager.getInstance().getDruidDataNodeConnection();
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.dnConnections.add(connectionWrap);
            useDb(connectionWrap, db);
            setSqlMode(ConnectionManager.getInstance().getMysqlMode(), connectionWrap);
            return connectionWrap;
        } catch (SQLException t) {
            log.error("get MysqlConnection error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getDataNodeConnectionSecond() {
        return getDataNodeConnectionSecond(PropertiesUtil.mysqlDBName1());
    }

    public synchronized Connection getDataNodeConnectionSecond(String db) {
        try {
            checkClosedDnConnectionsSecond();
            Connection connection = ConnectionManager.getInstance().getDruidDataNodeConnectionSecond();
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.dnConnectionsSecond.add(connectionWrap);
            useDb(connectionWrap, db);
            setSqlMode(ConnectionManager.getInstance().getMysqlMode(), connectionWrap);
            return connectionWrap;
        } catch (SQLException t) {
            log.error("get MysqlConnectionSecond error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getDataNodeConnectionByAddress(String fullAddress) {
        return getDataNodeConnectionByAddress(fullAddress, PropertiesUtil.mysqlDBName1());
    }

    public synchronized Connection getDataNodeConnectionByAddress(String fullAddress, String db) {
        String mysqlFullAddress = String.format("%s:%s", ConnectionManager.getInstance().getDnMysqlAddress(),
            ConnectionManager.getInstance().getDnMysqlPort());
        String mysqlFullAddressSecond =
            String.format("%s:%s", ConnectionManager.getInstance().getDnMysqlAddressSecond(),
                ConnectionManager.getInstance().getDnMysqlPortSecond());

        if (StringUtils.equals(fullAddress, mysqlFullAddress)) {
            return getDataNodeConnection(db);
        } else if (StringUtils.equals(fullAddress, mysqlFullAddressSecond)) {
            return getDataNodeConnectionSecond(db);
        } else {
            throw new RuntimeException("fullAddress mismatched : " + fullAddress);
        }
    }

    public synchronized Connection getDataNodeDirectConnection() {
        return getDataNodeDirectConnection(PropertiesUtil.mysqlDBName1());
    }

    public synchronized Connection getDataNodeDirectConnection(String db) {
        try {
            checkClosedDnConnections();
            Connection connection = ConnectionManager.getInstance().newMysqlConnection();
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.dnConnections.add(connectionWrap);
            useDb(connectionWrap, db);
            setSqlMode(ConnectionManager.getInstance().getMysqlMode(), connectionWrap);
            return connectionWrap;
        } catch (Throwable t) {
            log.error("get MysqlDirectConnection error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getDataNodeConnectionWithExtraParams(String extraParams) {
        return getDataNodeDirectConnection(PropertiesUtil.mysqlDBName1(), extraParams);
    }

    public synchronized Connection getDataNodeDirectConnection(String db, String extraParams) {
        try {
            checkClosedDnConnections();
            Connection connection = ConnectionManager.getInstance().newMysqlConnectionWithExtraParams(extraParams);
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.dnConnections.add(connectionWrap);
            useDb(connectionWrap, db);
            setSqlMode(ConnectionManager.getInstance().getMysqlMode(), connectionWrap);
            return connectionWrap;
        } catch (Throwable t) {
            log.error("get MysqlDirectConnection error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getMetaConnection() {
        try {
            checkClosedMetaDBConnections();
            Connection connection = ConnectionManager.getInstance().getDruidMetaConnection();
            JdbcUtil.useDb(connection, PropertiesUtil.getMetaDB);
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.metaDBConnections.add(connectionWrap);
            return connectionWrap;
        } catch (SQLException t) {
            log.error("getPolardbxConnection error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getPolardbxDirectConnection(
        String server, String user, String password, String polardbxPort) {
        try {
            checkClosedPolardbxConnections();
            String url = String.format(
                ConfigConstant.URL_PATTERN_WITHOUT_DB + getConnectionProperties(false), server, polardbxPort);
            Properties prop = new Properties();
            prop.setProperty("user", user);
            if (password != null) {
                prop.setProperty("password", password);
            }
            prop.setProperty("allowMultiQueries", String.valueOf(true));
            ConnectionWrap connectionWrap = new ConnectionWrap(DriverManager.getConnection(url, prop));
            this.polardbxConnections.add(connectionWrap);
            return connectionWrap;
        } catch (SQLException t) {
            log.error("get PolardbxConnection error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getPolardbxDirectConnection(
        String server, String user, String dbName, String password, String polardbxPort) {
        try {
            checkClosedPolardbxConnections();
            String url = String.format(
                ConfigConstant.URL_PATTERN_WITH_DB + getConnectionProperties(false), server, polardbxPort, dbName);
            Properties prop = new Properties();
            prop.setProperty("user", user);
            if (password != null) {
                prop.setProperty("password", password);
            }
            prop.setProperty("allowMultiQueries", String.valueOf(true));
            ConnectionWrap connectionWrap = new ConnectionWrap(DriverManager.getConnection(url, prop));
            this.polardbxConnections.add(connectionWrap);
            return connectionWrap;
        } catch (SQLException t) {
            log.error("get PolardbxConnection error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getCdcSyncDbConnection() {
        return getCdcSyncDbConnection(null);
    }

    public synchronized Connection getCdcSyncDbConnection(String db) {
        try {
            checkClosedCdcSyncDbConnections();
            Connection connection = ConnectionManager.getInstance().getDruidCdcSyncDbConnection();
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.cdcSyncDbConnections.add(connectionWrap);
            if (StringUtils.isNotBlank(db)) {
                useDb(connectionWrap, db);
            }
            return connectionWrap;
        } catch (SQLException t) {
            log.error("get MysqlConnection error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getCdcSyncDbConnectionFirst() {
        return getCdcSyncDbConnectionFirst("mysql");
    }

    public synchronized Connection getCdcSyncDbConnectionFirst(String db) {
        try {
            checkClosedCdcSyncDbConnectionsFirst();
            Connection connection = ConnectionManager.getInstance().getDruidCdcSyncDbConnectionFirst();
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.cdcSyncDbConnectionsFirst.add(connectionWrap);
            useDb(connectionWrap, db);
            return connectionWrap;
        } catch (SQLException t) {
            log.error("get MysqlConnectionSecond error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getCdcSyncDbConnectionSecond() {
        return getCdcSyncDbConnectionSecond("mysql");
    }

    public synchronized Connection getCdcSyncDbConnectionSecond(String db) {
        try {
            checkClosedCdcSyncDbConnectionsSecond();
            Connection connection = ConnectionManager.getInstance().getDruidCdcSyncDbConnectionSecond();
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.cdcSyncDbConnectionsSecond.add(connectionWrap);
            useDb(connectionWrap, db);
            return connectionWrap;
        } catch (SQLException t) {
            log.error("get MysqlConnectionSecond error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getCdcSyncDbConnectionThird() {
        return getCdcSyncDbConnectionThird("mysql");
    }

    public synchronized Connection getCdcSyncDbConnectionThird(String db) {
        try {
            checkClosedCdcSyncDbConnectionsThird();
            Connection connection = ConnectionManager.getInstance().getDruidCdcSyncDbConnectionThird();
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.cdcSyncDbConnectionsThird.add(connectionWrap);
            useDb(connectionWrap, db);
            return connectionWrap;
        } catch (SQLException t) {
            log.error("get MysqlConnectionSecond error!", t);
            throw new RuntimeException(t);
        }
    }

    private void checkClosedDnConnections() {
        Iterator<ConnectionWrap> iterator = dnConnections.iterator();
        removeClosed(iterator);
    }

    private void checkClosedDnConnectionsSecond() {
        Iterator<ConnectionWrap> iterator = dnConnectionsSecond.iterator();
        removeClosed(iterator);
    }

    private void checkClosedMetaDBConnections() {
        Iterator<ConnectionWrap> iterator = metaDBConnections.iterator();
        removeClosed(iterator);
    }

    private void checkClosedCdcSyncDbConnections() {
        Iterator<ConnectionWrap> iterator = cdcSyncDbConnections.iterator();
        removeClosed(iterator);
    }

    private void checkClosedCdcSyncDbConnectionsFirst() {
        Iterator<ConnectionWrap> iterator = cdcSyncDbConnectionsFirst.iterator();
        removeClosed(iterator);
    }

    private void checkClosedCdcSyncDbConnectionsSecond() {
        Iterator<ConnectionWrap> iterator = cdcSyncDbConnectionsSecond.iterator();
        removeClosed(iterator);
    }

    private void checkClosedCdcSyncDbConnectionsThird() {
        Iterator<ConnectionWrap> iterator = cdcSyncDbConnectionsThird.iterator();
        removeClosed(iterator);
    }

    private void checkClosedPolardbxConnections() {
        Iterator<ConnectionWrap> iterator = polardbxConnections.iterator();
        removeClosed(iterator);
    }

    @SneakyThrows
    private void removeClosed(Iterator<ConnectionWrap> iterator) {
        while (iterator.hasNext()) {
            Connection connection = iterator.next();
            if (connection.isClosed()) {
                iterator.remove();
            }
        }
    }

    @After
    public void afterBaseTestCase() {
        Throwable throwable = null;
        for (ConnectionWrap connection : polardbxConnections) {
            if (!connection.isClosed()) {
                //确保所有连接都被正常关闭
                try {
                    //保险起见, 主动rollback
                    if (!connection.getAutoCommit()) {
                        connection.rollback();
                    }
                    connection.close();
                } catch (Throwable t) {
                    log.error("close the Connection!", t);
                    if (!"connection disabled".contains(t.getMessage())) {
                        if (throwable == null) {
                            throwable = t;
                        }
                    }
                }
            }
        }

        for (ConnectionWrap connection : dnConnections) {
            if (!connection.isClosed()) {
                //确保所有连接都被正常关闭
                try {
                    connection.close();
                } catch (Throwable t) {
                    log.error("close the Connection!", t);
                    if (throwable == null) {
                        throwable = t;
                    }
                }
            }
        }

        for (ConnectionWrap connection : dnConnectionsSecond) {
            if (!connection.isClosed()) {
                //确保所有连接都被正常关闭
                try {
                    connection.close();
                } catch (Throwable t) {
                    log.error("close the Connection!", t);
                    if (throwable == null) {
                        throwable = t;
                    }
                }
            }
        }

        for (ConnectionWrap connection : metaDBConnections) {
            if (!connection.isClosed()) {
                //确保所有连接都被正常关闭
                try {
                    connection.close();
                } catch (Throwable t) {
                    log.error("close the Connection!", t);
                    if (throwable == null) {
                        throwable = t;
                    }
                }
            }
        }

        for (ConnectionWrap connection : cdcSyncDbConnections) {
            if (!connection.isClosed()) {
                //确保所有连接都被正常关闭
                try {
                    connection.close();
                } catch (Throwable t) {
                    log.error("close the Connection!", t);
                    if (throwable == null) {
                        throwable = t;
                    }
                }
            }
        }

        for (ConnectionWrap connection : cdcSyncDbConnectionsFirst) {
            if (!connection.isClosed()) {
                //确保所有连接都被正常关闭
                try {
                    connection.close();
                } catch (Throwable t) {
                    log.error("close the Connection!", t);
                    if (throwable == null) {
                        throwable = t;
                    }
                }
            }
        }

        for (ConnectionWrap connection : cdcSyncDbConnectionsSecond) {
            if (!connection.isClosed()) {
                //确保所有连接都被正常关闭
                try {
                    connection.close();
                } catch (Throwable t) {
                    log.error("close the Connection!", t);
                    if (throwable == null) {
                        throwable = t;
                    }
                }
            }
        }

        for (ConnectionWrap connection : cdcSyncDbConnectionsThird) {
            if (!connection.isClosed()) {
                //确保所有连接都被正常关闭
                try {
                    connection.close();
                } catch (Throwable t) {
                    log.error("close the Connection!", t);
                    if (throwable == null) {
                        throwable = t;
                    }
                }
            }
        }

        if (throwable != null) {
            Assert.fail(throwable.getMessage());
        }
    }

    public List<List<String>> getTrace(Connection tddlConnection) {
        final ResultSet rs = JdbcUtil.executeQuery("show trace", tddlConnection);
        return JdbcUtil.getStringResult(rs, false);
    }

    public void setSqlMode(String mode, Connection conn) {
        String sql = "SET session sql_mode = '" + mode + "'";
        JdbcUtil.updateDataTddl(conn, sql, null);
    }

    public int getExplainNum(Connection tddlConnection, String sql) {
        ResultSet rs = JdbcUtil.executeQuerySuccess(tddlConnection, "explain " + sql);
        try {
            final List<String> columnNameListToLowerCase = JdbcUtil.getColumnNameListToLowerCase(rs);
            if (columnNameListToLowerCase.contains("count")) {
                rs.next();
                return rs.getInt("COUNT");
            }
            return JdbcUtil.resultsSize(rs);
        } catch (Exception e) {
            Assert.fail("explain exception:explain " + sql + ", messsage is " + e.getMessage());
            return -1;
        } finally {
            JdbcUtil.close(rs);
        }

    }

    public String getExplainResult(Connection tddlConnection, String sql) {
        ResultSet rs = JdbcUtil.executeQuerySuccess(tddlConnection, "explain " + sql);
        try {
            return JdbcUtil.resultsStr(rs);
        } finally {
            JdbcUtil.close(rs);
        }
    }

    protected void checkPhySqlId(List<List<String>> trace) {
        final Map<String, List<Token>> phySqlGroups = new HashMap<>();
        for (List<String> traceRow : trace) {
            final String phySql = traceRow.get(11);
            final MySqlExprParser exprParser = new MySqlExprParser(ByteString.from(phySql), true);
            final Lexer lexer = exprParser.getLexer();
            final String headerHint = lexer.getComments().get(0);

            final EnumSet<Token> acceptToken =
                EnumSet.of(Token.SELECT, Token.INSERT, Token.REPLACE, Token.UPDATE, Token.DELETE);
            Token token = lexer.token();
            while (!acceptToken.contains(token)) {
                lexer.nextToken();
                token = lexer.token();
            }

            final String[] splited = StringUtils.split(headerHint, "/");
            Assert.assertEquals("Unexpected header hint for physical sql: " + headerHint, 6, splited.length);
            final String phySqlId = splited[3];

            phySqlGroups.computeIfAbsent(phySqlId, (k) -> new ArrayList<>()).add(token);
        }

        for (Map.Entry<String, List<Token>> entry : phySqlGroups.entrySet()) {
            final String phySqlId = entry.getKey();
            final List<Token> phySqlTypes = entry.getValue();

            final Token sqlType = phySqlTypes.get(0);
            for (Token phySqlType : phySqlTypes) {
                Assert.assertEquals("Different physical type with same phySqlId: " + phySqlId, sqlType, phySqlType);
            }
        }
    }

    public void useDb(Connection connection, String db) {
        JdbcUtil.executeQuery("use " + db, connection);
    }

    public List<String> getColumnsByDesc(String database, String tableName, Connection conn) throws Exception {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<String> columns = new ArrayList<>();
        String sql = null;
        try {
            sql = String.format("DESC `%s`.`%s`", escape(database), escape(tableName));
            stmt = conn.prepareStatement(sql);
            rs = stmt.executeQuery(sql);
            while (rs.next()) {
                columns.add(rs.getString(1));
            }
        } catch (SQLException e) {
            log.error("Exception in finding columns. SQL: {}, database {}, table {}", sql, database, tableName, e);
            throw e;
        } finally {
            FlashBackTest.DataSourceUtil.closeQuery(rs, stmt, conn);
        }
        return columns;
    }

    public List<String> getColumns(String database, String tableName, Connection conn) throws Exception {
        PreparedStatement stmt = null;
        ResultSet rs = null;
        List<String> columns = new ArrayList<>();
        String sql = null;
        try {
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
            FlashBackTest.DataSourceUtil.closeQuery(rs, stmt, conn);
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
            concatWsSb.append(String.format("LOWER(`%s`), ", column));
        }
        concatWsSb.append(concatSb);
        // CONCAT_WS(',', `id`, `name`, `order_od`, CONCAT(ISNULL(`id`), ISNULL(`name`), ISNULL(`order_id`))))
        String concatWs = String.format("CONCAT_WS(%s)", concatWsSb);

        return String.format(
            "SELECT BIT_XOR(CAST(CRC32(%s) AS UNSIGNED)) AS checksum FROM `%s`.`%s`", concatWs,
            escape(dbName), escape(tableName));
    }

    public void waitForSync(int timeout, TimeUnit unit) throws SQLException {
        Connection conn = getPolardbxConnection();
        String tokenTable = "t_token_" + UUID.randomUUID().toString();
        useDb(conn, "cdc_token_db");
        JdbcUtil.executeSuccess(conn,
            "create table `" + tokenTable + "` (  `id` bigint(20) NOT NULL,   PRIMARY KEY (`id`))");
        conn.close();
        long targetTimestamp = unit.toMillis(timeout) + System.currentTimeMillis();
        Connection syncConn = getCdcSyncDbConnection();
        useDb(syncConn, "cdc_token_db");
        while (true) {
            ResultSet rs = JdbcUtil.executeQuery("show tables", syncConn);
            while (rs.next()) {
                String table = rs.getString(1);
                if (org.apache.commons.lang3.StringUtils.equalsIgnoreCase(table, tokenTable)) {
                    log.info("find cdc_token_db with : " + table);
                    return;
                }
            }
            rs.close();
            Assert.assertTrue("wait for all data sync failed! ", System.currentTimeMillis() < targetTimestamp);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
            }
        }
    }

    public void checkOneTable(Pair<String, String> tablePair) throws Exception {
        try {
            List<String> columns1 = getColumnsByDesc(tablePair.getKey(), tablePair.getValue(), getPolardbxConnection());
            List<String> columns2 =
                getColumnsByDesc(tablePair.getKey(), tablePair.getValue(), getCdcSyncDbConnection());
            Assert.assertEquals(
                columns1.stream().map(String::toLowerCase).collect(Collectors.toList()),
                columns2.stream().map(String::toLowerCase).collect(Collectors.toList()));
            log.info("columns is consistent between source and target for table {}.{}", tablePair.getKey(),
                tablePair.getValue());
        } catch (Throwable t) {
            log.info("Compute columns consistent error for [{}].[{}]!", tablePair.getKey(), tablePair.getValue(), t);
            throw t;
        }

        List<String> columns = getColumns(tablePair.getKey(), tablePair.getValue(), getPolardbxConnection());
        String sourceSQL = generateChecksumSQL(tablePair.getKey(), tablePair.getValue(), columns);
        String dstSQL = generateChecksumSQL(tablePair.getKey(), tablePair.getValue(), columns);
        Connection sourceConn = null;
        ResultSet sourceRs = null;

        Connection targetConn = null;
        ResultSet targetRs = null;
        try {
            sourceConn = getPolardbxConnection();
            sourceRs = FlashBackTest.DataSourceUtil.query(sourceConn, sourceSQL, 0);
            String sourceCheckSum = null;
            while (sourceRs.next()) {
                sourceCheckSum = sourceRs.getString(1);
            }

            targetConn = getCdcSyncDbConnection();
            targetRs = FlashBackTest.DataSourceUtil.query(targetConn, dstSQL, 0);
            String targetChecksum = null;
            while (targetRs.next()) {
                targetChecksum = targetRs.getString(1);
            }

            if (!org.apache.commons.lang3.StringUtils.equals(sourceCheckSum, targetChecksum)) {
                throw new RuntimeException(
                    String.format("checksum is diff for table %s.%s, source checksum is %s, target checksum is %s,"
                            + "source check sql is %s , target check sql is %s.",
                        tablePair.getKey(), tablePair.getValue(), sourceCheckSum, targetChecksum, sourceSQL, dstSQL));
            } else {
                log.info(String.format("checksum is consistent between source and target for table %s.%s",
                    tablePair.getKey(), tablePair.getValue()));
            }
        } catch (Exception e) {
            log.error("Compute checksum error. SQL: {}", sourceSQL, e);
            throw e;
        } finally {
            FlashBackTest.DataSourceUtil.closeQuery(sourceRs, null, sourceConn);
            FlashBackTest.DataSourceUtil.closeQuery(targetRs, null, targetConn);
        }
    }

}
