/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.api;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.api.DbsApi;
import com.aliyun.polardbx.binlog.api.DescribeBinlogFilesResult;
import com.aliyun.polardbx.binlog.api.DescribeRdsBinlogListApi;
import com.aliyun.polardbx.binlog.api.RdsApi;
import com.aliyun.polardbx.binlog.api.dbs.ArchiveLogPages;
import com.aliyun.polardbx.binlog.api.dbs.DbsBinlogFile;
import com.aliyun.polardbx.binlog.api.dbs.DescribeRestoreArchiveLogFilesResult;
import com.aliyun.polardbx.binlog.api.dbs.DescribeUnifyArchiveLogFilesResult;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DescribeRdsBinlogListApiTest extends BaseTest {

    @BeforeClass
    public static void setUp() {
        System.setProperty("dbs_api_url", "test");
        System.setProperty("dbs_api_access_id", "test");
        System.setProperty("dbs_api_access_key", "test");
        System.setProperty("dbs_region_code", "cn-hangzhou");
    }

    @Before
    public void before() {
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_MAX_CONCURRENCY, "3");
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_COUNT, "3");
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_INTERVAL_MS, "1000");
    }

    @After
    public void tearDown() {
        DescribeRdsBinlogListApi.resetSemaphore();
    }

    @Test
    public void testRdsApi() throws Exception {
        try (MockedStatic<RdsApi> rdsApiMockedStatic = Mockito.mockStatic(RdsApi.class)) {
            DescribeBinlogFilesResult result = new DescribeBinlogFilesResult();
            ArchiveLogPages pages = new ArchiveLogPages();
            pages.setPageNumber(1);
            pages.setTotalElements(1);
            pages.setPageSize(1);
            BinlogFile dbsBinlogFile = new BinlogFile();
            List<BinlogFile> rdsBinlogFiles = new ArrayList<>();
            rdsBinlogFiles.add(dbsBinlogFile);
            result.setItems(rdsBinlogFiles);
            result.setTotalRecords(1);
            result.setItemsNumbers(1);
            result.setPageNumbers(1);
            long now = System.currentTimeMillis();
            String startTime = RdsApi.formatUTCTZ(new Date(now - 1000));
            String endTime = RdsApi.formatUTCTZ(new Date(now));
            rdsApiMockedStatic.when(
                    () -> RdsApi.describeBinlogFiles("test-instance", "111", "222", startTime, endTime, 200, 1))
                .thenReturn(result);
            List<BinlogFile> binlogFiles =
                DescribeRdsBinlogListApi.describeBinlogFiles("test-instance", "111", "222", now - 1000, now, 200,
                    false);
            Assert.assertEquals(rdsBinlogFiles.size(), binlogFiles.size());
        }

    }

    /**
     * 测试 useDbsApi=true 时调用 describeRestoreArchiveLogFiles 接口（单页）
     */
    @Test
    public void testDbsApiWithRestoreArchiveLogFiles() throws Exception {
        try (MockedStatic<DbsApi> dbsApi = Mockito.mockStatic(DbsApi.class)) {
            DescribeUnifyArchiveLogFilesResult result = new DescribeUnifyArchiveLogFilesResult();
            ArchiveLogPages archiveLogPages = new ArchiveLogPages();
            archiveLogPages.setPageNumber(1);
            archiveLogPages.setTotalElements(1);
            archiveLogPages.setPageSize(1);
            DbsBinlogFile dbsBinlogFile = new DbsBinlogFile();
            List<DbsBinlogFile> dbsBinlogFiles = new ArrayList<>();
            dbsBinlogFiles.add(dbsBinlogFile);
            archiveLogPages.setContent(dbsBinlogFiles);
            result.setData(archiveLogPages);
            result.setSuccess("true");
            result.setCode("200");
            long now = System.currentTimeMillis();
            dbsApi.when(() -> DbsApi.describeUnifyArchiveLogFiles(
                "test-instance", "111", "222", now - 1000, now, 200, 1)).thenReturn(result);
            List<BinlogFile> binlogFiles = DescribeRdsBinlogListApi.describeBinlogFiles(
                "test-instance", "111", "222", now - 1000, now, 200, true);
            Assert.assertEquals(dbsBinlogFiles.size(), binlogFiles.size());
        }
    }

    /**
     * 测试 useDbsApi=true 时多页分页查询
     */
    @Test
    public void testDbsApiWithRestoreArchiveLogFilesMultiPage() throws Exception {
        try (MockedStatic<DbsApi> dbsApi = Mockito.mockStatic(DbsApi.class)) {
            // 第一页结果
            DescribeUnifyArchiveLogFilesResult result1 = new DescribeUnifyArchiveLogFilesResult();
            ArchiveLogPages pages1 = new ArchiveLogPages();
            pages1.setPageNumber(1);
            pages1.setTotalElements(3);
            pages1.setPageSize(2);
            DbsBinlogFile file1 = new DbsBinlogFile();
            file1.setLogFileName("mysql-bin.000001");
            DbsBinlogFile file2 = new DbsBinlogFile();
            file2.setLogFileName("mysql-bin.000002");
            List<DbsBinlogFile> list1 = new ArrayList<>();
            list1.add(file1);
            list1.add(file2);
            pages1.setContent(list1);
            result1.setData(pages1);
            result1.setSuccess("true");
            result1.setCode("200");

            // 第二页结果
            DescribeUnifyArchiveLogFilesResult result2 = new DescribeUnifyArchiveLogFilesResult();
            ArchiveLogPages pages2 = new ArchiveLogPages();
            pages2.setPageNumber(2);
            pages2.setTotalElements(3);
            pages2.setPageSize(2);
            DbsBinlogFile file3 = new DbsBinlogFile();
            file3.setLogFileName("mysql-bin.000003");
            List<DbsBinlogFile> list2 = new ArrayList<>();
            list2.add(file3);
            pages2.setContent(list2);
            result2.setData(pages2);
            result2.setCode("200");
            result2.setSuccess("true");

            long now = System.currentTimeMillis();
            dbsApi.when(() -> DbsApi.describeUnifyArchiveLogFiles(
                "test-instance", "111", "222", now - 1000, now, 200, 1)).thenReturn(result1);
            dbsApi.when(() -> DbsApi.describeUnifyArchiveLogFiles(
                "test-instance", "111", "222", now - 1000, now, 200, 2)).thenReturn(result2);

            List<BinlogFile> binlogFiles = DescribeRdsBinlogListApi.describeBinlogFiles(
                "test-instance", "111", "222", now - 1000, now, 200, true);
            Assert.assertEquals(3, binlogFiles.size());
        }
    }

    // ======================== 限流相关测试 ========================

    /**
     * 测试限流信号量从配置中获取
     */
    @Test
    public void testGetSemaphoreFromConfig() {
        Semaphore sem = DescribeRdsBinlogListApi.getSemaphore();
        Assert.assertNotNull(sem);
        Assert.assertEquals(3, sem.availablePermits());
    }

    /**
     * 测试配置动态更新后信号量的permits随之变化
     */
    @Test
    public void testGetSemaphoreConfigUpdate() {
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_MAX_CONCURRENCY, "5");
        Semaphore sem = DescribeRdsBinlogListApi.getSemaphore();
        Assert.assertEquals(5, sem.availablePermits());

        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_MAX_CONCURRENCY, "2");
        Semaphore sem2 = DescribeRdsBinlogListApi.getSemaphore();
        Assert.assertEquals(2, sem2.availablePermits());
    }

    /**
     * 测试并发限流生效，最多只有配置数量的线程可以同时执行。
     * 直接使用getSemaphore()返回的信号量验证并发控制，避免多线程下mockStatic的线程安全问题。
     */
    @Test
    public void testConcurrencyLimiting() throws Exception {
        final int maxConcurrency = 2;
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_MAX_CONCURRENCY, String.valueOf(maxConcurrency));
        final Semaphore sem = DescribeRdsBinlogListApi.getSemaphore();

        final int totalThreads = 5;
        final AtomicInteger currentRunning = new AtomicInteger(0);
        final AtomicInteger maxObservedConcurrency = new AtomicInteger(0);
        final CountDownLatch allDone = new CountDownLatch(totalThreads);
        final AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < totalThreads; i++) {
            new Thread(() -> {
                try {
                    sem.acquire();
                    try {
                        int running = currentRunning.incrementAndGet();
                        maxObservedConcurrency.updateAndGet(prev -> Math.max(prev, running));
                        Thread.sleep(200);
                    } finally {
                        currentRunning.decrementAndGet();
                        sem.release();
                    }
                } catch (Throwable e) {
                    error.compareAndSet(null, e);
                } finally {
                    allDone.countDown();
                }
            }).start();
        }

        Assert.assertTrue("测试超时", allDone.await(10, TimeUnit.SECONDS));
        Assert.assertNull("并发测试中出现异常: " + error.get(), error.get());
        Assert.assertTrue("并发数超过限制: maxObserved=" + maxObservedConcurrency.get()
                + ", limit=" + maxConcurrency,
            maxObservedConcurrency.get() <= maxConcurrency);
    }

    /**
     * 测试限流后信号量能正确释放，后续调用不会被永久阻塞
     */
    @Test
    public void testSemaphoreReleasedAfterCall() throws Exception {
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_MAX_CONCURRENCY, "1");
        DescribeRdsBinlogListApi.getSemaphore();

        try (MockedStatic<RdsApi> rdsApiMockedStatic = Mockito.mockStatic(RdsApi.class)) {
            long now = System.currentTimeMillis();
            String startTime = RdsApi.formatUTCTZ(new Date(now - 1000));
            String endTime = RdsApi.formatUTCTZ(new Date(now));

            DescribeBinlogFilesResult result = buildSinglePageRdsResult();
            rdsApiMockedStatic.when(() -> RdsApi.describeBinlogFiles(
                "test-instance", "111", "222", startTime, endTime, 200, 1)).thenReturn(result);

            for (int i = 0; i < 5; i++) {
                List<BinlogFile> binlogFiles = DescribeRdsBinlogListApi.describeBinlogFiles(
                    "test-instance", "111", "222", now - 1000, now, 200, false);
                Assert.assertEquals(1, binlogFiles.size());
            }

            Semaphore sem = DescribeRdsBinlogListApi.getSemaphore();
            Assert.assertEquals(1, sem.availablePermits());
        }
    }

    /**
     * 测试异常情况下信号量也能正确释放
     */
    @Test
    public void testSemaphoreReleasedOnException() throws Exception {
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_MAX_CONCURRENCY, "1");
        // 设置重试次数1，避免内部重试干扰测试
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_COUNT, "1");
        DescribeRdsBinlogListApi.getSemaphore();

        try (MockedStatic<RdsApi> rdsApiMockedStatic = Mockito.mockStatic(RdsApi.class)) {
            long now = System.currentTimeMillis();
            String startTime = RdsApi.formatUTCTZ(new Date(now - 1000));
            String endTime = RdsApi.formatUTCTZ(new Date(now));

            rdsApiMockedStatic.when(() -> RdsApi.describeBinlogFiles(
                    "test-instance", "111", "222", startTime, endTime, 200, 1))
                .thenThrow(new RuntimeException("模拟API调用失败"));

            try {
                DescribeRdsBinlogListApi.describeBinlogFiles(
                    "test-instance", "111", "222", now - 1000, now, 200, false);
                Assert.fail("应该抛出异常");
            } catch (Exception e) {
                // 经过guava-retrying包装后的异常
                Assert.assertNotNull(e);
            }

            Semaphore sem = DescribeRdsBinlogListApi.getSemaphore();
            Assert.assertEquals(1, sem.availablePermits());
        }
    }

    // ======================== 重试相关测试 ========================

    /**
     * 测试超时异常触发重试后成功（RDS API）
     */
    @Test
    public void testRdsApiRetryOnSocketTimeout() throws Exception {
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_COUNT, "3");
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_INTERVAL_MS, "100");

        try (MockedStatic<RdsApi> rdsApiMockedStatic = Mockito.mockStatic(RdsApi.class)) {
            long now = System.currentTimeMillis();
            String startTime = RdsApi.formatUTCTZ(new Date(now - 1000));
            String endTime = RdsApi.formatUTCTZ(new Date(now));

            AtomicInteger callCount = new AtomicInteger(0);
            DescribeBinlogFilesResult successResult = buildSinglePageRdsResult();

            rdsApiMockedStatic.when(() -> RdsApi.describeBinlogFiles(
                "test-instance", "111", "222", startTime, endTime, 200, 1)
            ).thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                if (count <= 2) {
                    throw new SocketTimeoutException("连接超时");
                }
                return successResult;
            });

            List<BinlogFile> binlogFiles = DescribeRdsBinlogListApi.describeBinlogFiles(
                "test-instance", "111", "222", now - 1000, now, 200, false);
            Assert.assertEquals(1, binlogFiles.size());
            Assert.assertEquals(3, callCount.get());
        }
    }

    /**
     * 测试超时异常触发重试后成功（DBS API）
     */
    @Test
    public void testDbsApiRetryOnSocketTimeout() throws Exception {
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_COUNT, "3");
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_INTERVAL_MS, "100");

        try (MockedStatic<DbsApi> dbsApiMockedStatic = Mockito.mockStatic(DbsApi.class)) {
            long now = System.currentTimeMillis();
            AtomicInteger callCount = new AtomicInteger(0);
            DescribeUnifyArchiveLogFilesResult successResult = buildSinglePageDbsResult();

            dbsApiMockedStatic.when(() -> DbsApi.describeUnifyArchiveLogFiles(
                "test-instance", "111", "222", now - 1000, now, 200, 1)
            ).thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                if (count <= 1) {
                    throw new SocketTimeoutException("连接超时");
                }
                return successResult;
            });

            List<BinlogFile> binlogFiles = DescribeRdsBinlogListApi.describeBinlogFiles(
                "test-instance", "111", "222", now - 1000, now, 200, true);
            Assert.assertEquals(1, binlogFiles.size());
            Assert.assertEquals(2, callCount.get());
        }
    }

    /**
     * 测试重试耗尽后抛出异常
     */
    @Test
    public void testRetryExhaustedThrowsException() throws Exception {
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_COUNT, "2");
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_INTERVAL_MS, "100");

        try (MockedStatic<RdsApi> rdsApiMockedStatic = Mockito.mockStatic(RdsApi.class)) {
            long now = System.currentTimeMillis();
            String startTime = RdsApi.formatUTCTZ(new Date(now - 1000));
            String endTime = RdsApi.formatUTCTZ(new Date(now));

            AtomicInteger callCount = new AtomicInteger(0);
            rdsApiMockedStatic.when(() -> RdsApi.describeBinlogFiles(
                "test-instance", "111", "222", startTime, endTime, 200, 1)
            ).thenAnswer(invocation -> {
                callCount.incrementAndGet();
                throw new SocketTimeoutException("持续超时");
            });

            try {
                DescribeRdsBinlogListApi.describeBinlogFiles(
                    "test-instance", "111", "222", now - 1000, now, 200, false);
                Assert.fail("应该抛出异常");
            } catch (Exception e) {
                Assert.assertNotNull(e);
            }
            Assert.assertEquals(2, callCount.get());
        }
    }

    /**
     * 测试不可重试的异常不会触发重试
     */
    @Test
    public void testNonRetryableExceptionDoesNotRetry() throws Exception {
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_COUNT, "3");
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_INTERVAL_MS, "100");

        try (MockedStatic<RdsApi> rdsApiMockedStatic = Mockito.mockStatic(RdsApi.class)) {
            long now = System.currentTimeMillis();
            String startTime = RdsApi.formatUTCTZ(new Date(now - 1000));
            String endTime = RdsApi.formatUTCTZ(new Date(now));

            AtomicInteger callCount = new AtomicInteger(0);
            rdsApiMockedStatic.when(() -> RdsApi.describeBinlogFiles(
                "test-instance", "111", "222", startTime, endTime, 200, 1)
            ).thenAnswer(invocation -> {
                callCount.incrementAndGet();
                throw new IllegalArgumentException("参数错误");
            });

            try {
                DescribeRdsBinlogListApi.describeBinlogFiles(
                    "test-instance", "111", "222", now - 1000, now, 200, false);
                Assert.fail("应该抛出异常");
            } catch (Exception e) {
                Assert.assertNotNull(e);
            }
            // 不可重试异常只调用一次
            Assert.assertEquals(1, callCount.get());
        }
    }

    /**
     * 测试isRetryableException对各类异常的判断
     */
    @Test
    public void testIsRetryableException() {
        Assert.assertTrue(DescribeRdsBinlogListApi.isRetryableException(
            new SocketTimeoutException("超时")));
        Assert.assertTrue(DescribeRdsBinlogListApi.isRetryableException(
            new java.net.ConnectException("连接失败")));
        Assert.assertTrue(DescribeRdsBinlogListApi.isRetryableException(
            new java.io.IOException("IO异常")));
        // 包装在RuntimeException中的可重试异常
        Assert.assertTrue(DescribeRdsBinlogListApi.isRetryableException(
            new RuntimeException(new SocketTimeoutException("超时"))));
        // 不可重试异常
        Assert.assertFalse(DescribeRdsBinlogListApi.isRetryableException(
            new IllegalArgumentException("参数错误")));
        Assert.assertFalse(DescribeRdsBinlogListApi.isRetryableException(
            new NullPointerException()));
    }

    /**
     * 测试IO异常触发重试后成功
     */
    @Test
    public void testRetryOnIOException() throws Exception {
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_COUNT, "3");
        mockConfig(ConfigKeys.DESCRIBE_BINLOG_LIST_API_RETRY_INTERVAL_MS, "100");

        try (MockedStatic<RdsApi> rdsApiMockedStatic = Mockito.mockStatic(RdsApi.class)) {
            long now = System.currentTimeMillis();
            String startTime = RdsApi.formatUTCTZ(new Date(now - 1000));
            String endTime = RdsApi.formatUTCTZ(new Date(now));

            AtomicInteger callCount = new AtomicInteger(0);
            DescribeBinlogFilesResult successResult = buildSinglePageRdsResult();

            rdsApiMockedStatic.when(() -> RdsApi.describeBinlogFiles(
                "test-instance", "111", "222", startTime, endTime, 200, 1)
            ).thenAnswer(invocation -> {
                int count = callCount.incrementAndGet();
                if (count == 1) {
                    throw new java.io.IOException("网络IO异常");
                }
                return successResult;
            });

            List<BinlogFile> binlogFiles = DescribeRdsBinlogListApi.describeBinlogFiles(
                "test-instance", "111", "222", now - 1000, now, 200, false);
            Assert.assertEquals(1, binlogFiles.size());
            Assert.assertEquals(2, callCount.get());
        }
    }

    // ======================== 辅助方法 ========================

    private DescribeBinlogFilesResult buildSinglePageRdsResult() {
        DescribeBinlogFilesResult result = new DescribeBinlogFilesResult();
        BinlogFile binlogFile = new BinlogFile();
        List<BinlogFile> files = new ArrayList<>();
        files.add(binlogFile);
        result.setItems(files);
        result.setTotalRecords(1);
        result.setItemsNumbers(1);
        result.setPageNumbers(1);
        return result;
    }

    private DescribeUnifyArchiveLogFilesResult buildSinglePageDbsResult() {
        DescribeUnifyArchiveLogFilesResult result = new DescribeUnifyArchiveLogFilesResult();
        ArchiveLogPages archiveLogPages = new ArchiveLogPages();
        result.setData(archiveLogPages);
        result.setSuccess("true");
        result.setCode("200");
        archiveLogPages.setPageNumber(1);
        archiveLogPages.setTotalElements(1);
        archiveLogPages.setPageSize(1);
        DbsBinlogFile dbsBinlogFile = new DbsBinlogFile();
        List<DbsBinlogFile> dbsBinlogFiles = new ArrayList<>();
        dbsBinlogFiles.add(dbsBinlogFile);
        archiveLogPages.setContent(dbsBinlogFiles);
        return result;
    }
}
