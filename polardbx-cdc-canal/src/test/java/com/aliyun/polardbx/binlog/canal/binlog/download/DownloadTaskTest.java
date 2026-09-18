/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.download.action.DownloadActionFactory;
import com.aliyun.polardbx.binlog.canal.binlog.download.action.IDownloadAction;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.DecompressCmd;
import com.aliyun.polardbx.binlog.util.DecompressCmdFactory;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;

public class DownloadTaskTest extends BaseTest {
    @Test
    public void testDownload() throws Exception {
        try (MockedStatic<HttpHelper> helperMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
//            helperMockedStatic.when(()->HttpHelper.download(Mockito.anyString(), Mockito.anyString()));
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setIntranetDownloadLink("http://1/mysql_bin.000001");
            binlogFile.setLogname("mysql_bin.000001");
            DownloadTask downloadTask = new DownloadTask("test-instance", binlogFile, "test-instance");
            DownloadTaskListener listener = Mockito.mock(DownloadTaskListener.class);
            downloadTask.registerListener(listener);
            downloadTask.run();
            helperMockedStatic.verify(() -> HttpHelper.download(Mockito.anyString(), Mockito.anyString()),
                Mockito.times(1));
            Mockito.verify(listener, Mockito.times(1)).beginDownload("test-instance");
            Mockito.verify(listener, Mockito.times(1)).endDownload("test-instance");
        }
    }

    @Test
    public void testDownloadTaskWithException() {
        try (MockedStatic<HttpHelper> helperMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
            PolardbxException exception = new PolardbxException("download failed");
            helperMockedStatic.when(() -> HttpHelper.download(Mockito.anyString(), Mockito.anyString()))
                .thenThrow(exception);
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setIntranetDownloadLink("http://1/mysql_bin.000001");
            binlogFile.setLogname("mysql_bin.000001");
            DownloadTask downloadTask = new DownloadTask("test-instance", binlogFile, "test-instance");
            DownloadTaskListener listener = Mockito.mock(DownloadTaskListener.class);
            downloadTask.registerListener(listener);
            downloadTask.run();
            helperMockedStatic.verify(() -> HttpHelper.download(Mockito.anyString(), Mockito.anyString()),
                Mockito.times(4));
            Mockito.verify(listener, Mockito.times(4)).beginDownload("test-instance");
            Mockito.verify(listener, Mockito.times(4)).endDownload("test-instance");
            Mockito.verify(listener, Mockito.times(1)).catchException(exception);

        }
    }

    @Test
    public void testDownloadWithZstdCmd() throws Exception {
        try (MockedStatic<DownloadActionFactory> downloadMockedStatic = Mockito.mockStatic(DownloadActionFactory.class);
            MockedStatic<DecompressCmdFactory> decompressCmdFactoryMockedStatic = Mockito.mockStatic(
                DecompressCmdFactory.class)) {
            DecompressCmd cmd = Mockito.mock(DecompressCmd.class);
            decompressCmdFactoryMockedStatic.when(
                () -> DecompressCmdFactory.create(Mockito.anyString(), Mockito.anyString())).thenReturn(cmd);
            IDownloadAction action = Mockito.mock(IDownloadAction.class);
            downloadMockedStatic.when(() -> DownloadActionFactory.create()).thenReturn(action);
            BinlogFile binlogFile = new BinlogFile();
            String fileName = "mysql_zst_bin.0001";
            String filePath = DownloadTaskTest.class.getResource("/").getPath();
            String url = String.format("http://1/%s.zst", fileName);
            String localFilePath = filePath + fileName;
            String localFilePathZst = filePath + fileName + ".zst";
            FileUtils.deleteQuietly(new File(localFilePath));
            FileUtils.deleteQuietly(new File(localFilePathZst));
            binlogFile.setIntranetDownloadLink(url);
            binlogFile.setLogname(fileName);
            DownloadTask downloadTask = new DownloadTask("test-instance", binlogFile, filePath + fileName);
            DownloadTaskListener listener = Mockito.mock(DownloadTaskListener.class);
            downloadTask.registerListener(listener);
            downloadTask.exec();
            decompressCmdFactoryMockedStatic.verify(() -> DecompressCmdFactory.create(localFilePathZst, localFilePath),
                Mockito.times(1));
            Mockito.verify(cmd, Mockito.times(1)).execute();
        }
    }
}
