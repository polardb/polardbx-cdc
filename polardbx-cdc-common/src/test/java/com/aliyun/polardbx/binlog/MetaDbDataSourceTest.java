/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.ConfigPropMap;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.tomcat.jdbc.pool.PoolProperties;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.apache.tomcat.jdbc.pool.DataSource;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.when;

/**
 * meta db datasource
 *
 * @author yudong
 **/
@Slf4j
public class MetaDbDataSourceTest extends BaseTest {

    @Test
    public void testWithDdlMode() throws SQLException, NoSuchFieldException, IllegalAccessException {
        try (MockedStatic<SQLUtils> sqlUtilsStatic = Mockito.mockStatic(SQLUtils.class)) {
            when(SQLUtils.isLeaderByDdl(Mockito.any())).thenReturn(true);
            when(SQLUtils.isLeaderBySqlQuery(Mockito.mock(DataSource.class))).thenReturn(false);

            MetaDbDataSource metaDb = new MetaDbDataSource("", false);
            Field field = ConfigPropMap.class.getDeclaredField("CONFIG_MAP");
            field.setAccessible(true);
            Map<String, String> CONFIG_MAP = (Map<String, String>) field.get(null);
            CONFIG_MAP.put(ConfigKeys.BINLOG_META_LEADER_DETECT_BY_DDL_MODE_ENABLE, "true");
            Assert.assertTrue(metaDb.isLeaderAndAvailable());
            CONFIG_MAP.put(ConfigKeys.BINLOG_META_LEADER_DETECT_BY_DDL_MODE_ENABLE, "false");
            Assert.assertFalse(metaDb.isLeaderAndAvailable());
        }
    }

    @Test
    @SneakyThrows
    public void testScan() {
        mockConfig(ConfigKeys.METADB_URL, "jdbc:mysql://127.0.0.1:3306/__cdc__");
        mockConfig(ConfigKeys.DAEMON_METADB_SCAN_BY_SELECT_ENABLED, "true");
        mockConfig(ConfigKeys.DAEMON_METADB_SCAN_CHECK_ENV_ENABLED, "true");
        MetaDbDataSource metaDbDataSource = Mockito.mock(MetaDbDataSource.class);

        Field field = metaDbDataSource.getClass().getDeclaredField("metaDbDataSource");
        field.setAccessible(true);
        DataSource dataSource = Mockito.mock(DataSource.class);
        field.set(metaDbDataSource, dataSource);

        Field poolPropertiesField = metaDbDataSource.getClass().getDeclaredField("poolProperties");
        poolPropertiesField.setAccessible(true);
        PoolProperties poolProperties = Mockito.mock(PoolProperties.class);
        poolPropertiesField.set(metaDbDataSource, poolProperties);

        Connection cnConnection = Mockito.mock(Connection.class);
        when(metaDbDataSource.getCnConnection()).thenReturn(cnConnection);
        when(metaDbDataSource.getMetaDBUrlBySelect(cnConnection)).thenReturn("jdbc:mysql://127.0.0.1:3306/__cdc__");
        when(metaDbDataSource.buildMetaDbDataSource()).thenReturn(dataSource);
        Mockito.doCallRealMethod().when(metaDbDataSource).scan();
        Mockito.doCallRealMethod().when(metaDbDataSource).haWitch(any());
        Mockito.doCallRealMethod().when(metaDbDataSource).getUrl();
        Mockito.doCallRealMethod().when(metaDbDataSource).setUrl(any());

        metaDbDataSource.scan();

        Assert.assertEquals("jdbc:mysql://127.0.0.1:3306/__cdc__&socketTimeout=30000&connectTimeout=5000",
            metaDbDataSource.getUrl());
    }

    @Test
    @SneakyThrows
    public void testGetDNConnection() {
        MetaDbDataSource metaDbDataSource = Mockito.mock(MetaDbDataSource.class, CALLS_REAL_METHODS);
        try (MockedStatic<DriverManager> driverManagerMockedStatic = Mockito.mockStatic(DriverManager.class)) {
            Connection connection = Mockito.mock(Connection.class);
            driverManagerMockedStatic.when(() -> DriverManager.getConnection(any(), any(), any()))
                .thenReturn(connection);
            Connection c = metaDbDataSource.getDNConnection("127.0.0.1", 3306, "root", "4Gfq3RU3hUobsoZ+1ENF3g==");
            Assert.assertEquals(connection, c);
        }
    }

