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
package com.aliyun.polardbx.binlog.testing.h2;

import com.alibaba.polardbx.druid.sql.ast.SQLDataType;
import com.alibaba.polardbx.druid.sql.ast.SQLDataTypeImpl;
import com.alibaba.polardbx.druid.sql.ast.SQLIndexDefinition;
import com.alibaba.polardbx.druid.sql.ast.SQLStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCharacterDataType;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLColumnDefinition;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLDropDatabaseStatement;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLTableElement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.MySqlPrimaryKey;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlCreateTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlLockTableStatement;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlTableIndex;
import com.alibaba.polardbx.druid.sql.dialect.mysql.ast.statement.MySqlUnlockTablesStatement;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.StringEscapeUtils;
import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.Assert;

import javax.sql.DataSource;
import java.io.File;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Scanner;

import static com.aliyun.polardbx.binlog.util.SQLUtils.parseSQLStatement;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class H2Util {
    private static final String URL = "jdbc:h2:mem:testing;CACHE_SIZE=1000;MODE=MYSQL;";
    private static final String USER_NAME = "cdc";
    private static final String USER_PASSWORD = "cdc";

    @SneakyThrows
    public static Connection getH2Connection() {
        return JdbcConnectionPool.create(URL, USER_NAME, USER_PASSWORD)
            .getConnection();
    }

    public static JdbcConnectionPool getH2JdbcConnectionPool() {
        return JdbcConnectionPool.create(URL, USER_NAME, USER_PASSWORD);
    }

    @SneakyThrows
    public static void executeBatchSql(Connection connection, File file) {
        String sql = FileUtils.readFileToString(file, "UTF-8");
        executeBatchSql(connection, sql);
    }

    @SneakyThrows
    public static void executeBatchSql(Connection connection, String sql) {
        Scanner scanner = new Scanner(sql);

        StringBuilder sb = new StringBuilder();
        while (scanner.hasNextLine()) {
            String s = scanner.nextLine();
            if (s.trim().startsWith("--")) {
                continue;
            }
            if (s.trim().startsWith("#")) {
                continue;
            }
            if (s.trim().startsWith("/*") && s.trim().endsWith("*/")) {
                continue;
            }
            if (StringUtils.startsWithIgnoreCase(s.trim(), "SET @saved_cs_client")) {
                continue;
            }
            if (StringUtils.startsWithIgnoreCase(s.trim(), "SET character_set_client")) {
                continue;
            }
            if (StringUtils.startsWithIgnoreCase(s.trim(), "DELIMITER")) {
                continue;
            }

            sb.append(s).append("\n");
            if (StringUtils.endsWith(s, ";")) {
                String convertedSql = "";
                try (Statement stmt = connection.createStatement()) {
                    convertedSql = convertSql(sb.toString());
                    if (StringUtils.isNotBlank(convertedSql)) {
                        stmt.execute(convertedSql);

                        if (log.isDebugEnabled()) {
                            log.debug("successfully execute sql : " + convertedSql);
                        }
                    }
                    sb = new StringBuilder();
                } catch (Throwable t) {
                    log.error("execute sql error ! \n origin sql is : {}, \n converted sql is {}, \n isSame : {}",
                        s, convertedSql, StringUtils.equals(s, convertedSql));
                    throw t;
                }
            }
        }
    }

    private static String convertSql(String sql) throws NoSuchFieldException, IllegalAccessException {
        sql = sql.trim();
        if (StringUtils.startsWithIgnoreCase(sql, "insert") || StringUtils.startsWithIgnoreCase(sql, "update")
            || StringUtils.startsWithIgnoreCase(sql, "delete")) {
            sql = StringUtils.replace(sql, "\\'", "''");
            sql = StringEscapeUtils.unescapeJava(sql);
        }

        //remove hints like /*!32312 ... */ , /*! ... */
        sql = removeHints(sql);
        if (StringUtils.isBlank(sql)) {
            return "";
        }

        SQLStatement sqlStatement = parseSQLStatement(sql);
        if (sqlStatement instanceof SQLCreateDatabaseStatement || sqlStatement instanceof SQLDropDatabaseStatement) {
            //h2 不支持 database key word
            return StringUtils.replaceIgnoreCase(sql, "DATABASE", "SCHEMA");
        } else if (sqlStatement instanceof MySqlCreateTableStatement) {
            MySqlCreateTableStatement createTableStatement = (MySqlCreateTableStatement) sqlStatement;
            createTableStatement.setPartitioning(null);
            createTableStatement.setTablePartitions(null);
            createTableStatement.setTablePartitionBy(null);
            createTableStatement.getTableOptions().clear();

            Iterator<SQLTableElement> it = createTableStatement.getTableElementList().iterator();
            while (it.hasNext()) {
                SQLTableElement el = it.next();

                if (el instanceof MySqlTableIndex) {
                    it.remove();
                } else if (el instanceof MySqlKey) {
                    if (!(el instanceof MySqlPrimaryKey)) {
                        it.remove();
                    } else {
                        //h2 not support USING BTREE
                        SQLIndexDefinition indexDefinition = ((MySqlPrimaryKey) el).getIndexDefinition();
                        Field field = SQLIndexDefinition.class.getDeclaredField("options");
                        field.setAccessible(true);
                        field.set(indexDefinition, null);
                    }
                } else if (el instanceof SQLColumnDefinition) {
                    SQLColumnDefinition columnDefinition = (SQLColumnDefinition) el;
                    columnDefinition.setCharsetExpr(null);
                    SQLDataType sqlDataType = columnDefinition.getDataType();

                    // h2 不支持charset & character set
                    if (sqlDataType instanceof SQLCharacterDataType) {
                        SQLCharacterDataType characterDataType = (SQLCharacterDataType) sqlDataType;
                        SQLDataTypeImpl newDataType = new SQLDataTypeImpl();
                        characterDataType.cloneTo(newDataType);
                        columnDefinition.setDataType(newDataType);
                    } else if (sqlDataType instanceof SQLDataTypeImpl) {
                        SQLDataTypeImpl dataTypeImpl = (SQLDataTypeImpl) sqlDataType;
                        if ("set".equalsIgnoreCase(dataTypeImpl.getName()) ||
                            "enum".equalsIgnoreCase(dataTypeImpl.getName())) {
                            dataTypeImpl.setName("text");
                            dataTypeImpl.getArguments().clear();
                        }
                    }
                }
            }
            return sqlStatement.toString();
        } else if (sqlStatement instanceof MySqlLockTableStatement) {
            return "";
        } else if (sqlStatement instanceof MySqlUnlockTablesStatement) {
            return "";
        } else {
            return sql;
        }
    }

    private static String removeHints(String sql) {
        String newSql = sql.replaceAll("/\\*![0-9]([\\s\\S]*?)\\*/", "");
        while (StringUtils.endsWith(newSql.trim(), ";")) {
            newSql = StringUtils.substringBeforeLast(newSql, ";");
        }
        if (!StringUtils.equals(sql, newSql)) {
            if (log.isDebugEnabled()) {
                log.debug("before remove hints , sql is : " + sql);
                log.debug("after  remove hints , sql is : " + newSql);
            }
        }
        return newSql;
    }

    // -----------------------------------------------------------------------------------------------------------------
    // ------------------------------------------------JDBC METHODS-----------------------------------------------------
    // -----------------------------------------------------------------------------------------------------------------

    @SneakyThrows
    public static List<String> showDatabases(Connection conn) {
        List<String> tables = new ArrayList<>();
        ResultSet resultSet = executeQuery("show databases", conn);
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

    @SneakyThrows
    public static List<String> showTables(Connection conn, String dbName) {
        List<String> tables = new ArrayList<>();

        String sql = null;
        if (StringUtils.isBlank(dbName)) {
            sql = "show tables";
        } else {
            sql = "show tables from " + dbName;
        }
        ResultSet resultSet = executeQuery(sql, conn);
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

    public static ResultSet executeQuery(String sql, Connection c) {
        Statement ps = createStatement(c);
        ResultSet rs = null;
        try {
            rs = ps.executeQuery(sql);
        } catch (SQLException e) {
            String errorMs = "[Execute statement query] failed! sql is: " + sql;
            log.error(errorMs, e);
            Assert.fail(errorMs + " \n" + e);
        }
        return rs;
    }

    public static void executeUpdate(Connection conn, String sql) {
        Statement stmt = null;
        try {
            stmt = conn.createStatement();
            stmt.execute(sql);
        } catch (SQLException e) {
            Assert.fail(e.getMessage());
        } finally {
            close(stmt);
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

    public static void execUpdate(DataSource dataSource, String sql) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(sql);
            }
        }
    }

    public static List<List<Serializable>> getStringData(DataSource dataSource, String sql, int length)
        throws Exception {
        List<List<Serializable>> results = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement(ResultSet.TYPE_FORWARD_ONLY,
                ResultSet.CONCUR_READ_ONLY)) {
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        List<Serializable> valListInner = new ArrayList<>();
                        for (int i = 1; i <= length; i++) {
                            valListInner.add(rs.getString(i));
                        }
                        results.add(valListInner);
                    }
                }
            }
        }
        return results;
    }

}
