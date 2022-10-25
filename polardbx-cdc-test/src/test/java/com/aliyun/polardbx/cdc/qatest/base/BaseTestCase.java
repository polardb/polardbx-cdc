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
package com.aliyun.polardbx.cdc.qatest.base;

import com.alibaba.polardbx.druid.sql.dialect.mysql.parser.MySqlExprParser;
import com.alibaba.polardbx.druid.sql.parser.ByteString;
import com.alibaba.polardbx.druid.sql.parser.Lexer;
import com.alibaba.polardbx.druid.sql.parser.Token;
import org.apache.commons.lang.StringUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.aliyun.polardbx.cdc.qatest.base.PropertiesUtil.getConnectionProperties;

/**
 * created by ziyang.lb
 */
public class BaseTestCase implements BaseTestMode {
    private static final Logger log = LoggerFactory.getLogger(BaseTestCase.class);

    private List<ConnectionWrap> polardbxConnections = new ArrayList<>();
    private List<ConnectionWrap> dnConnections = new ArrayList<>();
    private List<ConnectionWrap> dnConnectionsSecond = new ArrayList<>();
    private List<ConnectionWrap> metaDBConnections = new ArrayList<>();
    private List<ConnectionWrap> cdcSyncDbConnections = new ArrayList<>();
    private List<ConnectionWrap> cdcSyncDbConnectionsSecond = new ArrayList<>();

    @Before
    public void beforeBaseTestCase() {
        this.polardbxConnections = new ArrayList<>();
        this.dnConnections = new ArrayList<>();
        this.dnConnectionsSecond = new ArrayList<>();
        this.metaDBConnections = new ArrayList<>();
        this.cdcSyncDbConnections = new ArrayList<>();
        this.cdcSyncDbConnectionsSecond = new ArrayList<>();
    }

    public synchronized Connection getPolardbxConnection() {
        return getPolardbxConnection(PropertiesUtil.polardbXDBName1(usingNewPartDb()));
    }

    public synchronized Connection getPolardbxConnection(String db) {
        try {
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
            String url = String.format(
                ConfigConstant.URL_PATTERN + getConnectionProperties(), server, polardbxPort);
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
            String url = String.format(
                ConfigConstant.URL_PATTERN_WITH_DB + getConnectionProperties(), server, polardbxPort, dbName);
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
        return getCdcSyncDbConnection("mysql");
    }

    public synchronized Connection getCdcSyncDbConnection(String db) {
        try {
            Connection connection = ConnectionManager.getInstance().getDruidCdcSyncDbConnection();
            ConnectionWrap connectionWrap = new ConnectionWrap(connection);
            this.cdcSyncDbConnections.add(connectionWrap);
            useDb(connectionWrap, db);
            return connectionWrap;
        } catch (SQLException t) {
            log.error("get MysqlConnection error!", t);
            throw new RuntimeException(t);
        }
    }

    public synchronized Connection getCdcSyncDbConnectionSecond() {
        return getCdcSyncDbConnectionSecond("mysql");
    }

    public synchronized Connection getCdcSyncDbConnectionSecond(String db) {
        try {
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

    public void useDb(Connection connection, String db) {
        JdbcUtil.executeQuery("use " + db, connection);
    }

}