    @Test
    @SneakyThrows
    public void testGetMetaUrlBySelect() {
        MetaDbDataSource metaDbDataSource = Mockito.mock(MetaDbDataSource.class);

        Field metaDbUrlTemplateField = metaDbDataSource.getClass().getDeclaredField("metaDbUrlTemplate");
        metaDbUrlTemplateField.setAccessible(true);
        metaDbUrlTemplateField.set(metaDbDataSource, "jdbc:mysql://%s/polardbx_meta_db?useSSL=false");

        Connection c = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        ResultSet rs = Mockito.mock(ResultSet.class);

        when(c.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(any())).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString("ip")).thenReturn("localhost");
        when(rs.getInt("port")).thenReturn(3306);
        when(rs.getString("user")).thenReturn("root");
        when(rs.getString("passwd_enc")).thenReturn("4Gfq3RU3hUobsoZ+1ENF3g==");
        when(rs.getString("ROLE")).thenReturn("Leader");
        when(metaDbDataSource.getDNConnection("localhost", 3306, "root", "4Gfq3RU3hUobsoZ+1ENF3g==")).thenReturn(c);
        when(metaDbDataSource.getMetaDBUrlBySelect(c)).thenCallRealMethod();

        String url = metaDbDataSource.getMetaDBUrlBySelect(c);
        log.warn("url:{}", url);
        Assert.assertEquals("jdbc:mysql://localhost:3306/polardbx_meta_db?useSSL=false", url);
        when(rs.next()).thenReturn(false);
        url = metaDbDataSource.getMetaDBUrlBySelect(c);
        Assert.assertNull(url);
    }

    /**
     * 测试 getDNConnection 抛出 SQLException 时，getMetaDBUrlBySelect 跳过失败节点，继续找到 Leader 节点
     */
    @Test
    @SneakyThrows
    public void testGetMetaUrlBySelectSkipsFailedConnection() {
        MetaDbDataSource metaDbDataSource = Mockito.mock(MetaDbDataSource.class);

        Field metaDbUrlTemplateField = metaDbDataSource.getClass().getDeclaredField("metaDbUrlTemplate");
        metaDbUrlTemplateField.setAccessible(true);
        metaDbUrlTemplateField.set(metaDbDataSource, "jdbc:mysql://%s/polardbx_meta_db?useSSL=false");

        Connection c = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        ResultSet rs = Mockito.mock(ResultSet.class);

        when(c.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(any())).thenReturn(rs);
        // 两条记录
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString("ip")).thenReturn("10.0.0.1", "10.0.0.2");
        when(rs.getInt("port")).thenReturn(3306, 3307);
        when(rs.getString("user")).thenReturn("root", "root");
        when(rs.getString("passwd_enc")).thenReturn("enc1", "enc2");

        // 第一个节点连接失败
        when(metaDbDataSource.getDNConnection("10.0.0.1", 3306, "root", "enc1"))
            .thenThrow(new SQLException("connection failed"));

        // 第二个节点连接成功，且为 Leader
        Connection dnConn = Mockito.mock(Connection.class);
        Statement dnStmt = Mockito.mock(Statement.class);
        ResultSet dnRs = Mockito.mock(ResultSet.class);
        when(dnConn.createStatement()).thenReturn(dnStmt);
        when(dnStmt.executeQuery(any())).thenReturn(dnRs);
        when(dnRs.next()).thenReturn(true);
        when(dnRs.getString("ROLE")).thenReturn("Leader");
        when(metaDbDataSource.getDNConnection("10.0.0.2", 3307, "root", "enc2"))
            .thenReturn(dnConn);

        when(metaDbDataSource.getMetaDBUrlBySelect(c)).thenCallRealMethod();

        String url = metaDbDataSource.getMetaDBUrlBySelect(c);
        Assert.assertEquals("jdbc:mysql://10.0.0.2:3307/polardbx_meta_db?useSSL=false", url);
    }

    /**
     * 测试所有节点的 getDNConnection 都抛出 SQLException 时，getMetaDBUrlBySelect 返回 null
     */
    @Test
    @SneakyThrows
    public void testGetMetaUrlBySelectAllConnectionsFailed() {
        MetaDbDataSource metaDbDataSource = Mockito.mock(MetaDbDataSource.class);

        Field metaDbUrlTemplateField = metaDbDataSource.getClass().getDeclaredField("metaDbUrlTemplate");
        metaDbUrlTemplateField.setAccessible(true);
        metaDbUrlTemplateField.set(metaDbDataSource, "jdbc:mysql://%s/polardbx_meta_db?useSSL=false");

        Connection c = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        ResultSet rs = Mockito.mock(ResultSet.class);

        when(c.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(any())).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString("ip")).thenReturn("10.0.0.1", "10.0.0.2");
        when(rs.getInt("port")).thenReturn(3306, 3307);
        when(rs.getString("user")).thenReturn("root", "root");
        when(rs.getString("passwd_enc")).thenReturn("enc1", "enc2");

        // 两个节点连接都失败
        when(metaDbDataSource.getDNConnection("10.0.0.1", 3306, "root", "enc1"))
            .thenThrow(new SQLException("connection failed"));
        when(metaDbDataSource.getDNConnection("10.0.0.2", 3307, "root", "enc2"))
            .thenThrow(new SQLException("connection failed"));

        when(metaDbDataSource.getMetaDBUrlBySelect(c)).thenCallRealMethod();

        String url = metaDbDataSource.getMetaDBUrlBySelect(c);
        Assert.assertNull(url);
    }

    /**
     * 测试首节点不是 Leader、第二节点连接失败时，getMetaDBUrlBySelect 返回 null
     */
    @Test
    @SneakyThrows
    public void testGetMetaUrlBySelectNonLeaderThenConnectionFailed() {
        MetaDbDataSource metaDbDataSource = Mockito.mock(MetaDbDataSource.class);

        Field metaDbUrlTemplateField = metaDbDataSource.getClass().getDeclaredField("metaDbUrlTemplate");
        metaDbUrlTemplateField.setAccessible(true);
        metaDbUrlTemplateField.set(metaDbDataSource, "jdbc:mysql://%s/polardbx_meta_db?useSSL=false");

        Connection c = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        ResultSet rs = Mockito.mock(ResultSet.class);

        when(c.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(any())).thenReturn(rs);
        when(rs.next()).thenReturn(true, true, false);
        when(rs.getString("ip")).thenReturn("10.0.0.1", "10.0.0.2");
        when(rs.getInt("port")).thenReturn(3306, 3307);
        when(rs.getString("user")).thenReturn("root", "root");
        when(rs.getString("passwd_enc")).thenReturn("enc1", "enc2");

        // 第一个节点连接成功但不是 Leader
        Connection dnConn1 = Mockito.mock(Connection.class);
        Statement dnStmt1 = Mockito.mock(Statement.class);
        ResultSet dnRs1 = Mockito.mock(ResultSet.class);
        when(dnConn1.createStatement()).thenReturn(dnStmt1);
        when(dnStmt1.executeQuery(any())).thenReturn(dnRs1);
        when(dnRs1.next()).thenReturn(true, false);
        when(dnRs1.getString("ROLE")).thenReturn("Follower");
        when(metaDbDataSource.getDNConnection("10.0.0.1", 3306, "root", "enc1"))
            .thenReturn(dnConn1);

        // 第二个节点连接失败
        when(metaDbDataSource.getDNConnection("10.0.0.2", 3307, "root", "enc2"))
            .thenThrow(new SQLException("connection failed"));

        when(metaDbDataSource.getMetaDBUrlBySelect(c)).thenCallRealMethod();

        String url = metaDbDataSource.getMetaDBUrlBySelect(c);
        Assert.assertNull(url);
    }

    @Test
    @SneakyThrows
    public void testGetMetaUrlByShowStorage() {
        MetaDbDataSource metaDbDataSource = Mockito.mock(MetaDbDataSource.class);

        Field metaDbUrlTemplateField = metaDbDataSource.getClass().getDeclaredField("metaDbUrlTemplate");
        metaDbUrlTemplateField.setAccessible(true);
        metaDbUrlTemplateField.set(metaDbDataSource, "jdbc:mysql://%s/polardbx_meta_db?useSSL=false");

        Connection c = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        ResultSet rs = Mockito.mock(ResultSet.class);

        when(c.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery(any())).thenReturn(rs);
        when(rs.next()).thenReturn(true);
        when(rs.getString("INST_KIND")).thenReturn("META_DB");
        when(rs.getString("LEADER_NODE")).thenReturn("localhost:3306");
        when(metaDbDataSource.getMetaDBUrlByShowStorage(c)).thenCallRealMethod();

        String url = metaDbDataSource.getMetaDBUrlByShowStorage(c);
        log.warn("url:{}", url);
        Assert.assertEquals("jdbc:mysql://localhost:3306/polardbx_meta_db?useSSL=false", url);
        when(rs.getString("LEADER_NODE")).thenReturn("");
        url = metaDbDataSource.getMetaDBUrlByShowStorage(c);
        Assert.assertNull(url);
        when(rs.next()).thenReturn(false);
        url = metaDbDataSource.getMetaDBUrlByShowStorage(c);
        Assert.assertNull(url);
    }
}
