/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTask;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTaskFactory;
import com.aliyun.polardbx.binlog.canal.binlog.download.StorageDownloader;
import com.aliyun.polardbx.binlog.canal.exception.ConsumeOSSBinlogEndException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.io.file.Counters;
import org.apache.commons.io.file.PathUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.util.LinkedList;

public class ContinuesFileLogFetcherTest extends BaseTest {

    @Test(expected = PolardbxException.class)
    public void continueFileTest() throws IOException {
        FileLogFetcher fileLogFetcher = Mockito.mock(FileLogFetcher.class);
        String localPath = ContinuesFileLogFetcherTest.class.getResource("/").getPath() + "/test-instance";
        LinkedList<BinlogFile> binlogFileQueue = new LinkedList<>();
        BinlogFile binlogFile1 = new BinlogFile();
        binlogFile1.setLogname("my-bin.01");
        BinlogFile binlogFile2 = new BinlogFile();
        binlogFile2.setLogname("my-bin.02");
        binlogFileQueue.add(binlogFile1);
        binlogFileQueue.add(binlogFile2);
        StorageDownloader downloader = Mockito.mock(StorageDownloader.class);
        Mockito.when(fileLogFetcher.fetch()).thenReturn(false);
        Mockito.when(downloader.getException()).thenReturn(new PolardbxException());
        ContinuesFileLogFetcher fetcher =
            new ContinuesFileLogFetcher("test-instance", fileLogFetcher, localPath, binlogFile1, binlogFileQueue,
                downloader);
        fetcher.fetch();
    }

    @Test
    public void ossConsumeEndExceptionTest() throws IOException {
        FileLogFetcher fileLogFetcher = Mockito.mock(FileLogFetcher.class);
        String localPath = ContinuesFileLogFetcherTest.class.getResource("/").getPath() + "/test-instance";
        LinkedList<BinlogFile> binlogFileQueue = new LinkedList<>();
        BinlogFile binlogFile1 = new BinlogFile();
        binlogFile1.setLogname("my-bin.01");
        binlogFileQueue.add(binlogFile1);
        StorageDownloader downloader = Mockito.mock(StorageDownloader.class);
        Mockito.when(fileLogFetcher.fetch()).thenReturn(false);
        Mockito.when(downloader.getException()).thenReturn(new PolardbxException());
        Logger logger = Mockito.mock(Logger.class);
        ContinuesFileLogFetcher fetcher =
            new ContinuesFileLogFetcher("test-instance", fileLogFetcher, localPath, binlogFile1, binlogFileQueue,
                downloader);
        fetcher.setLogger(logger);
        Throwable t = null;
        try {
            fetcher.fetch();
        } catch (Exception e) {
            t = e;
        }
        Assert.assertNotNull(t);
        Assert.assertEquals(ConsumeOSSBinlogEndException.class, t.getClass());
        Mockito.verify(logger, Mockito.times(1))
            .info("last file {} finish, will trigger oss end and try direct consume!", binlogFile1.getLogname());
    }

    @Test
    public void fetcherTest() throws Exception {
        try (MockedStatic<PathUtils> mockedStaticFileUtils = Mockito.mockStatic(PathUtils.class);
            MockedStatic<DownloadTaskFactory> mockedStaticDownloadTaskFactory = Mockito.mockStatic(
                DownloadTaskFactory.class)) {
            Counters.PathCounters counters = Mockito.mock(Counters.PathCounters.class);
            Counters.Counter counter = Mockito.mock(Counters.Counter.class);
            Mockito.when(counter.get()).thenReturn(1L);
            Mockito.when(counters.getFileCounter()).thenReturn(counter);
            mockedStaticFileUtils.when(() -> PathUtils.delete(Mockito.any())).thenReturn(counters);
            DownloadTask downloadTask = Mockito.mock(DownloadTask.class);
            Mockito.doThrow(new RuntimeException("download failed")).when(downloadTask).exec();
            mockedStaticDownloadTaskFactory.when(
                () -> DownloadTaskFactory.createDownloadTask(Mockito.anyString(), Mockito.any(),
                    Mockito.anyString())).thenReturn(downloadTask);
            FileLogFetcher fetcher = Mockito.mock(FileLogFetcher.class);
            Mockito.when(fetcher.fetch()).thenReturn(false);
            LinkedList<BinlogFile> binlogFileQueue = new LinkedList<>();
            BinlogFile binlogFile1 = new BinlogFile();
            binlogFile1.setInstanceID(1L);
            binlogFile1.setFileSize(1024L);
            binlogFile1.setServerId(1L);
            binlogFile1.setIntranetDownloadLink("1.1.1.1");
            binlogFile1.setLogname("my-bin.01");
            binlogFileQueue.add(binlogFile1);
            BinlogFile binlogFile2 = new BinlogFile();
            binlogFile2.setInstanceID(1L);
            binlogFile2.setFileSize(1024L);
            binlogFile2.setServerId(1L);
            binlogFile2.setIntranetDownloadLink("1.1.1.1");
            binlogFile2.setLogname("my-bin.02");
            binlogFileQueue.add(binlogFile2);
            Logger logger = Mockito.mock(Logger.class);
            ContinuesFileLogFetcher continuesFileLogFetcher = Mockito.mock(ContinuesFileLogFetcher.class,
                Mockito.withSettings()
                    .useConstructor("test-instance", fetcher, "test-path", binlogFile1, binlogFileQueue, null));
            Mockito.doCallRealMethod().when(continuesFileLogFetcher).setLogger(Mockito.any());
            continuesFileLogFetcher.setLogger(logger);
            Mockito.when(continuesFileLogFetcher.fetch()).thenCallRealMethod();
            Throwable t = null;
            try {
                boolean ret = continuesFileLogFetcher.fetch();
            } catch (Exception e) {
                t = e;
            }
            Assert.assertNotNull(t);
            Assert.assertEquals(PolardbxException.class, t.getClass());
            String nextFilePath = "test-path" + File.separator + binlogFile2.getLogname();
            Assert.assertEquals("download file " + nextFilePath + " failed!", t.getMessage());
            Mockito.verify(logger, Mockito.times(1))
                .info("last file {} finish , rotate to new binlog file {}", binlogFile1.getLogname(),
                    binlogFile2.getLogname());

        }

    }
}
