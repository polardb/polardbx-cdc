/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.dump.MysqlConnection;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;

public class DnHealthCheckerTest extends BaseTest {
    @Test
    public void testBuildConn() throws NoSuchFieldException, IllegalAccessException {
        mockConfig(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_CONN_TIMEOUT_SEC, "4");
        AuthenticationInfo auth = new AuthenticationInfo();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 3306);
        String storageInstId = "test-dn";
        auth.setStorageInstId(storageInstId);
        auth.setAddress(address);
        DnHealthChecker checker = new DnHealthChecker(auth);
        MysqlConnection conn = checker.buildConn();
        Assert.assertNotNull(conn);
        Assert.assertEquals(address, conn.getAddress());
        Assert.assertEquals(storageInstId, checker.getStorageInstId());
        Field connField = MysqlConnection.class.getDeclaredField("connTimeout");
        connField.setAccessible(true);

        Field socketField = MysqlConnection.class.getDeclaredField("soTimeout");
        socketField.setAccessible(true);

        Assert.assertEquals(4000, connField.get(conn));
        Assert.assertEquals(4000, socketField.get(conn));
    }

    @Test
    public void testCheck() throws IOException {
        AuthenticationInfo auth = new AuthenticationInfo();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 3306);
        String storageInstId = "test-dn";
        auth.setStorageInstId(storageInstId);
        auth.setAddress(address);
        DnHealthChecker checker = Mockito.mock(DnHealthChecker.class, Mockito.withSettings().useConstructor(auth));
        Mockito.when(checker.getStorageInstId()).thenReturn("test-dn");
        MysqlConnection conn = Mockito.mock(MysqlConnection.class);
        Mockito.when(conn.query(anyString(), any())).thenReturn(1);
        Mockito.when(checker.buildConn()).thenReturn(conn);
        Mockito.doCallRealMethod().when(checker).check();
        checker.check();

        Mockito.verify(checker, Mockito.times(1)).buildConn();
        Mockito.verify(conn, Mockito.times(1)).connect();
        Mockito.verify(conn, Mockito.times(1)).query(anyString(), any());
    }

    @Test
    public void testRegister() throws NoSuchFieldException, IllegalAccessException {
        AuthenticationInfo auth = new AuthenticationInfo();
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 3306);
        String storageInstId = "test-dn";
        auth.setStorageInstId(storageInstId);
        auth.setAddress(address);
        DnHealthChecker checker = Mockito.mock(DnHealthChecker.class, Mockito.withSettings().useConstructor(auth));
        DnHealthCheckerManager manager = new DnHealthCheckerManager();

        registerSpringObject("dnHealthCheckerManager", manager);

        Mockito.when(checker.getStorageInstId()).thenReturn(storageInstId);
        Mockito.doCallRealMethod().when(checker).startCheck();
        Mockito.doCallRealMethod().when(checker).stopCheck();

        Field field = DnHealthCheckerManager.class.getDeclaredField("taskCheckerMap");
        field.setAccessible(true);
        ConcurrentHashMap<String, DnHealthChecker> taskCheckerMap =
            (ConcurrentHashMap<String, DnHealthChecker>) field.get(manager);
        Assert.assertTrue(taskCheckerMap.isEmpty());
        checker.startCheck();
        Assert.assertFalse(taskCheckerMap.isEmpty());
        Assert.assertEquals(checker, taskCheckerMap.get(storageInstId));
        checker.stopCheck();
        Assert.assertTrue(taskCheckerMap.isEmpty());
    }

    @Test(expected = PolardbxException.class)
    public void followerCheck() {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 3306);
        String storageInstId = "test-dn";
        AuthenticationInfo auth = new AuthenticationInfo(address, "root", "123456");
        auth.setStorageInstId(storageInstId);
        DnHealthChecker checker = Mockito.mock(DnHealthChecker.class, Mockito.withSettings().useConstructor(auth));
        Mockito.doCallRealMethod().when(checker).followerDelayCheck();
        Mockito.when(checker.followerDelay()).thenReturn(130);
        Mockito.when(checker.isFollower()).thenReturn(true);
        checker.followerDelayCheck();
    }

    @Test
    public void isFollowerTest() throws IOException, SQLException {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 3306);
        String storageInstId = "test-dn";
        AuthenticationInfo auth = new AuthenticationInfo(address, "root", "123456");
        auth.setStorageInstId(storageInstId);
        DnHealthChecker checker = Mockito.mock(DnHealthChecker.class, Mockito.withSettings().useConstructor(auth));

        Connection innerConn = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        Mockito.when(innerConn.createStatement()).thenReturn(stmt);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.next()).thenReturn(true, false);
        Mockito.when(resultSet.getString(anyInt())).thenReturn("Follower");
        Mockito.when(stmt.executeQuery(anyString())).thenReturn(resultSet);

        MysqlConnection conn = Mockito.mock(MysqlConnection.class, Mockito.withSettings().useConstructor(innerConn));
        Mockito.when(conn.query(anyString(), any())).thenCallRealMethod();
        Mockito.when(checker.buildConn()).thenReturn(conn);
        Mockito.when(checker.isFollower()).thenCallRealMethod();
        Mockito.doCallRealMethod().when(checker).check();
        checker.check();
        Assert.assertTrue(checker.isFollower());
    }

    @Test
    public void followerDelayTest() throws IOException, SQLException {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 3306);
        String storageInstId = "test-dn";
        AuthenticationInfo auth = new AuthenticationInfo(address, "root", "123456");
        auth.setStorageInstId(storageInstId);
        DnHealthChecker checker = Mockito.mock(DnHealthChecker.class, Mockito.withSettings().useConstructor(auth));

        Connection innerConn = Mockito.mock(Connection.class);
        Statement stmt = Mockito.mock(Statement.class);
        Mockito.when(innerConn.createStatement()).thenReturn(stmt);
        ResultSet resultSet = Mockito.mock(ResultSet.class);
        Mockito.when(resultSet.next()).thenReturn(true, false);
        Mockito.when(resultSet.getInt(anyString())).thenReturn(1000);
        Mockito.when(stmt.executeQuery(anyString())).thenReturn(resultSet);

        MysqlConnection conn = Mockito.mock(MysqlConnection.class, Mockito.withSettings().useConstructor(innerConn));
        Mockito.when(conn.query(anyString(), any())).thenCallRealMethod();
        Mockito.when(checker.buildConn()).thenReturn(conn);
        Mockito.when(checker.followerDelay()).thenCallRealMethod();
        Mockito.doCallRealMethod().when(checker).check();
        checker.check();
        Assert.assertEquals(1000, checker.followerDelay());
    }
}
