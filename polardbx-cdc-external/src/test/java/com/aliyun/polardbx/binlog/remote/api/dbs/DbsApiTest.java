/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api.dbs;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.api.DbsApi;
import com.aliyun.polardbx.binlog.api.dbs.ArchiveLogPages;
import com.aliyun.polardbx.binlog.api.dbs.CancelTaskResult;
import com.aliyun.polardbx.binlog.api.dbs.DbsBinlogFile;
import com.aliyun.polardbx.binlog.api.dbs.DescribeStorageInfoResult;
import com.aliyun.polardbx.binlog.api.dbs.DescribeTaskStatusResult;
import com.aliyun.polardbx.binlog.api.dbs.DescribeUnifyArchiveLogFilesResult;
import com.aliyun.polardbx.binlog.api.dbs.RdsDownloadForRestoreResult;
import com.aliyun.polardbx.binlog.api.dbs.StorageEntity;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;

public class DbsApiTest extends BaseTest {

    @Before
    public void configBefore(){
        // 模拟配置信息
        mockConfig(ConfigKeys.DBS_API_URL, "http://api.example.com");
        mockConfig(ConfigKeys.DBS_API_ACCESS_ID, "testAk");
        mockConfig(ConfigKeys.DBS_API_ACCESS_KEY, "testSk");
        mockConfig(ConfigKeys.DBS_REGION_CODE, "cn-hangzhou");
        mockConfig(ConfigKeys.INST_ID, "1111");
    }

    @Test
    public void testDescribeUnifyArchiveLogFiles_Success() throws Exception {
        DescribeUnifyArchiveLogFilesResult result = new DescribeUnifyArchiveLogFilesResult();
        result.setSuccess("success");
        result.setCode("200");
        result.setHttpStatusCode("200");
        ArchiveLogPages pages = new ArchiveLogPages();
        DbsBinlogFile binlogFile = new DbsBinlogFile();
        binlogFile.setLogFileName("test");
        pages.setContent(Arrays.asList(binlogFile));
        result.setData(pages);
        try(MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)){
            httpHelperMockedStatic.when(()->HttpHelper.doGet(anyString(), any(), any())).thenReturn(
                JSON.toJSONString(result));

            // 调用被测函数
            DescribeUnifyArchiveLogFilesResult actualResult = DbsApi.describeUnifyArchiveLogFiles(
                "testInstance", "testUid", "testUserId", 1234567890L, 1234567891L, 10, 1);

            // 验证结果
            assertEquals(result, actualResult);
        }
    }

    @Test
    public void testSubmitDownloadTask(){
        RdsDownloadForRestoreResult result = new RdsDownloadForRestoreResult();
        result.setSuccess(true);
        result.setCode("200");
        result.setHttpStatusCode(200);
        RdsDownloadForRestoreResult.DownloadData data = new RdsDownloadForRestoreResult.DownloadData();
        data.setStatus("success");
        data.setTaskId(UUID.randomUUID().toString());
        result.setData(data);
        try(MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)){
            httpHelperMockedStatic.when(()->HttpHelper.doGet(anyString(), any(), any())).thenReturn(
                JSON.toJSONString(result));

            // 调用被测函数
            RdsDownloadForRestoreResult actualResult = DbsApi.submitDownloadTask(
                "testInstance", "testUid", "testUserId", "archive_log_id", "archive_log_path");

            // 验证结果
            assertEquals(result, actualResult);
        }
    }

    @Test
    public void testDescribeTaskStatus(){

        DescribeTaskStatusResult result = new DescribeTaskStatusResult();
        result.setSuccess(true);
        result.setCode("200");
        result.setHttpStatusCode(200);
        DescribeTaskStatusResult.Data data = new DescribeTaskStatusResult.Data();
        data.setStatus("success");
        data.setTaskId(UUID.randomUUID().toString());
        result.setData(data);
        try(MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)){
            httpHelperMockedStatic.when(()->HttpHelper.doGet(anyString(), any(), any(), anyBoolean())).thenReturn(
                JSON.toJSONString(result));

            // 调用被测函数
            DescribeTaskStatusResult actualResult = DbsApi.describeTaskStatus(
                "testInstance", "testUid", "testUserId", "archive_log_id");

            // 验证结果
            assertEquals(result, actualResult);
        }
    }

    @Test
    public void testCancelTask(){
        CancelTaskResult result = new CancelTaskResult();
        result.setSuccess(true);
        result.setCode("200");
        result.setHttpStatusCode(200);
        CancelTaskResult.Data data = new CancelTaskResult.Data();
        data.setStatus("success");
        data.setTaskId(UUID.randomUUID().toString());
        result.setData(data);
        try(MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)){
            httpHelperMockedStatic.when(()->HttpHelper.doGet(anyString(), any(), any())).thenReturn(
                JSON.toJSONString(result));

            // 调用被测函数
            CancelTaskResult actualResult = DbsApi.cancelTask(
                "testInstance", "testUid", "testUserId", "archive_log_id");

            // 验证结果
            assertEquals(result, actualResult);
        }
    }


    @Test
    public void testDescribeStorageInfo(){
        DescribeStorageInfoResult result = new DescribeStorageInfoResult();
        result.setSuccess(true);
        result.setCode("200");
        result.setHttpStatusCode(200);
        StorageEntity entity = new StorageEntity();
        entity.setType("oss");
        result.setData(entity);
        result.setDataJson(JSON.toJSONString(entity));
        String expectedJson = "{\"Code\":\"200\",\"Data\":{\"type\":\"oss\"},\"HttpStatusCode\":200,\"Success\":true}";
        try(MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)){
            httpHelperMockedStatic.when(()->HttpHelper.doGet(anyString(), any(), any())).thenReturn(
                expectedJson);

            // 调用被测函数
            DescribeStorageInfoResult actualResult = DbsApi.describeStorageInfo(
                "testInstance", "testUid", "testUserId");

            // 验证结果
            assertEquals(result, actualResult);
        }
    }



}