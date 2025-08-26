/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download.action;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.api.DbsApi;
import com.aliyun.polardbx.binlog.api.dbs.DescribeStorageInfoResult;
import com.aliyun.polardbx.binlog.api.dbs.StorageEntity;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.Shell;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

public class GarethActionTest extends BaseTest {

    @Test
    public void testGarethAction() throws Exception {
        try(MockedStatic<Shell> shellMockedStatic = mockStatic(Shell.class)){
            shellMockedStatic.when(()->Shell.execCommand(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("success");
            mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_CONFIG, "{\"uid\":\"223274842729957349\",\"protocol\":\"nfs\",\"protocolVersion\":\"v4\",\"type\":\"nas\",\"region\":\"cn-beijing\",\"originalIp\":\"10.0.109.210\",\"originalPort\":\"2049\",\"mountpoint\":\"/apsaradb/test\"}");
            mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_POOL_TYPE, "nas");
            GarethAction action = new GarethAction();
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setDownloadLink("test-download");
            action.exec("test-dn", "localPath", binlogFile);
            shellMockedStatic.verify(()->Shell.execCommand(anyString(), anyString(), anyString(), anyString(), anyString()), times(1));
        }
    }

    @Test
    public void testGarethAction2() throws Exception {
        try(MockedStatic<Shell> shellMockedStatic = mockStatic(Shell.class);
            MockedStatic<DbsApi> dbsApiMockedStatic = mockStatic(DbsApi.class)){
            shellMockedStatic.when(()->Shell.execCommand(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("success");
            DescribeStorageInfoResult result = new DescribeStorageInfoResult();
            result.setSuccess(true);
            result.setData(new StorageEntity());
            result.getData().setType("nas");
            result.setDataJson("{\"uid\":\"223274842729957349\",\"protocol\":\"nfs\",\"protocolVersion\":\"v4\",\"type\":\"nas\",\"region\":\"cn-beijing\",\"originalIp\":\"10.0.109.210\",\"originalPort\":\"2049\",\"mountpoint\":\"/apsaradb/test\"}");
            dbsApiMockedStatic.when(()->DbsApi.describeStorageInfo(anyString(), anyString(), anyString())).thenReturn(result);
            mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_CONFIG, "");
            mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_POOL_TYPE, "");
            GarethAction action = new GarethAction();
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setDownloadLink("test-download");
            binlogFile.setStorageEntityId("test");
            action.exec("test-dn", "localPath", binlogFile);
            shellMockedStatic.verify(()->Shell.execCommand(anyString(), anyString(), anyString(), anyString(), anyString()), times(1));
        }
    }
}
