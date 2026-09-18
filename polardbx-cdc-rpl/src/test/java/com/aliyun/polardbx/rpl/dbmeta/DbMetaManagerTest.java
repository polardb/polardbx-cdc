/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.dbmeta;

import com.alibaba.polardbx.core.cj.NativeSession;
import com.alibaba.polardbx.core.cj.ServerVersion;
import com.alibaba.polardbx.core.cj.jdbc.DatabaseMetaDataUsingInfoSchema;
import com.alibaba.polardbx.core.cj.jdbc.JdbcConnection;
import com.alibaba.polardbx.core.cj.jdbc.JdbcPropertySetImpl;
import com.alibaba.polardbx.core.cj.jdbc.JdbcStatement;
import com.alibaba.polardbx.core.cj.jdbc.result.ResultSetInternalMethods;
import com.alibaba.polardbx.druid.sql.ast.statement.SQLCreateTableStatement;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.common.DataSourceUtil;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * @author shicai.xsc 2021/3/25 16:48
 * @since 5.0.0.0
 */

public class DbMetaManagerTest extends BaseTest {

    @Test
    public void testRealDriverPreservesSpecialMetadataNames() throws Exception {
        String[][] names = {
            {"ordinary", "ordinary"},
            {"`gxw_test-minus`", "`gxw\\_test-minus`"},
            {"t_1%", "t\\_1\\%"},
            {"t.with.dot", "t.with.dot"},
            {"t'quote\\slash", "t'quote\\\\slash"},
            {"表``名", "表``名"}
        };
        for (String[] name : names) {
            JdbcConnection connection = Mockito.mock(JdbcConnection.class);
            NativeSession session = Mockito.mock(NativeSession.class, Mockito.RETURNS_DEEP_STUBS);
            JdbcPropertySetImpl properties = new JdbcPropertySetImpl();
            Properties config = new Properties();
            // Exercise metadata properties here; URL/legacy-property normalization belongs to the compat driver.
            for (String key : Arrays.asList("useInformationSchema", "pedantic", "tinyInt1isBit", "yearIsDateType")) {
                config.setProperty(key, DataSourceUtil.DEFAULT_MYSQL_CONNECTION_PROPERTIES.get(key));
            }
            properties.initializeProperties(config);
            when(connection.getPropertySet()).thenReturn(properties);
            when(connection.getSession()).thenReturn(session);
            when(connection.getServerVersion()).thenReturn(ServerVersion.parseVersion("8.0.36"));
            when(session.getIdentifierQuoteString()).thenReturn("`");
            PreparedStatement statement = Mockito.mock(PreparedStatement.class,
                Mockito.withSettings().extraInterfaces(JdbcStatement.class));
            when(connection.clientPrepareStatement(anyString(), anyInt(), anyInt())).thenReturn(statement);
            ResultSetInternalMethods result = Mockito.mock(ResultSetInternalMethods.class,
                Mockito.RETURNS_DEEP_STUBS);
            when(statement.executeQuery()).thenReturn(result);
            DatabaseMetaData metadata = MetadataFactory.create(connection);
            Assert.assertTrue(metadata instanceof DatabaseMetaDataUsingInfoSchema);

            // Actual driver query construction and bindings; only transport/results are mocked.
            String schema = "`db_1%`";
            try (ResultSet ignored = DbMetaManager.getMetadataColumns(metadata, schema, name[0])) {
                Mockito.verify(statement).setString(1, schema);
                Mockito.verify(statement).setString(2, name[1]);
            }
            Mockito.clearInvocations(statement);
            try (ResultSet ignored = DbMetaManager.getMetadataPrimaryKeys(metadata, schema, name[0])) {
                Mockito.verify(statement).setString(1, schema);
                Mockito.verify(statement).setString(2, name[0]);
            }
        }
    }

    private static class MetadataFactory extends com.alibaba.polardbx.core.cj.jdbc.DatabaseMetaData {
        private MetadataFactory(JdbcConnection connection) {
            super(connection, "unused", null);
        }

        static DatabaseMetaData create(JdbcConnection connection) throws SQLException {
            return getInstance(connection, "unused", true, null);
        }
    }

    @Test
    public void testMetadataUsesExactCatalogAndEscapedTablePattern() throws Exception {
        DatabaseMetaData metadata = Mockito.mock(DatabaseMetaData.class);
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        DbMetaManager.getMetadataColumns(metadata, "db_1%", "t_1%`\\");
        DbMetaManager.getMetadataPrimaryKeys(metadata, "db_1%", "t_1%`\\");
        Mockito.verify(metadata).getColumns("db_1%", null, "t\\_1\\%`\\\\", null);
        Mockito.verify(metadata).getPrimaryKeys("db_1%", null, "t_1%`\\");
    }

