/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.sql.Statement;

import static org.mockito.Mockito.*;

/**
 * MysqlDetectingTimeTask测试类
 */
@Slf4j
public class MysqlDetectingTimeTaskTest extends BaseTest {

    @Mock
    private MysqlConnection mockMysqlConnection;

    @Mock
    private Connection mockConnection;

    @Mock
    private Statement mockStatement;

    @Mock
    private ResultSet mockResultSet;

    @Before
    public void setUp() throws SQLException {
        MockitoAnnotations.initMocks(this);
        when(mockMysqlConnection.getConn()).thenReturn(null, mockConnection);
        when(mockConnection.createStatement()).thenReturn(mockStatement);
    }

    /**
     * 测试createHeartbeatDatabaseAndTable方法
     * 验证是否正确创建数据库和表
     */
    @Test
    public void testCreateHeartbeatDatabaseAndTable() throws SQLException {
        // 准备测试数据
        MysqlDetectingTimeTask task = new MysqlDetectingTimeTask(mockMysqlConnection, true);

        // 模拟执行SQL时不抛出异常
        when(mockStatement.execute(anyString())).thenReturn(true);

        // 使用反射调用私有方法
        try {
            java.lang.reflect.Method method = MysqlDetectingTimeTask.class.getDeclaredMethod(
                "createHeartbeatDatabaseAndTable", Statement.class);
            method.setAccessible(true);
            method.invoke(task, mockStatement);

            // 验证是否执行了创建数据库的SQL
            verify(mockStatement, times(1)).execute("create database if not exists __polardbx2__");

            // 验证是否执行了创建表的SQL
            verify(mockStatement, times(1)).execute(
                "create table if not exists `__polardbx2__`.`__system__mysql__heartbeat__`" +
                    "(id int(4) AUTO_INCREMENT, gmt_create timestamp, PRIMARY KEY (`id`));");
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * 测试buildDetectedSqlBefore方法
     * 验证是否正确构建检测SQL
     */
    @Test
    public void testBuildDetectedSqlBefore() {
        // 准备测试数据
        MysqlDetectingTimeTask task = new MysqlDetectingTimeTask(mockMysqlConnection, true);

        // 调用方法前检查detectingSQL
        try {
            java.lang.reflect.Field detectingSQLField =
                MysqlDetectingTimeTask.class.getDeclaredField("detectingSQL");
            detectingSQLField.setAccessible(true);
            String beforeSQL = (String) detectingSQLField.get(task);

            // 使用反射调用私有方法
            java.lang.reflect.Method method = MysqlDetectingTimeTask.class.getDeclaredMethod(
                "buildDetectedSqlBefore");
            method.setAccessible(true);
            method.invoke(task);

            // 检查detectingSQL是否被正确更新
            String afterSQL = (String) detectingSQLField.get(task);
            Assert.assertEquals(
                "replace into `__polardbx2__`.`__system__mysql__heartbeat__`(id,gmt_create) values(1,NOW())", afterSQL);
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * 测试findIfExists方法
     * 验证是否能正确找到已存在的心跳表
     */
    @Test
    public void testFindIfExists() throws SQLException {
        // 准备测试数据
        MysqlDetectingTimeTask task = new MysqlDetectingTimeTask(mockMysqlConnection, true);

        // 模拟执行show databases查询
        when(mockStatement.executeQuery("show databases")).thenReturn(mockResultSet);

        // 模拟数据库查询结果
        when(mockResultSet.next()).thenReturn(true, true, false); // 返回两个数据库
        when(mockResultSet.getString(1)).thenReturn("information_schema", "test_db"); // 第一个是系统库，第二个是测试库

        // 模拟检查表是否存在时第一个表不存在，第二个表存在
        when(mockStatement.execute(
            "select 1 from `information_schema`.`__system__mysql__heartbeat__` limit 1")).thenThrow(
            new SQLException("Table doesn't exist"));
        when(mockStatement.execute("select 1 from `test_db`.`__system__mysql__heartbeat__` limit 1")).thenReturn(true);

        // 使用反射调用私有方法
        try {
            java.lang.reflect.Method method = MysqlDetectingTimeTask.class.getDeclaredMethod(
                "findIfExists", Statement.class);
            method.setAccessible(true);
            boolean result = (boolean) method.invoke(task, mockStatement);

            // 验证结果
            Assert.assertTrue("Should find existing heartbeat table", result);

            // 验证heartbeatDatabaseName和heartbeatTableName是否被正确更新
            java.lang.reflect.Field heartbeatDatabaseNameField =
                MysqlDetectingTimeTask.class.getDeclaredField("heartbeatDatabaseName");
            heartbeatDatabaseNameField.setAccessible(true);
            String databaseName = (String) heartbeatDatabaseNameField.get(task);
            Assert.assertEquals("test_db", databaseName);

            java.lang.reflect.Field heartbeatTableNameField =
                MysqlDetectingTimeTask.class.getDeclaredField("heartbeatTableName");
            heartbeatTableNameField.setAccessible(true);
            String tableName = (String) heartbeatTableNameField.get(task);
            Assert.assertEquals("__system__mysql__heartbeat__", tableName);
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
    }

    @Test
    public void testFindIfExistsContinuesAfterFirstDatabaseWithoutHeartbeat() throws SQLException {
        MysqlDetectingTimeTask task = new MysqlDetectingTimeTask(mockMysqlConnection, true);
        when(mockStatement.executeQuery("show databases")).thenReturn(mockResultSet);
        when(mockResultSet.next()).thenReturn(true, true, false);
        when(mockResultSet.getString(1)).thenReturn("first_db", "second_db");
        java.util.concurrent.atomic.AtomicBoolean closed = new java.util.concurrent.atomic.AtomicBoolean();
        doAnswer(invocation -> {
            closed.set(true);
            return null;
        }).when(mockResultSet).close();
        when(mockStatement.executeQuery("select 1 from `first_db`.`__system__mysql__heartbeat__` limit 1"))
            .thenAnswer(invocation -> {
                Assert.assertTrue("database cursor must be consumed and closed before reusing Statement", closed.get());
                throw new SQLException("Table doesn't exist");
            });
        ResultSet heartbeat = mock(ResultSet.class);
        when(mockStatement.executeQuery("select 1 from `second_db`.`__system__mysql__heartbeat__` limit 1"))
            .thenReturn(heartbeat);
        Assert.assertTrue(task.findIfExists(mockStatement));
        verify(mockResultSet).close();
        verify(heartbeat).close();
    }

    /**
     * 测试findAndCreateIfNotExists方法
     * 验证是否能正确找到第一个非系统数据库并创建心跳表
     */
    @Test
    public void testFindAndCreateIfNotExists() throws SQLException {
        // 准备测试数据
        MysqlDetectingTimeTask task = new MysqlDetectingTimeTask(mockMysqlConnection, true);

        // 模拟执行show databases查询
        when(mockStatement.executeQuery("show databases")).thenReturn(mockResultSet);

        // 模拟数据库查询结果
        when(mockResultSet.next()).thenReturn(true, true, false); // 返回两个数据库
        when(mockResultSet.getString(1)).thenReturn("information_schema", "test_db"); // 第一个是系统库，第二个是测试库

        // 模拟执行SQL时不抛出异常
        when(mockStatement.execute(anyString())).thenReturn(true);

        // 使用反射调用私有方法
        try {
            java.lang.reflect.Method method = MysqlDetectingTimeTask.class.getDeclaredMethod(
                "findAndCreateIfNotExists", Statement.class);
            method.setAccessible(true);
            method.invoke(task, mockStatement);

            // 验证是否执行了创建表的SQL
            verify(mockStatement, times(1)).execute(
                "create table if not exists `test_db`.`__system__mysql__heartbeat__`" +
                    "(id int(4) AUTO_INCREMENT, gmt_create DATETIME(3), PRIMARY KEY (`id`));");

            // 验证heartbeatDatabaseName和heartbeatTableName是否被正确更新
            java.lang.reflect.Field heartbeatDatabaseNameField =
                MysqlDetectingTimeTask.class.getDeclaredField("heartbeatDatabaseName");
            heartbeatDatabaseNameField.setAccessible(true);
            String databaseName = (String) heartbeatDatabaseNameField.get(task);
            Assert.assertEquals("test_db", databaseName);

            java.lang.reflect.Field heartbeatTableNameField =
                MysqlDetectingTimeTask.class.getDeclaredField("heartbeatTableName");
            heartbeatTableNameField.setAccessible(true);
            String tableName = (String) heartbeatTableNameField.get(task);
            Assert.assertEquals("__system__mysql__heartbeat__", tableName);
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * 测试run方法 - 当需要创建心跳表且设置了heartbeatDatabaseName时
     * 验证是否正确创建数据库和表
     */
    @Test
    public void testRun_CreateHeartbeatTableWithDatabaseName() throws SQLException {
        mockConfig(ConfigKeys.IS_LAB_ENV, "true");
        // 准备测试数据
        MysqlDetectingTimeTask task = new MysqlDetectingTimeTask(mockMysqlConnection, true);
        task.setServerId("123456");

        // 模拟执行SQL时不抛出异常
        when(mockStatement.execute(anyString())).thenReturn(true);

        // 调用被测试的方法
        task.run();
        task.run();

        // 验证是否执行了创建数据库的SQL
        verify(mockStatement, times(2)).execute("set polardbx_server_id=123456");
        verify(mockStatement, times(1)).execute("create database if not exists __polardbx2__");

        // 验证是否执行了创建表的SQL
        verify(mockStatement, times(1)).execute(
            "create table if not exists `__polardbx2__`.`__system__mysql__heartbeat__`" +
                "(id int(4) AUTO_INCREMENT, gmt_create timestamp, PRIMARY KEY (`id`));");

        // 验证是否执行了心跳检测SQL
        verify(mockStatement, times(1)).execute(
            "replace into `__polardbx2__`.`__system__mysql__heartbeat__`(id,gmt_create) values(1,NOW())");
    }

    /**
     * 测试run方法 - 当需要创建心跳表但未设置heartbeatDatabaseName时
     * 验证是否在第一个非系统数据库中创建心跳表
     */
    @Test
    public void testRun_CreateHeartbeatTableWithoutDatabaseName() throws SQLException {
        // 准备测试数据
        MysqlDetectingTimeTask task = new MysqlDetectingTimeTask(mockMysqlConnection, true);

        // 使用反射修改私有字段
        try {
            java.lang.reflect.Field heartbeatDatabaseNameField =
                MysqlDetectingTimeTask.class.getDeclaredField("heartbeatDatabaseName");
            heartbeatDatabaseNameField.setAccessible(true);
            heartbeatDatabaseNameField.set(task, "");

            // 模拟数据库查询结果
            when(mockResultSet.next()).thenReturn(true, true, false, true, true, false); // 返回两个数据库
            when(mockResultSet.getString(1)).thenReturn("information_schema", "test_db", "information_schema",
                "test_db"); // 第一个是系统库，第二个是测试库

            // 模拟检查表是否存在时抛出异常（表不存在）
            when(mockStatement.executeQuery("select 1 from `test_db`.`__system__mysql__heartbeat__` limit 1")).
                thenThrow(new SQLException("Table doesn't exist"));
            // 模拟执行show databases查询
            when(mockStatement.executeQuery("show databases")).thenReturn(mockResultSet);

            // 模拟执行SQL时不抛出异常
            when(mockStatement.execute(anyString())).thenReturn(true);

            // 调用被测试的方法
            task.run();
            task.run();

            // 验证是否执行了创建表的SQL
            verify(mockStatement, times(1)).execute(
                "create table if not exists `test_db`.`__system__mysql__heartbeat__`(id int(4) AUTO_INCREMENT, gmt_create DATETIME(3), PRIMARY KEY (`id`));");

            // 验证是否执行了心跳检测SQL
            verify(mockStatement, times(1)).execute(
                "replace into `test_db`.`__system__mysql__heartbeat__`(id,gmt_create) values(1,NOW())");
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * 测试run方法 - 当不需要创建心跳表时
     * 验证是否直接执行心跳检测SQL
     */
    @Test
    public void testRun_WithoutCreatingHeartbeatTable() throws SQLException {
        // 准备测试数据
        MysqlDetectingTimeTask task = new MysqlDetectingTimeTask(mockMysqlConnection, false);
        // 模拟执行SQL时不抛出异常
        when(mockStatement.execute(anyString())).thenReturn(true);

        // 调用被测试的方法
        task.run();

        // 验证是否执行了心跳检测SQL
        verify(mockStatement, times(1)).execute(
            "replace into `__polardbx2__`.`__system__mysql__heartbeat__`(id,gmt_create) values(1,NOW())");
    }

    /**
     * 测试run方法 - 当发生SQLException时
     * 验证是否正确处理异常并设置reconnect标志
     */
    @Test
    public void testRun_HandleSQLException() throws SQLException {
        // 准备测试数据
        MysqlDetectingTimeTask task = new MysqlDetectingTimeTask(mockMysqlConnection, false);

        // 模拟执行SQL时抛出SQLException
        doThrow(new SQLException("Test exception")).when(mockStatement).execute(anyString());

        // 调用被测试的方法
        task.run();

        // 验证是否设置了reconnect标志
        try {
            java.lang.reflect.Field reconnectField =
                MysqlDetectingTimeTask.class.getDeclaredField("reconnect");
            reconnectField.setAccessible(true);
            boolean reconnect = (boolean) reconnectField.get(task);
            Assert.assertTrue("reconnect flag should be set to true", reconnect);
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * 测试run方法 - 当发生SocketTimeoutException时
     * 验证是否正确处理异常并设置reconnect标志
     */
    @Test
    public void testRun_HandleSocketTimeoutException() throws SQLException {
        // 准备测试数据
        MysqlDetectingTimeTask task = new MysqlDetectingTimeTask(mockMysqlConnection, false);

        // 模拟执行SQL时抛出SocketTimeoutException
        when(mockStatement.execute(anyString())).thenThrow(new SQLTimeoutException("Test timeout"));

        // 调用被测试的方法
        task.run();
        task.run();

        // 验证是否设置了reconnect标志
        try {
            java.lang.reflect.Field reconnectField =
                MysqlDetectingTimeTask.class.getDeclaredField("reconnect");
            reconnectField.setAccessible(true);
            boolean reconnect = (boolean) reconnectField.get(task);
            Assert.assertTrue("reconnect flag should be set to true", reconnect);
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e.getMessage());
        }
    }

    /**
     * 测试getMysqlConnection方法
     * 验证是否正确返回MysqlConnection对象
     */
    @Test
    public void testGetMysqlConnection() {
        // 准备测试数据
        MysqlDetectingTimeTask task = new MysqlDetectingTimeTask(mockMysqlConnection, false);

        // 调用被测试的方法
        MysqlConnection result = task.getMysqlConnection();

        // 验证结果
        Assert.assertEquals("Should return the same MysqlConnection object", mockMysqlConnection, result);
    }
}
