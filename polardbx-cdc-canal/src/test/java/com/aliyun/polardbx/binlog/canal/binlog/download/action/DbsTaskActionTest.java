/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download.action;

import com.aliyun.polardbx.binlog.api.DbsApi;
import com.aliyun.polardbx.binlog.api.dbs.DescribeTaskStatusResult;
import com.aliyun.polardbx.binlog.api.dbs.RdsDownloadForRestoreResult;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.Shell;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;

public class DbsTaskActionTest extends BaseTest {

    private DescribeTaskStatusResult mockProgressResult(int progress){
        DescribeTaskStatusResult result = new DescribeTaskStatusResult();
        DescribeTaskStatusResult.Data data = new DescribeTaskStatusResult.Data();
        data.setStatus("success");
        data.setTaskId(UUID.randomUUID().toString());
        data.setProgress(progress);
        result.setData(data);
        return result;
    }

    @Test
    public void testDownload() throws Exception {

        try(MockedStatic<DbsApi> dbsApiMockedStatic = Mockito.mockStatic(DbsApi.class);
        MockedStatic<Shell> shellMockedStatic = Mockito.mockStatic(Shell.class);
        MockedStatic<FileUtils> fileUtilsMockedStatic = Mockito.mockStatic(FileUtils.class)){
            RdsDownloadForRestoreResult submitResult = new RdsDownloadForRestoreResult();
            RdsDownloadForRestoreResult.DownloadData downloadData = new RdsDownloadForRestoreResult.DownloadData();
            downloadData.setTaskId(UUID.randomUUID().toString());
            downloadData.setStatus("OK");
            submitResult.setData(downloadData);
            dbsApiMockedStatic.when(()->DbsApi.submitDownloadTask(anyString(),anyString(),anyString(),anyString(),anyString())).thenReturn(submitResult);
            dbsApiMockedStatic.when(()->DbsApi.describeTaskStatus(anyString(),anyString(),anyString(),anyString())).thenReturn(mockProgressResult(0),mockProgressResult(50),mockProgressResult(100));
            dbsApiMockedStatic.when(()->DbsApi.cancelTask(anyString(),anyString(),anyString(),anyString())).thenReturn(null);
            shellMockedStatic.when(()->Shell.execCommand(anyString(),anyString(),anyString(), anyString(), anyString())).thenReturn("success");
//            fileUtilsMockedStatic.when(()->FileUtils.moveFile(any(), any())).thenReturn(null);
            DbsTaskAction action = new DbsTaskAction();
            String storageInstance = "pxc-s-s-s-s";
            String localPath = "/a/b/c/mysql-bin.0001";
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setLogname("mysql-bin.0001");
            binlogFile.setArchiveLogId("1111");
            action.exec(storageInstance, localPath, binlogFile);

            dbsApiMockedStatic.verify(()->DbsApi.submitDownloadTask(anyString(),anyString(),anyString(),anyString(),anyString()), Mockito.times(1));
            dbsApiMockedStatic.verify(()->DbsApi.describeTaskStatus(anyString(),anyString(),anyString(),anyString()), Mockito.times(3));
            dbsApiMockedStatic.verify(()->DbsApi.cancelTask(anyString(),anyString(),anyString(),anyString()), Mockito.times(0));
        }

    }
}
