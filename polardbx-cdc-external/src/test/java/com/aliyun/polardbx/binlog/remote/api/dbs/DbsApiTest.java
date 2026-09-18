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
import com.aliyun.polardbx.binlog.api.dbs.DescribeRestoreArchiveLogFilesResult;
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
    public void configBefore() {
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
        try (MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
            httpHelperMockedStatic.when(() -> HttpHelper.doGet(anyString(), any(), any())).thenReturn(
                JSON.toJSONString(result));

            // 调用被测函数
            DescribeUnifyArchiveLogFilesResult actualResult = DbsApi.describeUnifyArchiveLogFiles(
                "testInstance", "testUid", "testUserId", 1234567890L, 1234567891L, 10, 1);

            // 验证结果
            assertEquals(result, actualResult);
        }
    }

    @Test
    public void testSubmitDownloadTask() {
        RdsDownloadForRestoreResult result = new RdsDownloadForRestoreResult();
        result.setSuccess(true);
        result.setCode("200");
        result.setHttpStatusCode(200);
        RdsDownloadForRestoreResult.DownloadData data = new RdsDownloadForRestoreResult.DownloadData();
        data.setStatus("success");
        data.setTaskId(UUID.randomUUID().toString());
        result.setData(data);
        try (MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
            httpHelperMockedStatic.when(() -> HttpHelper.doGet(anyString(), any(), any())).thenReturn(
                JSON.toJSONString(result));

            // 调用被测函数
            RdsDownloadForRestoreResult actualResult = DbsApi.submitDownloadTask(
                "testInstance", "testUid", "testUserId", "archive_log_id", "archive_log_path");

            // 验证结果
            assertEquals(result, actualResult);
        }
    }

    @Test
    public void testDescribeTaskStatus() {

        DescribeTaskStatusResult result = new DescribeTaskStatusResult();
        result.setSuccess(true);
        result.setCode("200");
        result.setHttpStatusCode(200);
        DescribeTaskStatusResult.Data data = new DescribeTaskStatusResult.Data();
        data.setStatus("success");
        data.setTaskId(UUID.randomUUID().toString());
        result.setData(data);
        try (MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
            httpHelperMockedStatic.when(() -> HttpHelper.doGet(anyString(), any(), any(), anyBoolean())).thenReturn(
                JSON.toJSONString(result));

            // 调用被测函数
            DescribeTaskStatusResult actualResult = DbsApi.describeTaskStatus(
                "testInstance", "testUid", "testUserId", "archive_log_id");

            // 验证结果
            assertEquals(result, actualResult);
        }
    }

    @Test
    public void testCancelTask() {
        CancelTaskResult result = new CancelTaskResult();
        result.setSuccess(true);
        result.setCode("200");
        result.setHttpStatusCode(200);
        CancelTaskResult.Data data = new CancelTaskResult.Data();
        data.setStatus("success");
        data.setTaskId(UUID.randomUUID().toString());
        result.setData(data);
        try (MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
            httpHelperMockedStatic.when(() -> HttpHelper.doGet(anyString(), any(), any())).thenReturn(
                JSON.toJSONString(result));

            // 调用被测函数
            CancelTaskResult actualResult = DbsApi.cancelTask(
                "testInstance", "testUid", "testUserId", "archive_log_id");

            // 验证结果
            assertEquals(result, actualResult);
        }
    }

    @Test
    public void testDescribeStorageInfo() {
        DescribeStorageInfoResult result = new DescribeStorageInfoResult();
        result.setSuccess(true);
        result.setCode("200");
        result.setHttpStatusCode(200);
        StorageEntity entity = new StorageEntity();
        entity.setType("oss");
        result.setData(entity);
        result.setDataJson(JSON.toJSONString(entity));
        String expectedJson = "{\"Code\":\"200\",\"Data\":{\"type\":\"oss\"},\"HttpStatusCode\":200,\"Success\":true}";
        try (MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
            httpHelperMockedStatic.when(() -> HttpHelper.doGet(anyString(), any(), any())).thenReturn(
                expectedJson);

            // 调用被测函数
            DescribeStorageInfoResult actualResult = DbsApi.describeStorageInfo(
                "testInstance", "testUid", "testUserId");

            // 验证结果
            assertEquals(result, actualResult);
        }
    }

    /**
     * 测试 describeRestoreArchiveLogFiles 接口请求和响应
     */
    @Test
    public void testDescribeRestoreArchiveLogFiles() throws Exception {
        DescribeRestoreArchiveLogFilesResult result = new DescribeRestoreArchiveLogFilesResult();
        result.setSuccess(true);
        result.setCode("200");
        result.setHttpStatusCode(200);
        result.setRequestId("test-request-id");
        DescribeRestoreArchiveLogFilesResult.Data data = new DescribeRestoreArchiveLogFilesResult.Data();
        data.setRestoreTimeValid(true);
        data.setRestoreMessage("ok");
        ArchiveLogPages archiveLogPages = new ArchiveLogPages();
        archiveLogPages.setPageNumber(1);
        archiveLogPages.setPageSize(10);
        archiveLogPages.setTotalElements(2);
        archiveLogPages.setTotalPages(1);
        DbsBinlogFile binlogFile1 = new DbsBinlogFile();
        binlogFile1.setLogFileName("mysql-bin.000001");
        binlogFile1.setArchiveLogId("log-001");
        binlogFile1.setLogFileSize(1024L);
        binlogFile1.setHostInstanceId(100L);
        DbsBinlogFile binlogFile2 = new DbsBinlogFile();
        binlogFile2.setLogFileName("mysql-bin.000002");
        binlogFile2.setArchiveLogId("log-002");
        binlogFile2.setLogFileSize(2048L);
        binlogFile2.setHostInstanceId(100L);
        archiveLogPages.setContent(Arrays.asList(binlogFile1, binlogFile2));
        data.setArchiveLogInfo(archiveLogPages);
        result.setData(data);

        try (MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
            httpHelperMockedStatic.when(() -> HttpHelper.doGet(anyString(), any(), any())).thenReturn(
                JSON.toJSONString(result));

            // 调用被测函数
            DescribeRestoreArchiveLogFilesResult actualResult = DbsApi.describeRestoreArchiveLogFiles(
                "testInstance", "testUid", "testUserId", 1234567890L, 1234567891L, 10, 1);

            // 验证结果
            assertEquals(true, actualResult.isSuccess());
            assertEquals("200", actualResult.getCode());
            assertEquals(2, actualResult.getData().getArchiveLogInfo().getContent().size());
            assertEquals("mysql-bin.000001",
                actualResult.getData().getArchiveLogInfo().getContent().get(0).getLogFileName());
            assertEquals("mysql-bin.000002",
                actualResult.getData().getArchiveLogInfo().getContent().get(1).getLogFileName());
            assertEquals(true, actualResult.getData().isRestoreTimeValid());
        }
    }

    /**
     * 测试 describeRestoreArchiveLogFiles 接口返回空数据
     */
    @Test
    public void testDescribeRestoreArchiveLogFiles_EmptyResult() throws Exception {
        DescribeRestoreArchiveLogFilesResult result = new DescribeRestoreArchiveLogFilesResult();
        result.setSuccess(true);
        result.setCode("200");
        result.setHttpStatusCode(200);
        DescribeRestoreArchiveLogFilesResult.Data data = new DescribeRestoreArchiveLogFilesResult.Data();
        data.setRestoreTimeValid(false);
        data.setRestoreMessage("no data");
        ArchiveLogPages archiveLogPages = new ArchiveLogPages();
        archiveLogPages.setPageNumber(1);
        archiveLogPages.setPageSize(10);
        archiveLogPages.setTotalElements(0);
        archiveLogPages.setContent(Arrays.asList());
        data.setArchiveLogInfo(archiveLogPages);
        result.setData(data);

        try (MockedStatic<HttpHelper> httpHelperMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
            httpHelperMockedStatic.when(() -> HttpHelper.doGet(anyString(), any(), any())).thenReturn(
                JSON.toJSONString(result));

            DescribeRestoreArchiveLogFilesResult actualResult = DbsApi.describeRestoreArchiveLogFiles(
                "testInstance", "testUid", "testUserId", 1234567890L, 1234567891L, 10, 1);

            assertEquals(true, actualResult.isSuccess());
            assertEquals(0, actualResult.getData().getArchiveLogInfo().getContent().size());
            assertEquals(false, actualResult.getData().isRestoreTimeValid());
        }
    }

}