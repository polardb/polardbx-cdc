/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

public class DownloadTaskTest extends BaseTest {
    @Test
    public void testDownload() throws Exception {
        try (MockedStatic<HttpHelper> helperMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
//            helperMockedStatic.when(()->HttpHelper.download(Mockito.anyString(), Mockito.anyString()));
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setDownloadLink("http://1/mysql_bin.000001");
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
            binlogFile.setDownloadLink("http://1/mysql_bin.000001");
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
}