    @Test
    public void testInformationSchemaQueriesBindLiteralNames() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        PreparedStatement basic = Mockito.mock(PreparedStatement.class);
        PreparedStatement charset = Mockito.mock(PreparedStatement.class);
        when(connection.prepareStatement(
            "SELECT * FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")).thenReturn(basic);
        when(connection.prepareStatement(
            "SELECT COLUMN_NAME, CHARACTER_SET_NAME, COLLATION_NAME FROM INFORMATION_SCHEMA.COLUMNS"
                + " WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?")).thenReturn(charset);
        ResultSet empty = Mockito.mock(ResultSet.class);
        when(basic.executeQuery()).thenReturn(empty);
        when(charset.executeQuery()).thenReturn(empty);
        when(connection.createStatement()).thenReturn(Mockito.mock(Statement.class));
        DatabaseMetaData metadata = Mockito.mock(DatabaseMetaData.class);
        when(connection.getMetaData()).thenReturn(metadata);
        when(metadata.getDatabaseProductName()).thenReturn("H2");
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        when(metadata.getColumns(any(), any(), any(), any())).thenReturn(empty);
        when(metadata.getPrimaryKeys(any(), any(), any())).thenReturn(empty);
        String schema = "db'\\%", table = "`table'\\_`";
        try {
            DbMetaManager.getTableInfo(dataSource, schema, table, HostType.RDS, false);
            Assert.fail("empty metadata must still be rejected");
        } catch (SQLException expected) {
            Assert.assertTrue(expected.getMessage().contains("empty JDBC column metadata"));
        }
        for (PreparedStatement statement : Arrays.asList(basic, charset)) {
            Mockito.verify(statement).setString(1, schema);
            Mockito.verify(statement).setString(2, table);
            Mockito.verify(statement).executeQuery();
            Mockito.verify(statement).close();
        }
    }

    @Test
    public void testFullMetadataWithQuoteBackslashAndWildcardNames() throws Exception {
        org.h2.jdbcx.JdbcDataSource dataSource = new org.h2.jdbcx.JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:rpl_metadata_literal_names");
        String schema = "DB'\\_%`", table = "T'\\_%`";
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA \"" + schema + "\"");
            statement.execute("CREATE TABLE \"" + schema + "\".\"" + table
                + "\"(ID BIGINT PRIMARY KEY, NOTE VARCHAR(64))");
            TableInfo info = DbMetaManager.getTableInfo(dataSource, schema, table, HostType.RDS, false);
            Assert.assertEquals(2, info.getColumns().size());
            Assert.assertEquals(Collections.singletonList("id"), info.getPks());
        }
    }

    @Test
    public void testMetadataPreservesLiteralQuoteBoundaries() throws Exception {
        DatabaseMetaData metadata = Mockito.mock(DatabaseMetaData.class);
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        String schema = "`db_1%`";
        String table = "`gxw_test-minus`";
        DbMetaManager.getMetadataColumns(metadata, schema, table);
        DbMetaManager.getMetadataPrimaryKeys(metadata, schema, table);
        Mockito.verify(metadata).getColumns(schema, null, "`gxw\\_test-minus`", null);
        Mockito.verify(metadata).getPrimaryKeys(schema, null, table);
    }

    @Test
    public void testMetadataUsesSchemaWhenDriverSupportsSchemas() throws Exception {
        DatabaseMetaData metadata = Mockito.mock(DatabaseMetaData.class);
        when(metadata.supportsSchemasInTableDefinitions()).thenReturn(true);
        when(metadata.getSearchStringEscape()).thenReturn("\\");
        DbMetaManager.getMetadataColumns(metadata, "db_1%", "t_1%");
        DbMetaManager.getMetadataPrimaryKeys(metadata, "db_1%", "t_1%");
        Mockito.verify(metadata).getColumns(null, "db\\_1\\%", "t\\_1\\%", null);
        Mockito.verify(metadata).getPrimaryKeys(null, "db_1%", "t_1%");
    }

    @Test
    public void testRejectEmptyOrIncompleteColumnMetadata() throws Exception {
        TableInfo empty = new TableInfo("db1", "t1");
        assertInvalidColumnMetadata(empty, "empty JDBC column metadata");
        TableInfo incomplete = tableInfoWithColumns("id");
        incomplete.setPks(Arrays.asList("id", "missing"));
        assertInvalidColumnMetadata(incomplete, "key column missing");
        incomplete.setPks(Collections.singletonList("id"));
        DbMetaManager.validateTableColumns(incomplete);
        incomplete.setPks(Collections.emptyList());
        DbMetaManager.validateTableColumns(incomplete);
    }

    @Test
    public void testH2MetadataDoesNotMatchOtherSchemasOrWildcardTableNames() throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:h2:mem:rpl_metadata_scope");
            Statement statement = conn.createStatement()) {
            statement.execute("CREATE SCHEMA DB_A");
            statement.execute("CREATE SCHEMA DBXA");
            statement.execute("CREATE TABLE DB_A.T_1(ID BIGINT PRIMARY KEY, NOTE VARCHAR(64))");
            statement.execute("CREATE TABLE DB_A.TX1(WRONG_COLUMN INT)");
            statement.execute("CREATE TABLE DBXA.T_1(WRONG_KEY INT PRIMARY KEY)");
            DatabaseMetaData metadata = conn.getMetaData();
            List<String> columns = new ArrayList<>();
            try (ResultSet rs = DbMetaManager.getMetadataColumns(metadata, "DB_A", "T_1")) {
                while (rs.next()) {
                    Assert.assertEquals("DB_A", rs.getString("TABLE_SCHEM"));
                    columns.add(rs.getString("COLUMN_NAME"));
                }
            }
            Assert.assertEquals(Arrays.asList("ID", "NOTE"), columns);
            try (ResultSet rs = DbMetaManager.getMetadataPrimaryKeys(metadata, "DB_A", "T_1")) {
                Assert.assertTrue(rs.next());
                Assert.assertEquals("ID", rs.getString("COLUMN_NAME"));
                Assert.assertFalse(rs.next());
            }
        }
    }

    private void assertInvalidColumnMetadata(TableInfo tableInfo, String message) throws Exception {
        try {
            DbMetaManager.validateTableColumns(tableInfo);
            Assert.fail("invalid metadata must not be cached");
        } catch (SQLException expected) {
            Assert.assertTrue(expected.getMessage(), expected.getMessage().contains(message));
        }
    }

    @Test
    public void testLoadExternalizedColumnInfoMarksColumnsFromShowCreateTable() throws Exception {
        String createTable = "CREATE TABLE `t1` (\n"
            + "  `id` bigint NOT NULL,\n"
            + "  `Payload` longtext EXTERNALIZE,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE=InnoDB";
        TableInfo tableInfo = tableInfoWithColumns("id", "payload");

        invokeLoadExternalizedColumnInfo(showCreateDataSource(createTable), "db1", "t1", tableInfo);

        Assert.assertEquals(createTable, tableInfo.getCreateTable());
        Assert.assertFalse(tableInfo.getColumnInfo("id").isExternalized());
        Assert.assertTrue(tableInfo.getColumnInfo("payload").isExternalized());
        Assert.assertEquals(Collections.singleton("payload"), tableInfo.getExternalizedColumnNames());
    }

    @Test
    public void testLoadExternalizedColumnInfoDoesNotMarkOrdinaryColumns() throws Exception {
        String createTable = "CREATE TABLE `t1` (\n"
            + "  `id` bigint NOT NULL,\n"
            + "  `payload` longtext,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE=InnoDB";
        TableInfo tableInfo = tableInfoWithColumns("id", "payload");

        invokeLoadExternalizedColumnInfo(showCreateDataSource(createTable), "db1", "t1", tableInfo);

        Assert.assertEquals(createTable, tableInfo.getCreateTable());
        Assert.assertTrue(tableInfo.getExternalizedColumnNames().isEmpty());
    }

    @Test
    public void testLoadExternalizedColumnInfoFailsClosedWhenShowCreateIsEmpty() throws Exception {
        TableInfo tableInfo = tableInfoWithColumns("id", "payload");

        SQLException error = assertLoadExternalizedColumnInfoFails(
            showCreateDataSource(null), tableInfo);

        Assert.assertTrue(error.getMessage().contains("empty SHOW CREATE TABLE result"));
        Assert.assertNull(tableInfo.getCreateTable());
    }

    @Test
    public void testLoadExternalizedColumnInfoWrapsShowCreateFailure() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        SQLException queryError = new SQLException("show create failed");
        when(dataSource.getConnection()).thenThrow(queryError);

        SQLException error = assertLoadExternalizedColumnInfoFails(
            dataSource, tableInfoWithColumns("id", "payload"));

        Assert.assertTrue(error.getMessage().contains("failed to load SHOW CREATE TABLE"));
        Assert.assertSame(queryError, error.getCause());
    }

    @Test
    public void testLoadExternalizedColumnInfoFailsClosedWhenCreateTableCannotBeParsed() throws Exception {
        String malformedCreateTable = "CREATE TABLE `t1` (`payload` longtext EXTERNALIZE";

        SQLException error = assertLoadExternalizedColumnInfoFails(
            showCreateDataSource(malformedCreateTable), tableInfoWithColumns("payload"));

        Assert.assertTrue(error.getMessage().contains("failed to parse SHOW CREATE TABLE column definitions"));
        Assert.assertNotNull(error.getCause());
    }

    @Test
    public void testLoadExternalizedColumnInfoFailsClosedWhenJdbcColumnIsMissing() throws Exception {
        String createTable = "CREATE TABLE `t1` (\n"
            + "  `id` bigint NOT NULL,\n"
            + "  `payload` longtext EXTERNALIZE,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ") ENGINE=InnoDB";

        SQLException error = assertLoadExternalizedColumnInfoFails(
            showCreateDataSource(createTable), tableInfoWithColumns("id"));

        Assert.assertTrue(error.getMessage().contains("externalized column payload"));
        Assert.assertTrue(error.getMessage().contains("absent in JDBC metadata"));
    }

    @Test
    public void testParseCreateTableColumnDefinitionsIgnoresMalformedLegacySuffixForOrdinaryTable() {
        String createTable = "CREATE TABLE `gxw_test-minus` (\n"
            + "  `col``backtick` int NOT NULL,\n"
            + "  `c2` int DEFAULT NULL,\n"
            + "  `externalize` varchar(64) DEFAULT 'EXTERNALIZE()',\n"
            + "  PRIMARY KEY (`col``backtick`)\n"
            + ") ENGINE=InnoDB dbpartition by hash(`col`backtick`)";

        SQLCreateTableStatement statement = DbMetaManager.parseCreateTableColumnDefinitions(createTable);
        assertFalse(statement.getColumnDefinitions().stream().anyMatch(column -> column.isExternalize()));
    }

    @Test
    public void testParseCreateTableColumnDefinitionsCutsMalformedSuffixForExternalizedTable() {
        String columnDefinitionSql = "CREATE TABLE `中文外列表` (\n"
            + "  `id` bigint NOT NULL,\n"
            + "  `正文` longtext EXTERNALIZE,\n"
            + "  PRIMARY KEY (`id`)\n"
            + ")";
        String createTable = columnDefinitionSql
            + " ENGINE=InnoDB dbpartition by hash(`col`backtick`)";

        SQLCreateTableStatement statement = DbMetaManager.parseCreateTableColumnDefinitions(createTable);
        assertEquals(1, statement.getColumnDefinitions().stream().filter(column -> column.isExternalize()).count());
        assertEquals("`正文`", statement.getColumnDefinitions().stream()
            .filter(column -> column.isExternalize()).findFirst().get().getColumnName());
    }

    @Test
    public void testGetTableUksMultipleUniqueKeyGroups() throws Exception {
        // 准备测试数据
        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        ukGroups.put("key1", new ArrayList<>());
        ukGroups.put("key2", new ArrayList<>());
        KeyColumnInfo column11 = new KeyColumnInfo("tbName", "key1", "columnName1", 0, 0);
        KeyColumnInfo column12 = new KeyColumnInfo("tbName", "key1", "columnName2", 0, 1);
        KeyColumnInfo column21 = new KeyColumnInfo("tbName", "key2", "columnName1", 0, 0);
        KeyColumnInfo column22 = new KeyColumnInfo("tbName", "key2", "columnName3", 0, 0);
        ukGroups.get("key1").add(column11);
        ukGroups.get("key1").add(column12);
        ukGroups.get("key2").add(column21);
        ukGroups.get("key2").add(column22);

        // Mock 数据库层
        DataSource mockDataSource = Mockito.mock(DataSource.class);
        Connection mockConnection = Mockito.mock(Connection.class);
        PreparedStatement mockStatement = Mockito.mock(PreparedStatement.class);
        ResultSet mockResultSet = Mockito.mock(ResultSet.class);

        when(mockDataSource.getConnection()).thenReturn(mockConnection);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockStatement);
        when(mockStatement.executeQuery()).thenReturn(mockResultSet);

        // 使用 mockStatic 来 mock getTableUkGroups 方法
        try (MockedStatic<DbMetaManager> dbMetaManager = Mockito.mockStatic(DbMetaManager.class)) {
            // Mock getTableUkGroups 方法
            dbMetaManager.when(
                    () -> DbMetaManager.getTableUkGroups(any(), anyString(), anyString(), any(HostType.class)))
                .thenReturn(ukGroups);

            // 让 getTableUks 真实执行
            dbMetaManager.when(() -> DbMetaManager.getTableUks(any(), anyString(), anyString(), any(HostType.class)))
                .thenCallRealMethod();

            // 执行测试
            List<String> actualUks = DbMetaManager.getTableUks(mockDataSource, "SCHEMA", "TBNAME", HostType.RDS);

            // 验证结果
            List<String> expectedUks = new ArrayList<>();
            expectedUks.add("columnname1");
            expectedUks.add("columnname2");
            expectedUks.add("columnname3");
            assertEquals(3, actualUks.size());
            for (String uk : expectedUks) {
                assertTrue(actualUks.contains(uk.toLowerCase()));
            }
        }
    }

    @Test
    public void metaTest() throws Throwable {
        String tbName = "rpl_task";
        DataSource dataSource = getGmsDataSource();
        // The H2 database is named polardbx_meta_db, but the fixture tables live in PUBLIC.
        try (Connection connection = dataSource.getConnection()) {
            TableInfo tableInfo = DbMetaManager.getTableInfo(dataSource, connection.getSchema(), tbName,
                HostType.RDS, false);
            Assert.assertFalse(tableInfo.getColumns().isEmpty());
            Assert.assertEquals(Collections.singletonList("id"), tableInfo.getPks());
        }
    }

    @Test
    public void testBuildTableBasicInfo() throws SQLException {
        TableInfo tableInfo = new TableInfo("d1", "t1");
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        ResultSetMetaData metaData = Mockito.mock(ResultSetMetaData.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getString(Mockito.anyInt())).thenReturn("InnoDB");
        when(resultSet.getMetaData()).thenReturn(metaData);
        when(metaData.getColumnCount()).thenReturn(1);
        when(metaData.getColumnName(Mockito.anyInt())).thenReturn("ENGINE");

        DbMetaManager.buildTableBasicInfo(dataSource, "d1", "t1", tableInfo);
        Assert.assertEquals("InnoDB", tableInfo.getEngine());
    }

    @Test
    public void testGetTableUkGroupsFromGlobalIndex() throws SQLException {
        // 准备测试数据
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        // 设置mock行为
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // 模拟查询结果 - 有两个全局索引，每个索引包含多个列
        when(resultSet.next())
            .thenReturn(true)  // 第一行
            .thenReturn(true)  // 第二行
            .thenReturn(false); // 结束

        // 第一行数据
        when(resultSet.getInt("NON_UNIQUE")).thenReturn(0); // 非唯一索引
        when(resultSet.getString("KEY_NAME")).thenReturn("uk_index1");
        when(resultSet.getString("INDEX_NAMES")).thenReturn("col1, col2");

        // 为了模拟第二次调用返回不同值，需要使用Answer
        when(resultSet.getInt("NON_UNIQUE"))
            .thenReturn(0) // 第一次调用
            .thenReturn(0); // 第二次调用

        when(resultSet.getString("KEY_NAME"))
            .thenReturn("uk_index1") // 第一次调用
            .thenReturn("uk_index2"); // 第二次调用

        when(resultSet.getString("INDEX_NAMES"))
            .thenReturn("col1, col2") // 第一次调用
            .thenReturn("col3"); // 第二次调用

        // 执行方法
        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        DbMetaManager.getTableUkGroupsFromGlobalIndex(dataSource, "test_schema", "test_table", ukGroups);

        // 验证结果
        Assert.assertEquals(2, ukGroups.size()); // 应该有两个唯一键组
        Assert.assertTrue(ukGroups.containsKey("uk_index1"));
        Assert.assertTrue(ukGroups.containsKey("uk_index2"));

        // 验证第一个唯一键组的内容
        List<KeyColumnInfo> index1Columns = ukGroups.get("uk_index1");
        Assert.assertEquals(2, index1Columns.size());
        Assert.assertEquals("col1", index1Columns.get(0).getColumnName());
        Assert.assertEquals("col2", index1Columns.get(1).getColumnName());

        // 验证第二个唯一键组的内容
        List<KeyColumnInfo> index2Columns = ukGroups.get("uk_index2");
        Assert.assertEquals(1, index2Columns.size());
        Assert.assertEquals("col3", index2Columns.get(0).getColumnName());

        // 验证PreparedStatement被正确调用
        Mockito.verify(connection).prepareStatement("SHOW GLOBAL INDEX FROM `test_schema`.`test_table`");
        Mockito.verify(preparedStatement).executeQuery();
    }

    @Test(expected = SQLException.class)
    public void testGetTableUkGroupsFromGlobalIndexWithException() throws SQLException {
        // 测试异常情况
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery())
            .thenThrow(new SQLException("Test exception"));

        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        DbMetaManager.getTableUkGroupsFromGlobalIndex(dataSource, "test_schema", "test_table", ukGroups);
    }

    @Test
    public void testGetTableUkGroupsFromGlobalIndexWithNonUniqueEqualsOne() throws SQLException {
        // 测试当NON_UNIQUE=1时（非唯一索引），应该被跳过
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // 模拟返回非唯一索引数据（NON_UNIQUE=1）
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getInt("NON_UNIQUE")).thenReturn(1); // 非唯一索引，应该被跳过
        when(resultSet.getString("KEY_NAME")).thenReturn("non_unique_index");
        when(resultSet.getString("INDEX_NAMES")).thenReturn("col1");

        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        DbMetaManager.getTableUkGroupsFromGlobalIndex(dataSource, "test_schema", "test_table", ukGroups);

        // 验证非唯一索引被跳过，所以ukGroups应该是空的
        Assert.assertEquals(0, ukGroups.size());
    }

    @Test
    public void testGetTableUkGroupsFromGlobalIndexWithPrimaryKey() throws SQLException {
        // 测试当KEY_NAME是PRIMARY时，应该被跳过
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // 模拟返回主键数据
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getInt("NON_UNIQUE")).thenReturn(0); // 唯一索引
        when(resultSet.getString("KEY_NAME")).thenReturn("PRIMARY"); // 主键，应该被跳过
        when(resultSet.getString("INDEX_NAMES")).thenReturn("id");

        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        DbMetaManager.getTableUkGroupsFromGlobalIndex(dataSource, "test_schema", "test_table", ukGroups);

        // 验证主键被跳过，所以ukGroups应该是空的
        Assert.assertEquals(0, ukGroups.size());
    }

    @Test
    public void testGetTableUkGroupsFromGlobalIndexEmptyResultSet() throws SQLException {
        // 测试空结果集的情况
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(false); // 立即返回false，表示没有结果

        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        DbMetaManager.getTableUkGroupsFromGlobalIndex(dataSource, "test_schema", "test_table", ukGroups);

        // 验证ukGroups是空的
        Assert.assertEquals(0, ukGroups.size());
        Mockito.verify(connection).prepareStatement("SHOW GLOBAL INDEX FROM `test_schema`.`test_table`");
        Mockito.verify(preparedStatement).executeQuery();
    }

    @Test
    public void testGetTableUkGroupsFromGlobalIndexCapturesPrefixLength() throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getInt("NON_UNIQUE")).thenReturn(0);
        when(resultSet.getString("KEY_NAME")).thenReturn("uk_name");
        when(resultSet.getString("INDEX_NAMES")).thenReturn("`name`(16)");

        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        DbMetaManager.getTableUkGroupsFromGlobalIndex(dataSource, "test_schema", "test_table", ukGroups);

        KeyColumnInfo keyColumnInfo = ukGroups.get("uk_name").get(0);
        Assert.assertEquals("name", keyColumnInfo.getColumnName());
        Assert.assertEquals(Integer.valueOf(16), keyColumnInfo.getSubPart());
    }

    /**
     * 表达式唯一索引不再阻断元数据加载，而是保留占位信息供 V3 判定串行回退。
     */
    @Test
    public void testGetTableUkGroupsFromLocalIndexExpressionIndexIsMarkedUnsupported() throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // 模拟：唯一索引，非PRIMARY，但 Column_name 为 null（表达式索引）
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getInt("Non_unique")).thenReturn(0);
        when(resultSet.getString("Key_name")).thenReturn("uk_expr_idx");
        when(resultSet.getInt("Seq_in_index")).thenReturn(1);
        when(resultSet.getString("Column_name")).thenReturn(null);

        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        DbMetaManager.getTableUkGroupsFromLocalIndex(dataSource, "test_schema", "test_table", ukGroups);

        Assert.assertEquals(1, ukGroups.get("uk_expr_idx").size());
        Assert.assertTrue(ukGroups.get("uk_expr_idx").get(0).isExpression());
        Assert.assertNull(ukGroups.get("uk_expr_idx").get(0).getColumnName());
    }

    /**
     * 测试正常唯一索引（Column_name 不为 null）不会抛异常
     */
    @Test
    public void testGetTableUkGroupsFromLocalIndex_normalIndex_noException() throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);

        // 模拟：正常的唯一索引
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getInt("Non_unique")).thenReturn(0);
        when(resultSet.getString("Key_name")).thenReturn("uk_normal");
        when(resultSet.getInt("Seq_in_index")).thenReturn(1);
        when(resultSet.getString("Column_name")).thenReturn("col1");

        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        DbMetaManager.getTableUkGroupsFromLocalIndex(dataSource, "test_schema", "test_table", ukGroups);

        // 验证正常索引被加入 ukGroups
        Assert.assertEquals(1, ukGroups.size());
        Assert.assertTrue(ukGroups.containsKey("uk_normal"));
        Assert.assertEquals("col1", ukGroups.get("uk_normal").get(0).getColumnName());
    }

    @Test
    public void testGetTableUkGroupsFromLocalIndexCapturesPrefixLength() throws SQLException {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement preparedStatement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);

        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(preparedStatement);
        when(preparedStatement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true).thenReturn(false);
        when(resultSet.getInt("Non_unique")).thenReturn(0);
        when(resultSet.getString("Key_name")).thenReturn("uk_name");
        when(resultSet.getInt("Seq_in_index")).thenReturn(1);
        when(resultSet.getString("Column_name")).thenReturn("name");
        when(resultSet.getObject("Sub_part")).thenReturn(16);

        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        DbMetaManager.getTableUkGroupsFromLocalIndex(dataSource, "test_schema", "test_table", ukGroups);

        KeyColumnInfo keyColumnInfo = ukGroups.get("uk_name").get(0);
        Assert.assertEquals("name", keyColumnInfo.getColumnName());
        Assert.assertEquals(Integer.valueOf(16), keyColumnInfo.getSubPart());
    }

    @Test
    public void testParallelApplyKeyCompatibilityAcceptsExactTypes() {
        TableInfo tableInfo = new TableInfo("db1", "t1");
        tableInfo.setPks(Collections.singletonList("id"));
        tableInfo.setUks(Collections.singletonList("uk_bin"));
        tableInfo.setColumns(Arrays.asList(
            new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20),
            new ColumnInfo("uk_bin", Types.VARBINARY, null, false, false, "VARBINARY", 16)));
        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        ukGroups.put("uk_bin", Collections.singletonList(
            new KeyColumnInfo("t1", "uk_bin", "uk_bin", 0, 1)));

        DbMetaManager.evaluateParallelApplyKeyCompatibility(tableInfo, ukGroups);

        Assert.assertFalse(tableInfo.isParallelApplyKeyUnsupported());
        Assert.assertNull(tableInfo.getParallelApplyKeyIncompatibleReason());
    }

    @Test
    public void testParallelApplyKeyCompatibilityAcceptsNormalizableCharacterUk() {
        TableInfo tableInfo = new TableInfo("db1", "t1");
        tableInfo.setPks(Collections.singletonList("id"));
        tableInfo.setUks(Collections.singletonList("name"));
        tableInfo.setColumns(Arrays.asList(
            new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20),
            new ColumnInfo("name", Types.VARCHAR, null, false, false, "VARCHAR", 32,
                "utf8mb4", "utf8mb4_general_ci")));
        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        ukGroups.put("uk_name", Collections.singletonList(
            new KeyColumnInfo("t1", "uk_name", "name", 0, 1)));

        DbMetaManager.evaluateParallelApplyKeyCompatibility(tableInfo, ukGroups);

        Assert.assertFalse(tableInfo.isParallelApplyKeyUnsupported());
        Assert.assertNull(tableInfo.getParallelApplyKeyIncompatibleReason());
    }

    @Test
    public void testParallelApplyKeyCompatibilityRejectsPrefixUk() {
        TableInfo tableInfo = new TableInfo("db1", "t1");
        tableInfo.setPks(Collections.singletonList("id"));
        tableInfo.setUks(Collections.singletonList("name"));
        tableInfo.setColumns(Arrays.asList(
            new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20),
            new ColumnInfo("name", Types.VARBINARY, null, false, false, "VARBINARY", 32)));
        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        ukGroups.put("uk_name", Collections.singletonList(
            new KeyColumnInfo("t1", "uk_name", "name", 0, 1, 16, false)));

        DbMetaManager.evaluateParallelApplyKeyCompatibility(tableInfo, ukGroups);

        Assert.assertTrue(tableInfo.isParallelApplyKeyUnsupported());
        Assert.assertTrue(tableInfo.getParallelApplyKeyIncompatibleReason().contains("name(16)"));
    }

    @Test
    public void testParallelApplyKeyCompatibilityRejectsExpressionUk() {
        TableInfo tableInfo = new TableInfo("db1", "t1");
        tableInfo.setPks(Collections.singletonList("id"));
        tableInfo.setColumns(Collections.singletonList(
            new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20)));
        HashMap<String, List<KeyColumnInfo>> ukGroups = new HashMap<>();
        ukGroups.put("uk_expr", Collections.singletonList(
            new KeyColumnInfo("t1", "uk_expr", null, 0, 1, null, true)));

        DbMetaManager.evaluateParallelApplyKeyCompatibility(tableInfo, ukGroups);

        Assert.assertTrue(tableInfo.isParallelApplyKeyUnsupported());
        Assert.assertTrue(tableInfo.getParallelApplyKeyIncompatibleReason().contains("expression"));
    }

    @Test
    public void testParallelApplyKeyCompatibilityRejectsFloatingPointAndGeneratedKeys() {
        TableInfo floatingPointTable = new TableInfo("db1", "float_key");
        floatingPointTable.setPks(Collections.singletonList("id"));
        floatingPointTable.setColumns(Collections.singletonList(
            new ColumnInfo("id", Types.DOUBLE, null, false, false, "DOUBLE", 22)));

        DbMetaManager.evaluateParallelApplyKeyCompatibility(floatingPointTable, Collections.emptyMap());

        Assert.assertTrue(floatingPointTable.isParallelApplyKeyUnsupported());
        Assert.assertTrue(floatingPointTable.getParallelApplyKeyIncompatibleReason().contains("DOUBLE"));

        TableInfo generatedUkTable = new TableInfo("db1", "generated_key");
        generatedUkTable.setPks(Collections.singletonList("id"));
        generatedUkTable.setUks(Collections.singletonList("generated_uk"));
        generatedUkTable.setColumns(Arrays.asList(
            new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20),
            new ColumnInfo("generated_uk", Types.BIGINT, null, false, true, "BIGINT", 20)));
        HashMap<String, List<KeyColumnInfo>> generatedUkGroups = new HashMap<>();
        generatedUkGroups.put("uk_generated", Collections.singletonList(
            new KeyColumnInfo("generated_key", "uk_generated", "generated_uk", 0, 1)));

        DbMetaManager.evaluateParallelApplyKeyCompatibility(generatedUkTable, generatedUkGroups);

        Assert.assertTrue(generatedUkTable.isParallelApplyKeyUnsupported());
        Assert.assertTrue(generatedUkTable.getParallelApplyKeyIncompatibleReason().contains("generated"));
    }

    @Test
    public void testParallelApplyKeyCompatibilityRejectsSpatialTypeMappedToBinary() {
        TableInfo tableInfo = new TableInfo("db1", "spatial_key");
        tableInfo.setPks(Collections.singletonList("shape"));
        tableInfo.setColumns(Collections.singletonList(
            new ColumnInfo("shape", Types.BINARY, null, false, false, "POINT", 0)));

        DbMetaManager.evaluateParallelApplyKeyCompatibility(tableInfo, Collections.emptyMap());

        Assert.assertTrue(tableInfo.isParallelApplyKeyUnsupported());
        Assert.assertTrue(tableInfo.getParallelApplyKeyIncompatibleReason().contains("POINT"));
    }

    /**
     * 测试 getTableColumnInfos 中 generated column 修正逻辑：
     * JDBC 驱动对 DEFAULT_GENERATED 列也返回 IS_GENERATEDCOLUMN=YES，
     * 但只有 VIRTUAL/STORED GENERATED 才是真正的生成列。
     * 此测试验证 DEFAULT_GENERATED 被修正为 generated=false。
     */
    @Test
    public void testGetTableInfo_generatedColumnCorrection_defaultGenerated() throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);

        // 3个连接：buildTableBasicInfo, getTableColumnInfos, getTablePks
        Connection conn1 = Mockito.mock(Connection.class);
        Connection conn2 = Mockito.mock(Connection.class);
        Connection conn3 = Mockito.mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(conn1).thenReturn(conn2).thenReturn(conn3);

        // --- conn1: buildTableBasicInfo ---
        PreparedStatement stmt1 = Mockito.mock(PreparedStatement.class);
        ResultSet infoSchemaRs = Mockito.mock(ResultSet.class);
        ResultSetMetaData infoSchemaMeta = Mockito.mock(ResultSetMetaData.class);
        when(conn1.prepareStatement(anyString())).thenReturn(stmt1);
        when(stmt1.executeQuery()).thenReturn(infoSchemaRs);
        when(infoSchemaRs.next()).thenReturn(true).thenReturn(false);
        when(infoSchemaRs.getMetaData()).thenReturn(infoSchemaMeta);
        when(infoSchemaMeta.getColumnCount()).thenReturn(1);
        when(infoSchemaMeta.getColumnName(1)).thenReturn("ENGINE");
        when(infoSchemaRs.getString(1)).thenReturn("InnoDB");

        // --- conn2: getTableColumnInfos ---
        Statement stmt2 = Mockito.mock(Statement.class);
        DatabaseMetaData dbMeta2 = Mockito.mock(DatabaseMetaData.class);
        when(conn2.createStatement()).thenReturn(stmt2);
        when(conn2.getMetaData()).thenReturn(dbMeta2);
        when(dbMeta2.getDatabaseProductName()).thenReturn("MySQL");
        when(dbMeta2.storesUpperCaseIdentifiers()).thenReturn(false);
        when(dbMeta2.storesLowerCaseIdentifiers()).thenReturn(false);

        // charset/collation query (empty result)
        ResultSet charsetRs = Mockito.mock(ResultSet.class);
        when(charsetRs.next()).thenReturn(false);
        PreparedStatement charsetStatement = Mockito.mock(PreparedStatement.class);
        when(conn2.prepareStatement(anyString())).thenReturn(charsetStatement);
        when(charsetStatement.executeQuery()).thenReturn(charsetRs);

        // DESC query result - 模拟3列：DEFAULT_GENERATED, VIRTUAL GENERATED, STORED GENERATED
        ResultSet descRs = Mockito.mock(ResultSet.class);
        when(descRs.next()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
        when(descRs.getString("Extra"))
            .thenReturn("DEFAULT_GENERATED on update CURRENT_TIMESTAMP")  // col1: 有on update + DEFAULT_GENERATED
            .thenReturn("VIRTUAL GENERATED")                               // col2: virtual generated
            .thenReturn("STORED GENERATED");                               // col3: stored generated

        when(stmt2.executeQuery(anyString())).thenReturn(descRs);

        // metaData.getColumns - 模拟3列（均标记 IS_GENERATEDCOLUMN=YES）
        ResultSet columnsRs = Mockito.mock(ResultSet.class);
        when(dbMeta2.getColumns(eq("db1"), isNull(), eq("t1"), isNull()))
            .thenReturn(columnsRs);
        when(columnsRs.next()).thenReturn(true).thenReturn(true).thenReturn(true).thenReturn(false);
        when(columnsRs.getString("COLUMN_NAME"))
            .thenReturn("gmt_modified").thenReturn("virtual_col").thenReturn("stored_col");
        when(columnsRs.getInt("DATA_TYPE"))
            .thenReturn(Types.TIMESTAMP).thenReturn(Types.VARCHAR).thenReturn(Types.VARCHAR);
        when(columnsRs.getString("TYPE_NAME"))
            .thenReturn("TIMESTAMP").thenReturn("VARCHAR").thenReturn("VARCHAR");
        when(columnsRs.getInt("NULLABLE"))
            .thenReturn(1).thenReturn(1).thenReturn(1);
        when(columnsRs.getString("IS_GENERATEDCOLUMN"))
            .thenReturn("YES").thenReturn("YES").thenReturn("YES");
        when(columnsRs.getInt("COLUMN_SIZE"))
            .thenReturn(19).thenReturn(255).thenReturn(255);

        // --- conn3: getTablePks ---
        DatabaseMetaData dbMeta3 = Mockito.mock(DatabaseMetaData.class);
        when(conn3.getMetaData()).thenReturn(dbMeta3);
        when(dbMeta3.storesUpperCaseIdentifiers()).thenReturn(false);
        when(dbMeta3.storesLowerCaseIdentifiers()).thenReturn(false);
        ResultSet pkRs = Mockito.mock(ResultSet.class);
        when(dbMeta3.getPrimaryKeys(eq("db1"), isNull(), eq("t1"))).thenReturn(pkRs);
        when(pkRs.next()).thenReturn(false);

        // 调用 getTableInfo (needUkAndGsi=false)
        TableInfo tableInfo = DbMetaManager.getTableInfo(dataSource, "db1", "t1", HostType.RDS, false);

        // 验证生成列修正：
        List<ColumnInfo> columns = tableInfo.getColumns();
        Assert.assertEquals(3, columns.size());

        // col1 (gmt_modified): IS_GENERATEDCOLUMN=YES 但 Extra 是 DEFAULT_GENERATED → 修正为 false
        assertFalse("DEFAULT_GENERATED 列应被修正为 generated=false",
            columns.get(0).isGenerated());
        // col1 的 onUpdate 应为 true（Extra 含 "on update"）
        assertTrue("含 on update 的列 onUpdate 应为 true",
            columns.get(0).isOnUpdate());

        // col2 (virtual_col): IS_GENERATEDCOLUMN=YES 且 Extra 含 VIRTUAL GENERATED → 保持 true
        assertTrue("VIRTUAL GENERATED 列应保持 generated=true",
            columns.get(1).isGenerated());

        // col3 (stored_col): IS_GENERATEDCOLUMN=YES 且 Extra 含 STORED GENERATED → 保持 true
        assertTrue("STORED GENERATED 列应保持 generated=true",
            columns.get(2).isGenerated());
        assertTrue("关闭 UK 元数据发现时 V3 必须保守回退串行",
            tableInfo.isParallelApplyKeyUnsupported());
        Assert.assertTrue(tableInfo.getParallelApplyKeyIncompatibleReason().contains("discovery is disabled"));
    }

    /**
     * 测试 forceIgnoreUK 逻辑：
     * 当 forceIgnoreUK=true 时，无论 hostType 是什么，enableUk 都应为 false。
     * 使用反射临时修改 forceIgnoreUK 字段来验证该逻辑。
     */
    @Test
    public void testGetTableInfo_forceIgnoreUK_disablesUk() throws Exception {
        // 使用反射设置 forceIgnoreUK = true
        Field forceIgnoreUKField = DbMetaManager.class.getDeclaredField("forceIgnoreUK");
        forceIgnoreUKField.setAccessible(true);

        // 移除 final 修饰符
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(forceIgnoreUKField, forceIgnoreUKField.getModifiers() & ~Modifier.FINAL);

        boolean originalValue = forceIgnoreUKField.getBoolean(null);
        try {
            forceIgnoreUKField.setBoolean(null, true);

            // 使用 mockStatic 部分 mock DbMetaManager
            try (MockedStatic<DbMetaManager> mocked = Mockito.mockStatic(DbMetaManager.class)) {
                // 让 getTableInfo(ds, schema, tbName, hostType, customizedMap) 调用真实方法
                mocked.when(() -> DbMetaManager.getTableInfo(
                    any(DataSource.class), anyString(), anyString(), any(HostType.class),
                    any())).thenCallRealMethod();

                // mock 最终调用的 getTableInfo(ds, schema, tbName, hostType, enableUk, customizedMap)
                mocked.when(() -> DbMetaManager.getTableInfo(
                    any(DataSource.class), anyString(), anyString(), any(HostType.class),
                    eq(false), any())).thenReturn(new TableInfo("db1", "t1"));

                DataSource ds = Mockito.mock(DataSource.class);
                // POLARX2 hostType 正常情况下 enableUk=true，但 forceIgnoreUK=true 应强制 enableUk=false
                TableInfo result = DbMetaManager.getTableInfo(ds, "db1", "t1", HostType.POLARX2, null);
                Assert.assertNotNull(result);

                // 验证调用的是 enableUk=false 的重载
                mocked.verify(() -> DbMetaManager.getTableInfo(
                    any(DataSource.class), eq("db1"), eq("t1"), eq(HostType.POLARX2),
                    eq(false), isNull()));
            }
        } finally {
            // 恢复原始值
            forceIgnoreUKField.setBoolean(null, originalValue);
            modifiersField.setInt(forceIgnoreUKField, forceIgnoreUKField.getModifiers() | Modifier.FINAL);
        }
    }

    /**
     * 测试 enableUk 逻辑正常路径：
     * 当 forceIgnoreUK=false 时，POLARX2 hostType 的 enableUk=true
     */
    @Test
    public void testGetTableInfo_enableUk_normalPath() throws Exception {
        Field forceIgnoreUKField = DbMetaManager.class.getDeclaredField("forceIgnoreUK");
        forceIgnoreUKField.setAccessible(true);
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(forceIgnoreUKField, forceIgnoreUKField.getModifiers() & ~Modifier.FINAL);

        boolean originalValue = forceIgnoreUKField.getBoolean(null);
        try {
            forceIgnoreUKField.setBoolean(null, false);

            try (MockedStatic<DbMetaManager> mocked = Mockito.mockStatic(DbMetaManager.class)) {
                mocked.when(() -> DbMetaManager.getTableInfo(
                    any(DataSource.class), anyString(), anyString(), any(HostType.class),
                    any())).thenCallRealMethod();

                mocked.when(() -> DbMetaManager.getTableInfo(
                    any(DataSource.class), anyString(), anyString(), any(HostType.class),
                    eq(true), any())).thenReturn(new TableInfo("db1", "t1"));

                DataSource ds = Mockito.mock(DataSource.class);
                // POLARX2 + forceIgnoreUK=false → enableUk=true
                TableInfo result = DbMetaManager.getTableInfo(ds, "db1", "t1", HostType.POLARX2, null);
                Assert.assertNotNull(result);

                mocked.verify(() -> DbMetaManager.getTableInfo(
                    any(DataSource.class), eq("db1"), eq("t1"), eq(HostType.POLARX2),
                    eq(true), isNull()));
            }
        } finally {
            forceIgnoreUKField.setBoolean(null, originalValue);
            modifiersField.setInt(forceIgnoreUKField, forceIgnoreUKField.getModifiers() | Modifier.FINAL);
        }
    }

    private DataSource showCreateDataSource(String createTable) throws Exception {
        DataSource dataSource = Mockito.mock(DataSource.class);
        Connection connection = Mockito.mock(Connection.class);
        PreparedStatement statement = Mockito.mock(PreparedStatement.class);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.prepareStatement(anyString())).thenReturn(statement);
        when(statement.executeQuery()).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(createTable != null);
        when(resultSet.getString(2)).thenReturn(createTable);
        return dataSource;
    }

    private TableInfo tableInfoWithColumns(String... columnNames) {
        TableInfo tableInfo = new TableInfo("db1", "t1");
        List<ColumnInfo> columns = new ArrayList<>();
        for (String columnName : columnNames) {
            columns.add(new ColumnInfo(columnName, Types.VARCHAR, null, true, false, "VARCHAR", 64));
        }
        tableInfo.setColumns(columns);
        return tableInfo;
    }

    private SQLException assertLoadExternalizedColumnInfoFails(DataSource dataSource, TableInfo tableInfo)
        throws Exception {
        try {
            invokeLoadExternalizedColumnInfo(dataSource, "db1", "t1", tableInfo);
            Assert.fail("expected externalized-column metadata loading to fail closed");
            return null;
        } catch (SQLException e) {
            return e;
        }
    }

    private void invokeLoadExternalizedColumnInfo(DataSource dataSource, String schema, String table,
                                                  TableInfo tableInfo) throws Exception {
        Method method = DbMetaManager.class.getDeclaredMethod("loadExternalizedColumnInfo",
            DataSource.class, String.class, String.class, TableInfo.class);
        method.setAccessible(true);
        try {
            method.invoke(null, dataSource, schema, table, tableInfo);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

}
