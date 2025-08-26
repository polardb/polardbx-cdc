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
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
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
}
