/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.service;

import com.alibaba.druid.util.JdbcUtils;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.PasswdUtil;
import com.aliyun.polardbx.binlog.util.SQLUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

public class StorageInfoServiceTest extends BaseTest {
    @Test
    public void getFollowerStorageInfoTest() {
        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);
        List<StorageInfo> storageInfo = service.getFollowerStorageInfo("sid", null);
        Assert.assertNull(storageInfo);
    }

    @Test
    public void getFollowerStorageByClusterLocalTest() {
        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);
        try (MockedStatic<DriverManager> managerMockedStatic = mockStatic(DriverManager.class);
            MockedStatic<JdbcUtils> jdbcUtilsMockedStatic = mockStatic(JdbcUtils.class);
            MockedStatic<PasswdUtil> passwdUtilMockedStatic = mockStatic(PasswdUtil.class)) {
            Connection conn = Mockito.mock(Connection.class);
            managerMockedStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                .thenReturn(conn);
            passwdUtilMockedStatic.when(() -> PasswdUtil.decryptBase64(anyString())).thenReturn("password");
            List<Map<String, Object>> resultList = new ArrayList<>();
            Map<String, Object> rowMap = new HashMap<>();
            rowMap.put("ROLE", "follower");
            resultList.add(rowMap);
            jdbcUtilsMockedStatic.when(() -> JdbcUtils.executeQuery(any(Connection.class), anyString(), anyList()))
                .thenReturn(resultList);
            List<StorageInfo> maybeFollowerList = new ArrayList<>();
            StorageInfo storageInfo = new StorageInfo();
            storageInfo.setStorageInstId("test-inst-id");
            storageInfo.setUser("user");
            storageInfo.setPasswdEnc("aaa");
            maybeFollowerList.add(storageInfo);
            List<StorageInfo> ret = service.getFollowerStorageByClusterLocal(maybeFollowerList);
            Assert.assertNotNull(ret);
            Assert.assertEquals(storageInfo, ret.get(0));
        }

    }

    @Test
    public void checkLeaderByDNTest_Success() {
        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);

        // 创建mock对象
        StorageInfo storageInfo = new StorageInfo();
        storageInfo.setIp("127.0.0.1");
        storageInfo.setPort(3306);
        storageInfo.setUser("user");
        storageInfo.setPasswdEnc("password");

        // Mock依赖组件
        try (MockedStatic<DriverManager> managerMockedStatic = mockStatic(DriverManager.class);
            MockedStatic<PasswdUtil> passwdUtilMockedStatic = mockStatic(PasswdUtil.class);
            MockedStatic<SQLUtils> sqlUtilsMockedStatic = mockStatic(SQLUtils.class)) {

            // Mock连接和密码解密
            Connection conn = mock(Connection.class);
            managerMockedStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                .thenReturn(conn);
            passwdUtilMockedStatic.when(() -> PasswdUtil.decryptBase64(anyString())).thenReturn("decryptedPassword");

            // Mock SQLUtils.isLeaderBySqlQuery返回true
            sqlUtilsMockedStatic.when(() -> SQLUtils.isLeaderBySqlQuery(conn)).thenReturn(true);

            // 执行测试
            boolean result = service.checkLeaderByDN(storageInfo);

            // 验证结果
            Assert.assertTrue(result);
        }
    }

    @Test
    public void checkLeaderByDNTest_NotLeader() throws SQLException {
        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);

        // 创建mock对象
        StorageInfo storageInfo = new StorageInfo();
        storageInfo.setIp("127.0.0.1");
        storageInfo.setPort(3306);
        storageInfo.setUser("user");
        storageInfo.setPasswdEnc("password");

        // Mock依赖组件
        try (MockedStatic<DriverManager> managerMockedStatic = mockStatic(DriverManager.class);
            MockedStatic<PasswdUtil> passwdUtilMockedStatic = mockStatic(PasswdUtil.class);
            MockedStatic<SQLUtils> sqlUtilsMockedStatic = mockStatic(SQLUtils.class)) {

            // Mock连接和密码解密
            Connection conn = mock(Connection.class);
            managerMockedStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                .thenReturn(conn);
            passwdUtilMockedStatic.when(() -> PasswdUtil.decryptBase64(anyString())).thenReturn("decryptedPassword");

            // Mock SQLUtils.isLeaderBySqlQuery返回false
            sqlUtilsMockedStatic.when(() -> SQLUtils.isLeaderBySqlQuery(conn)).thenReturn(false);

            // 执行测试
            boolean result = service.checkLeaderByDN(storageInfo);

            // 验证结果
            Assert.assertFalse(result);
        }
    }

    @Test
    public void checkLeaderByDNTest_SQLException() throws SQLException {
        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);

        // 创建mock对象
        StorageInfo storageInfo = new StorageInfo();
        storageInfo.setStorageInstId("test-inst-id");
        storageInfo.setIp("127.0.0.1");
        storageInfo.setPort(3306);
        storageInfo.setUser("user");
        storageInfo.setPasswdEnc("password");

        // Mock依赖组件
        try (MockedStatic<DriverManager> managerMockedStatic = mockStatic(DriverManager.class);
            MockedStatic<PasswdUtil> passwdUtilMockedStatic = mockStatic(PasswdUtil.class);
            MockedStatic<SQLUtils> sqlUtilsMockedStatic = mockStatic(SQLUtils.class)) {

            // Mock连接和密码解密
            Connection conn = mock(Connection.class);
            managerMockedStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                .thenReturn(conn);
            passwdUtilMockedStatic.when(() -> PasswdUtil.decryptBase64(anyString())).thenReturn("decryptedPassword");

            // Mock SQLUtils.isLeaderBySqlQuery抛出SQLException
            sqlUtilsMockedStatic.when(() -> SQLUtils.isLeaderBySqlQuery(conn))
                .thenAnswer(invocation -> {
                    throw new SQLException("Test exception");
                });

            // 执行测试
            boolean result = service.checkLeaderByDN(storageInfo);

            // 验证结果
            Assert.assertFalse(result);
        }
    }

    @Test
    public void checkLeaderByDNTest_AccessDeniedException() throws SQLException {
        StorageInfoService service = SpringContextHolder.getObject(StorageInfoService.class);

        // 创建mock对象
        StorageInfo storageInfo = new StorageInfo();
        storageInfo.setStorageInstId("test-inst-id");
        storageInfo.setIp("127.0.0.1");
        storageInfo.setPort(3306);
        storageInfo.setUser("user");
        storageInfo.setPasswdEnc("password");

        // Mock依赖组件
        try (MockedStatic<DriverManager> managerMockedStatic = mockStatic(DriverManager.class);
            MockedStatic<PasswdUtil> passwdUtilMockedStatic = mockStatic(PasswdUtil.class);
            MockedStatic<SQLUtils> sqlUtilsMockedStatic = mockStatic(SQLUtils.class)) {

            // Mock连接和密码解密
            Connection conn = mock(Connection.class);
            managerMockedStatic.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                .thenReturn(conn);
            passwdUtilMockedStatic.when(() -> PasswdUtil.decryptBase64(anyString())).thenReturn("decryptedPassword");

            // Mock SQLUtils.isLeaderBySqlQuery抛出Access denied SQLException
            sqlUtilsMockedStatic.when(() -> SQLUtils.isLeaderBySqlQuery(conn))
                .thenAnswer(invocation -> {
                    throw new SQLException("Access denied for user 'user'@'127.0.0.1'");
                });

            // 执行测试
            boolean result = service.checkLeaderByDN(storageInfo);

            // 验证结果
            Assert.assertFalse(result);
        }
    }
}