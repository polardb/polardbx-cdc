/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.dump;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.api.BinlogProcessor;
import com.aliyun.polardbx.binlog.api.DbsApi;
import com.aliyun.polardbx.binlog.api.DescribeBinlogFilesResult;
import com.aliyun.polardbx.binlog.api.RdsApi;
import com.aliyun.polardbx.binlog.api.dbs.DescribeStorageInfoResult;
import com.aliyun.polardbx.binlog.api.dbs.DescribeTaskStatusResult;
import com.aliyun.polardbx.binlog.api.dbs.RdsDownloadForRestoreResult;
import com.aliyun.polardbx.binlog.api.dbs.StorageEntity;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheManager;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTask;
import com.aliyun.polardbx.binlog.canal.binlog.download.DownloadTaskFactory;
import com.aliyun.polardbx.binlog.canal.binlog.download.StorageDownloader;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.ContinuesFileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.ContinuesURLLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcherFactory;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.URLLogFetcher;
import com.aliyun.polardbx.binlog.canal.exception.PositionNotFoundException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.HttpHelper;
import com.aliyun.polardbx.binlog.util.Shell;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.MessageFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_API_ACCESS_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_API_ACCESS_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.RDS_API_URL;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class OssConnectionTest extends BaseTest {

    @Test
    public void testUTC() throws ParseException {
        String utfHost = RdsApi.formatUTCTZ(new Date(1651782957000L));
        String utcBegin = RdsApi.formatUTCTZ(new Date(1651569048284L));
        System.out.println(utcBegin);
        System.out.println(BinlogFile.format(utcBegin));
        System.out.println(utfHost);
        System.out.println(BinlogFile.format(utfHost));

    }

    @Test
    public void testConnectWithSpecifyTso() throws Exception {
        mockConfig(TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE, "false");

        OssConnection connection = Mockito.mock(OssConnection.class,
            withSettings().useConstructor("storageInstanceId", "", "", "", 1L, 1L, 1L));
        when(connection.isInit()).thenReturn(false);
        when(connection.beginTimestamp()).thenReturn(System.currentTimeMillis());
        Mockito.doNothing().when(connection).connectBefore();
        Mockito.doNothing().when(connection).filterBinlogList(anyList());
        Mockito.doCallRealMethod().when(connection).connect();
        when(connection.callRdsApi(anyInt(), anyInt())).thenReturn(new ArrayList<>());
        connection.connect();
        connection.disconnect();

        verify(connection, times(1)).connectBefore();
        verify(connection, times(1)).callRdsApi(anyLong(), anyLong());
        verify(connection, times(1)).filterBinlogList(anyList());

    }

    @Test
    public void testConnectBeginTimestamp() {
        mockConfig(TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE, "false");
        long now = System.currentTimeMillis();
        OssConnection connection = new OssConnection("", "", "", "", 100242035L, null, now);

        long expected = now - TimeUnit.HOURS.toMillis(12);
        Assert.assertEquals(expected, connection.beginTimestamp());
    }

    @Test(expected = PositionNotFoundException.class)
    public void providerFetcherWithExceptionTest() throws IOException {
        OssConnection connection = Mockito.mock(OssConnection.class);
        when(connection.providerFetcher(anyString(), anyLong(), anyBoolean())).thenCallRealMethod();
        when(connection.getLastLogName()).thenReturn("my.01");
        when(connection.getBinlogFile(anyString())).thenReturn(null);
        connection.providerFetcher("my.01", 0, true);
    }

    @Test
    public void providerFetcherWithLocalTest() throws IOException {
        mockConfig(TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE, "true");
        OssConnection connection = Mockito.mock(OssConnection.class);
        when(connection.providerFetcher(anyString(), anyLong(), anyBoolean())).thenCallRealMethod();
        when(connection.getLastLogName()).thenReturn("my.01");
        when(connection.getBinlogFile(anyString())).thenReturn(new BinlogFile());
        when(connection.providerLocalFetcher(any(), anyLong(), anyBoolean())).thenReturn(
            Mockito.mock(ContinuesFileLogFetcher.class));
        when(connection.providerContinuesRemoteUrlFetcher(any(), anyLong())).thenReturn(
            Mockito.mock(ContinuesURLLogFetcher.class));

        LogFetcher fetcher = connection.providerFetcher("my.01", 0, true);
        Assert.assertEquals(ContinuesFileLogFetcher.class, fetcher.getClass());
    }

    @Test
    public void providerFetcherWithRemoteTest() throws IOException {
        mockConfig(TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE, "false");
        OssConnection connection = Mockito.mock(OssConnection.class);
        when(connection.providerFetcher(anyString(), anyLong(), anyBoolean())).thenCallRealMethod();
        when(connection.getLastLogName()).thenReturn("my.01");
        when(connection.getBinlogFile(anyString())).thenReturn(new BinlogFile());
        when(connection.providerLocalFetcher(any(), anyLong(), anyBoolean())).thenReturn(
            Mockito.mock(ContinuesFileLogFetcher.class));
        when(connection.providerContinuesRemoteUrlFetcher(any(), anyLong())).thenReturn(
            Mockito.mock(ContinuesURLLogFetcher.class));

        LogFetcher fetcher = connection.providerFetcher("my.01", 0, true);
        Assert.assertEquals(ContinuesURLLogFetcher.class, fetcher.getClass());
    }

    @Test
    public void binlogListTest() throws NoSuchFieldException, IllegalAccessException {
        OssConnection connection = new OssConnection("", "", "", "", 100242035L, null, 0L);
        Field binlogFileQueueField = OssConnection.class.getDeclaredField("binlogFileQueue");
        binlogFileQueueField.setAccessible(true);
        LinkedList<BinlogFile> binlogFileQueue = (LinkedList<BinlogFile>) binlogFileQueueField.get(connection);
        BinlogFile bf1 = new BinlogFile();
        bf1.setLogname("a");
        binlogFileQueue.add(bf1);
        BinlogFile bf2 = new BinlogFile();
        bf2.setLogname("b");
        binlogFileQueue.add(bf2);
        List<String> binlogList = connection.binlogList();
        Assert.assertEquals(binlogList, Arrays.asList("a", "b"));
    }

    @Test
    public void serverIdMatchTest() throws NoSuchFieldException, IllegalAccessException {
        OssConnection connection = new OssConnection("", "", "", "", 100242035L, 1L, 0L);
        Field binlogFileQueueField = OssConnection.class.getDeclaredField("ossBinlogFileMap");
        binlogFileQueueField.setAccessible(true);
        HashMap<String, BinlogFile> ossBinlogFileMap =
            (HashMap<String, BinlogFile>) binlogFileQueueField.get(connection);
        BinlogFile bf1 = new BinlogFile();
        bf1.setLogname("a");
        bf1.setServerId(1L);
        ossBinlogFileMap.put("a", bf1);
        BinlogFile bf2 = new BinlogFile();
        bf2.setLogname("b");
        bf2.setServerId(1L);
        ossBinlogFileMap.put("b", bf2);
        Assert.assertTrue(connection.isServerIdMatch());
    }

    @Test
    public void providerRemoteUrlFetcherTest() throws IOException {
        // Mock the behavior of BinlogFile
        String intranetDownloadLink = "http://intranet.example.com/test";
        String downloadLink = "http://example.com/test";
        long fileSize = 1024L;
        long binlogPosition = 0L;
        OssConnection ossConnection = Mockito.mock(OssConnection.class);
        URLLogFetcher urlLogFetcher = Mockito.mock(URLLogFetcher.class);
        BinlogFile ossBinlogFile = Mockito.mock(BinlogFile.class);
        when(ossBinlogFile.getIntranetDownloadLink()).thenReturn(intranetDownloadLink);
        when(ossBinlogFile.getDownloadLink()).thenReturn(downloadLink);
        when(ossBinlogFile.getFileSize()).thenReturn(fileSize);
        try (MockedStatic<LogFetcherFactory> logFetcherFactoryMock = Mockito.mockStatic(LogFetcherFactory.class)) {
            logFetcherFactoryMock.when(() -> LogFetcherFactory.createURLLogFetcher(any(), any()))
                .thenReturn(urlLogFetcher);
            // Mock the behavior of URLLogFetcher
            when(ossConnection.providerContinuesRemoteUrlFetcher(ossBinlogFile, binlogPosition)).thenCallRealMethod();
            when(ossConnection.providerRemoteUrlFetcher(ossBinlogFile, binlogPosition)).thenCallRealMethod();

            // Call the method under test
            LogFetcher result = ossConnection.providerContinuesRemoteUrlFetcher(ossBinlogFile, binlogPosition);

            // Verify interactions
            verify(urlLogFetcher).open(anyString(), anyLong(), anyLong(), any());

            // Verify the result
            Assert.assertEquals(ContinuesURLLogFetcher.class, result.getClass());
        }
    }

    @Test
    public void testProviderLocalFetcher_Success() throws Exception {
        // Mock the behavior of BinlogFile
        long binlogPosition = 0L;

        BinlogFile binlogFile = new BinlogFile();
        binlogFile.setLogname("a");
        binlogFile.setIntranetDownloadLink("download-link");
        binlogFile.setDownloadLink("download-link");
        binlogFile.setBeginTime(0L);
        binlogFile.setEndTime(10L);
        binlogFile.setFileSize(1024L);

        BinlogFile binlogFile1 = new BinlogFile();
        binlogFile1.setLogname("b");
        binlogFile1.setIntranetDownloadLink("download-link");
        binlogFile1.setDownloadLink("download-link");
        binlogFile1.setBeginTime(0L);
        binlogFile1.setEndTime(10L);
        binlogFile1.setFileSize(1024L);

        LinkedList<BinlogFile> binlogFileQueue = new LinkedList<>();
        binlogFileQueue.add(binlogFile);
        binlogFileQueue.add(binlogFile1);
        OssConnection ossConnection = Mockito.mock(OssConnection.class);

        try (MockedStatic<DownloadTaskFactory> downloadTaskFactory = Mockito.mockStatic(DownloadTaskFactory.class)) {
            // Mock the behavior of DownloadTaskFactory
            DownloadTask downloadTask = Mockito.mock(DownloadTask.class);
            downloadTaskFactory.when(() -> DownloadTaskFactory.createDownloadTask(any(), any(), anyString()))
                .thenReturn(downloadTask);

            // Mock the behavior of FileLogFetcher
            FileLogFetcher fetcher = Mockito.mock(FileLogFetcher.class);
            when(ossConnection.createFileLogFetcher()).thenReturn(fetcher);
            when(ossConnection.providerLocalFetcher(binlogFile, binlogPosition, false)).thenCallRealMethod();
            when(ossConnection.getBinlogFileQueue()).thenReturn(binlogFileQueue);

            StorageDownloader storageDownloader = Mockito.mock(StorageDownloader.class);
            when(ossConnection.createNewDownloader()).thenReturn(storageDownloader);

            // Call the method under test
            LogFetcher result = ossConnection.providerLocalFetcher(binlogFile, binlogPosition, false);

            // Verify interactions
            verify(downloadTask).exec();
            verify(fetcher).open(anyString(), eq(binlogPosition));

            // Verify the result
            Assert.assertEquals(ContinuesFileLogFetcher.class, result.getClass());
        }
    }

    @Test
    public void testCallApiTest() throws Exception {
        mockConfig(RDS_API_URL, "http");
        mockConfig(RDS_API_ACCESS_KEY, "access_key");
        mockConfig(RDS_API_ACCESS_ID, "access_id");
        OssConnection ossConnection = Mockito.mock(OssConnection.class,
            withSettings().useConstructor("pxc-aaa", "122222", "6655", "", 100242035L, 1L, 0L));
        try (MockedStatic<HttpHelper> httpMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
            DescribeBinlogFilesResult result = new DescribeBinlogFilesResult();
            List<BinlogFile> binlogFileList = new ArrayList<>();
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setLogname("a");
            binlogFile.setIntranetDownloadLink("download-link");
            binlogFile.setDownloadLink("download-link");
            binlogFile.setFileSize(1024L);
            binlogFileList.add(binlogFile);
            result.setItems(binlogFileList);
            result.setTotalRecords(binlogFileList.size());
            result.setItemsNumbers(binlogFileList.size());
            httpMockedStatic.when(() -> HttpHelper.get(anyString(), anyInt())).thenReturn(
                "{\"Code\":200,\"Data\":{\"Items\":" + JSON.toJSONString(result.getItems()) + ",\"TotalRecords\":"
                    + result.getTotalRecords() + ",\"ItemsNumbers\":" + result.getItemsNumbers() + "}}");
            when(ossConnection.callRdsApi(anyLong(), anyLong())).thenCallRealMethod();
            List<BinlogFile> resultList =
                ossConnection.callRdsApi(System.currentTimeMillis() - 1000, System.currentTimeMillis());
            Assert.assertEquals(1, resultList.size());
            Assert.assertEquals(binlogFile, resultList.get(0));
        }
    }

    @Test
    public void isInitTest() throws Exception {
        try (MockedStatic<BinlogProcessor> processorMockedStatic = Mockito.mockStatic(BinlogProcessor.class)) {
            List<BinlogFile> binlogFileList = new ArrayList<>();
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setLogname("a.01");
            binlogFile.setLogBeginTime("2024-12-30T00:00:00Z");
            binlogFile.setLogEndTime("2024-12-31T00:00:00Z");
            binlogFileList.add(binlogFile);
            processorMockedStatic.when(() -> BinlogProcessor.process(any(), any(), anyLong(), anyLong(), anyLong()))
                .thenReturn(binlogFileList);
            OssConnection connection = new OssConnection("a", "b", "c", "d", 1L, 3L, 4L);
            Assert.assertFalse(connection.isInit());

            connection.filterBinlogList(binlogFileList);
            Assert.assertTrue(connection.isInit());
        }
    }

    @Test()
    public void connectBeforeFailedTest() {
        mockConfig(TASK_DUMP_OFFLINE_BINLOG_IN_DOWNLOAD_MODE, "true");
        try (MockedStatic<FileUtils> fileUtilsMockedStatic = Mockito.mockStatic(FileUtils.class)) {

            IOException mockException = new IOException("mocked exception");
            fileUtilsMockedStatic.when(() -> FileUtils.forceMkdir(any())).thenThrow(mockException);

            OssConnection connection =
                Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
            doCallRealMethod().when(connection).connectBefore();
            doCallRealMethod().when(connection).setLogger(any());
            Logger logger = Mockito.mock(Logger.class);
            connection.setLogger(logger);
            Throwable t = null;
            try {
                connection.connectBefore();
            } catch (Throwable e) {
                t = e;
            }
            Assert.assertNotNull(t);
            verify(logger, times(1)).error("mkdir {} failed!", "d/a", mockException);
            Mockito.reset(connection);
        }
    }

    @Test
    public void connectTest() throws Exception {
        mockConfig(RDS_API_URL, "http");
        mockConfig(RDS_API_ACCESS_KEY, "access_key");
        mockConfig(RDS_API_ACCESS_ID, "access_id");
        OssConnection ossConnection =
            Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
        try (MockedStatic<HttpHelper> httpMockedStatic = Mockito.mockStatic(HttpHelper.class)) {
            DescribeBinlogFilesResult result = new DescribeBinlogFilesResult();
            List<BinlogFile> binlogFileList = new ArrayList<>();
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setLogname("a");
            binlogFile.setIntranetDownloadLink("download-link");
            binlogFile.setDownloadLink("download-link");
            binlogFile.setFileSize(1024L);
            binlogFileList.add(binlogFile);
            result.setItems(binlogFileList);
            result.setTotalRecords(binlogFileList.size());
            result.setItemsNumbers(binlogFileList.size());
            httpMockedStatic.when(() -> HttpHelper.get(anyString(), anyInt())).thenReturn(
                "{\"Code\":200,\"Data\":{\"Items\":" + JSON.toJSONString(result.getItems()) + ",\"TotalRecords\":"
                    + result.getTotalRecords() + ",\"ItemsNumbers\":" + result.getItemsNumbers() + "}}");
            when(ossConnection.callRdsApi(anyLong(), anyLong())).thenCallRealMethod();
            doCallRealMethod().when(ossConnection).connect();
            ossConnection.connect();
        }
        verify(ossConnection, times(1)).isInit();
        verify(ossConnection, times(1)).connectBefore();
        verify(ossConnection, times(1)).beginTimestamp();
        verify(ossConnection, times(1)).callRdsApi(anyLong(), anyLong());
        verify(ossConnection, times(1)).filterBinlogList(anyList());
    }

    @Test
    public void disconnectTest() throws IOException {
        OssConnection ossConnection =
            Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
        doCallRealMethod().when(ossConnection).disconnect();
        ossConnection.disconnect();
        verify(ossConnection, times(1)).stopAsyncDownload();
    }

    @Test
    public void printBinlogQueueTest() throws Exception {
        mockConfig(RDS_API_URL, "http");
        mockConfig(RDS_API_ACCESS_KEY, "access_key");
        mockConfig(RDS_API_ACCESS_ID, "access_id");
        try (MockedStatic<BinlogProcessor> binlogProcessorMockedStatic = Mockito.mockStatic(BinlogProcessor.class)) {
            List<BinlogFile> binlogFileList = new ArrayList<>();
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setLogname("a");
            binlogFile.setIntranetDownloadLink("download-link");
            binlogFile.setDownloadLink("download-link");
            binlogFile.setBeginTime(0L);
            binlogFile.setEndTime(10L);
            binlogFile.setFileSize(1024L);
            binlogFile.setServerId(1L);
            binlogFile.setInstanceID(1L);
            binlogFileList.add(binlogFile);
            binlogProcessorMockedStatic.when(
                    () -> BinlogProcessor.process(anyList(), anySet(), anyLong(), anyLong(), anyLong()))
                .thenReturn(binlogFileList);
            OssConnection ossConnection =
                Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
            when(ossConnection.callRdsApi(anyLong(), anyLong())).thenReturn(binlogFileList);
            doCallRealMethod().when(ossConnection).setLogger(any());
            Logger logger = Mockito.mock(Logger.class);
            ossConnection.setLogger(logger);
            doCallRealMethod().when(ossConnection).filterBinlogList(any());
            doCallRealMethod().when(ossConnection).connect();
            doCallRealMethod().when(ossConnection).printBinlogQueue();
            ossConnection.connect();
            ossConnection.printBinlogQueue();
            verify(logger, times(1)).info("fetch binlog size : {} and use host : {} with serverId : {}", 1, 1L, 1L);
            verify(logger, times(1)).error("{}{}", binlogFile.getLogname(),
                MessageFormat.format("[ {0} , {1} , {2}]", binlogFile.getLogBeginTime(), binlogFile.getLogEndTime(),
                    binlogFile.getDownloadLink()));
        }
    }

    @Test
    public void getLastLogNameTest() throws Exception {
        mockConfig(RDS_API_URL, "http");
        mockConfig(RDS_API_ACCESS_KEY, "access_key");
        mockConfig(RDS_API_ACCESS_ID, "access_id");
        try (MockedStatic<BinlogProcessor> binlogProcessorMockedStatic = Mockito.mockStatic(BinlogProcessor.class)) {
            OssConnection ossConnection =
                Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
            List<BinlogFile> binlogFileList = new ArrayList<>();
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setLogname("a");
            binlogFile.setIntranetDownloadLink("download-link");
            binlogFile.setDownloadLink("download-link");
            binlogFile.setBeginTime(0L);
            binlogFile.setEndTime(10L);
            binlogFile.setFileSize(1024L);
            binlogFileList.add(binlogFile);
            when(ossConnection.callRdsApi(anyLong(), anyLong())).thenReturn(binlogFileList);
            binlogProcessorMockedStatic.when(
                    () -> BinlogProcessor.process(anyList(), anySet(), anyLong(), anyLong(), anyLong()))
                .thenReturn(binlogFileList);

            doCallRealMethod().when(ossConnection).filterBinlogList(any());
            doCallRealMethod().when(ossConnection).connect();
            doCallRealMethod().when(ossConnection).getLastLogName();
            ossConnection.connect();
            String logName = ossConnection.getLastLogName();
            Assert.assertEquals(binlogFile.getLogname(), logName);

        }
    }

    @Test
    public void getBinlogFileTest() throws Exception {
        mockConfig(RDS_API_URL, "http");
        mockConfig(RDS_API_ACCESS_KEY, "access_key");
        mockConfig(RDS_API_ACCESS_ID, "access_id");
        try (MockedStatic<BinlogProcessor> binlogProcessorMockedStatic = Mockito.mockStatic(BinlogProcessor.class);
            MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = Mockito.mockStatic(BinlogFileUtil.class)
        ) {
            OssConnection ossConnection =
                Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
            List<BinlogFile> binlogFileList = new ArrayList<>();
            binlogFileUtilMockedStatic.when(()->BinlogFileUtil.readServerId(anyString())).thenReturn(111111L);
            binlogFileUtilMockedStatic.when(()->BinlogFileUtil.readFileSize(anyString())).thenReturn(30L);
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setLogname("a");
            binlogFile.setIntranetDownloadLink("download-link");
            binlogFile.setDownloadLink("download-link");
            binlogFile.setBeginTime(0L);
            binlogFile.setEndTime(10L);
            binlogFile.setFileSize(1024L);
            binlogFile.setInstanceID(100L);
            binlogFileList.add(binlogFile);
            when(ossConnection.callRdsApi(anyLong(), anyLong())).thenReturn(binlogFileList);
            binlogProcessorMockedStatic.when(
                    () -> BinlogProcessor.process(anyList(), anySet(), anyLong(), anyLong(), anyLong()))
                .thenReturn(binlogFileList);

            doCallRealMethod().when(ossConnection).filterBinlogList(any());
            doCallRealMethod().when(ossConnection).connect();
            doCallRealMethod().when(ossConnection).getBinlogFile(anyString());
            ossConnection.connect();
            BinlogFile targetFile = ossConnection.getBinlogFile(binlogFile.getLogname());
            Assert.assertEquals(binlogFile, targetFile);

        }
    }

    @Before
    public void setUp() {
        CacheManager cacheManager = Mockito.mock(CacheManager.class);
        registerSpringObject("cacheManager", cacheManager);
    }

    @Test
    public void providerFetcherExceptionTest() throws Exception {
        mockConfig(RDS_API_URL, "http");
        mockConfig(RDS_API_ACCESS_KEY, "access_key");
        mockConfig(RDS_API_ACCESS_ID, "access_id");

        OssConnection ossConnection =
            Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
        doCallRealMethod().when(ossConnection).providerFetcher(any(), anyLong(), anyBoolean());
        BinlogFile binlogFile = new BinlogFile();
        binlogFile.setLogname("a");
        binlogFile.setIntranetDownloadLink("download-link");
        binlogFile.setDownloadLink("download-link");
        binlogFile.setBeginTime(0L);
        binlogFile.setEndTime(10L);
        binlogFile.setFileSize(1024L);
        when(ossConnection.getLastLogName()).thenReturn(binlogFile.getLogname());
        when(ossConnection.getBinlogFile(anyString())).thenReturn(null);
        doCallRealMethod().when(ossConnection).setLogger(any());
        Logger logger = Mockito.mock(Logger.class);
        ossConnection.setLogger(logger);
        Throwable t = null;
        try {
            ossConnection.providerFetcher(null, 1L, false);
        } catch (Exception e) {
            t = e;
        }
        Assert.assertNotNull(t);
        Assert.assertEquals(PositionNotFoundException.class, t.getClass());
        verify(logger, times(1)).warn("may be dn transfer to new binlog sequence, will use max oss file continue :{}",
            binlogFile.getLogname());
        verify(logger, times(1)).error("can not find binlog file : {} from oss!", binlogFile.getLogname());
        verify(ossConnection, times(1)).getLastLogName();
        verify(ossConnection, times(1)).getBinlogFile(anyString());
        Mockito.reset(ossConnection);
    }

    @Test
    public void createFileLogFetcherTest() {
        OssConnection connection = Mockito.mock(OssConnection.class);
        doCallRealMethod().when(connection).createFileLogFetcher();
        FileLogFetcher fileLogFetcher = connection.createFileLogFetcher();
        Assert.assertNotNull(fileLogFetcher);
        Assert.assertEquals(FileLogFetcher.class, fileLogFetcher.getClass());
    }

    @Test
    public void providerLocalFetcherTest() throws Exception {
        try (MockedStatic<DownloadTaskFactory> downloadTaskFactoryMockedStatic = Mockito.mockStatic(
            DownloadTaskFactory.class)) {
            DownloadTask downloadTask = Mockito.mock(DownloadTask.class);
            downloadTaskFactoryMockedStatic.when(
                    () -> DownloadTaskFactory.createDownloadTask(anyString(), any(), anyString()))
                .thenReturn(downloadTask);
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setLogname("a");
            binlogFile.setIntranetDownloadLink("download-link");
            binlogFile.setDownloadLink("download-link");
            binlogFile.setBeginTime(0L);
            binlogFile.setEndTime(10L);
            binlogFile.setFileSize(1024L);

            BinlogFile binlogFile1 = new BinlogFile();
            binlogFile1.setLogname("b");
            binlogFile1.setIntranetDownloadLink("download-link");
            binlogFile1.setDownloadLink("download-link");
            binlogFile1.setBeginTime(0L);
            binlogFile1.setEndTime(10L);
            binlogFile1.setFileSize(1024L);

            LinkedList<BinlogFile> binlogFileQueue = new LinkedList<>();
            binlogFileQueue.add(binlogFile);
            binlogFileQueue.add(binlogFile1);

            OssConnection connection =
                Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
            StorageDownloader downloader = Mockito.mock(StorageDownloader.class);
            when(connection.createNewDownloader()).thenReturn(downloader);
            when(connection.getBinlogFileQueue()).thenReturn(binlogFileQueue);
            FileLogFetcher fetcher = Mockito.mock(FileLogFetcher.class);
            when(connection.createFileLogFetcher()).thenReturn(fetcher);
            doCallRealMethod().when(connection).setLogger(any());
            Logger logger = Mockito.mock(Logger.class);
            connection.setLogger(logger);
            when(connection.providerLocalFetcher(binlogFile, 1L, false)).thenCallRealMethod();
            LogFetcher logFetcher = connection.providerLocalFetcher(binlogFile, 1L, false);
            verify(downloader, times(1)).start();
            verify(downloadTask, times(1)).exec();
            verify(connection, times(1)).createFileLogFetcher();
            verify(connection, times(1)).createNewDownloader();
            verify(connection, times(1)).getBinlogFileQueue();
            Assert.assertEquals(ContinuesFileLogFetcher.class, logFetcher.getClass());
            verify(logger, times(1)).info("sync download first file {} before", binlogFile.getLogname());
            verify(logger, times(1)).info("provider fetcher file ： {} size ： {} pos : {}", "d/a/a", 0L, 1L);
        }
    }

    @Test
    public void createURLLogFetcherTest() throws IOException {
        URLLogFetcher fetcher = LogFetcherFactory.createURLLogFetcher("test", "a");
        Assert.assertNotNull(fetcher);
    }


    @Test
    public void testFilterBinlogListByGareth() throws Exception {
        OssConnection ossConnection =
            Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
        doCallRealMethod().when(ossConnection).filterBinlogList(anyList());
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_USE_DBS, "true");
        mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH, "true");
        mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_POOL_TYPE, "nas");
        mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_CONFIG, "{\"uid\":\"223274842729957349\",\"protocol\":\"nfs\",\"protocolVersion\":\"v4\",\"type\":\"nas\",\"region\":\"cn-beijing\",\"originalIp\":\"10.0.109.210\",\"originalPort\":\"2049\",\"mountpoint\":\"/apsaradb/test\"}");
        try(MockedStatic<Shell> shellMockedStatic = Mockito.mockStatic(Shell.class);
            MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = Mockito.mockStatic(BinlogFileUtil.class);
        ){
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.readFileSize(anyString())).thenReturn(30L);
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.readServerId(anyString())).thenReturn(1111L);
            shellMockedStatic.when(() -> Shell.execCommand(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("success");
            List<BinlogFile> binlogFileList = new ArrayList<>();
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setDownloadLink("download link");
            binlogFile.setLogname("binlog.000001");
            binlogFile.setArchiveLogId("archive id");
            binlogFileList.add(binlogFile);
            ossConnection.filterBinlogList(binlogFileList);
        }
    }

    @Test
    public void testFilterBinlogListByGarethAndTestDescribeStorageApi() throws Exception {
        OssConnection ossConnection =
            Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
        doCallRealMethod().when(ossConnection).filterBinlogList(anyList());
        doCallRealMethod().when(ossConnection).prepareServerIdForDbs(anyList());
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_USE_DBS, "true");
        mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH, "true");
        mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_POOL_TYPE, "");
        mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH_CONFIG, "");
        try(MockedStatic<Shell> shellMockedStatic = Mockito.mockStatic(Shell.class);
            MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = Mockito.mockStatic(BinlogFileUtil.class);
            MockedStatic<DbsApi> dbsApiMockedStatic = Mockito.mockStatic(DbsApi.class)
        ){
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.readFileSize(anyString())).thenReturn(30L);
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.readServerId(anyString())).thenReturn(1111L);
            DescribeStorageInfoResult result = new DescribeStorageInfoResult();
            StorageEntity entity = new StorageEntity();
            result.setDataJson(JSON.toJSONString(entity));
            result.setData(entity);
            result.getData().setType("nas");
            dbsApiMockedStatic.when(() -> DbsApi.describeStorageInfo(anyString(), anyString(), anyString())).thenReturn(result);
            shellMockedStatic.when(() -> Shell.execCommand(anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn("success");
            List<BinlogFile> binlogFileList = new ArrayList<>();
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setDownloadLink("download link");
            binlogFile.setLogname("binlog.000001");
            binlogFile.setArchiveLogId("archive id");
            binlogFile.setStorageEntityId("test");
            binlogFileList.add(binlogFile);
            ossConnection.filterBinlogList(binlogFileList);
            dbsApiMockedStatic.verify(()->DbsApi.describeStorageInfo(anyString(), anyString(), anyString()));
        }
    }

    @Test
    public void testFilterBinlogListByDbsTask() throws Exception {
        OssConnection ossConnection =
            Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
        doCallRealMethod().when(ossConnection).filterBinlogList(anyList());
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_USE_DBS, "true");
        mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH, "false");
        try(MockedStatic<Shell> shellMockedStatic = Mockito.mockStatic(Shell.class);
            MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = Mockito.mockStatic(BinlogFileUtil.class);
            MockedStatic<DbsApi> dbsApiMockedStatic = Mockito.mockStatic(DbsApi.class);
        ){
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.readFileSize(anyString())).thenReturn(30L);
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.readServerId(anyString())).thenReturn(1111L);
            RdsDownloadForRestoreResult restoreResult = new RdsDownloadForRestoreResult();
            RdsDownloadForRestoreResult.DownloadData downloadData = new RdsDownloadForRestoreResult.DownloadData();
            downloadData.setTaskId(UUID.randomUUID().toString());
            downloadData.setStatus("OK");
            restoreResult.setData(downloadData);
            dbsApiMockedStatic.when(() -> DbsApi.submitDownloadTask(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(restoreResult);
            List<BinlogFile> binlogFileList = new ArrayList<>();
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setDownloadLink("download link");
            binlogFile.setLogname("binlog.000001");
            binlogFile.setArchiveLogId("archive id");
            binlogFileList.add(binlogFile);
            ossConnection.filterBinlogList(binlogFileList);
        }
    }

    @Test(expected = PolardbxException.class)
    public void testFilterBinlogListByDbsTaskWithException() throws Exception {
        OssConnection ossConnection =
            Mockito.mock(OssConnection.class, withSettings().useConstructor("a", "b", "c", "d", 1L, 3L, 4L));
        doCallRealMethod().when(ossConnection).filterBinlogList(anyList());
        doCallRealMethod().when(ossConnection).prepareServerIdForDbs(anyList());
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_USE_DBS, "true");
        mockConfig(ConfigKeys.DBS_DOWNLOAD_DN_BINLOG_USE_DBS_GARETH, "false");
        try(MockedStatic<Shell> shellMockedStatic = Mockito.mockStatic(Shell.class);
            MockedStatic<BinlogFileUtil> binlogFileUtilMockedStatic = Mockito.mockStatic(BinlogFileUtil.class);
            MockedStatic<DbsApi> dbsApiMockedStatic = Mockito.mockStatic(DbsApi.class);
        ){
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.readFileSize(anyString())).thenReturn(10L);
            binlogFileUtilMockedStatic.when(() -> BinlogFileUtil.readServerId(anyString())).thenReturn(1111L);
            RdsDownloadForRestoreResult restoreResult = new RdsDownloadForRestoreResult();
            RdsDownloadForRestoreResult.DownloadData downloadData = new RdsDownloadForRestoreResult.DownloadData();
            downloadData.setTaskId(UUID.randomUUID().toString());
            downloadData.setStatus("OK");
            restoreResult.setData(downloadData);
            dbsApiMockedStatic.when(() -> DbsApi.submitDownloadTask(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(restoreResult);
            DescribeTaskStatusResult describeTaskStatusResult = new DescribeTaskStatusResult();
            describeTaskStatusResult.setData(new DescribeTaskStatusResult.Data());
            describeTaskStatusResult.getData().setStatus("Failed");
            dbsApiMockedStatic.when(() -> DbsApi.describeTaskStatus(anyString(), anyString(), anyString(), anyString())).thenReturn(describeTaskStatusResult);
            List<BinlogFile> binlogFileList = new ArrayList<>();
            BinlogFile binlogFile = new BinlogFile();
            binlogFile.setDownloadLink("download link");
            binlogFile.setLogname("binlog.000001");
            binlogFile.setArchiveLogId("archive id");
            binlogFileList.add(binlogFile);
            ossConnection.filterBinlogList(binlogFileList);
        }
    }

}
