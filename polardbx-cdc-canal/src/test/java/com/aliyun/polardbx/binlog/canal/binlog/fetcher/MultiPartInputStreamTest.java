/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.SearchMode;
import com.aliyun.polardbx.binlog.canal.binlog.BinlogDumpContext;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheManager;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import javax.net.ssl.SSLHandshakeException;
import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.SocketTimeoutException;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class MultiPartInputStreamTest extends BaseTest {

    @Test
    public void skipTest() throws IOException, NoSuchFieldException, IllegalAccessException {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "8192");
        CacheManager cacheManager = new CacheManager();
        cacheManager.registerStorage("test1");
        registerSpringObject("cacheManager", cacheManager);
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_RANGES_SUPPORT)).thenReturn("bytes");
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_CONTENT_LENGTH)).thenReturn("163840");
        String url = "my-proto://111.1.1.1.1/skip";
        mockUrlConnection(url, urlConnection);
        ExecutorService executorService = Mockito.mock(ExecutorService.class);
        MultiPartInputStream multiPartInputStream = Mockito.mock(MultiPartInputStream.class,
            withSettings().useConstructor("my-proto://111.1.1.1.1/skip", 8192L, "test-dn", "my-bin.001",
                executorService));
        doCallRealMethod().when(multiPartInputStream).skip(anyLong());
        multiPartInputStream.skip(8192L * 2);
        Field sequencer = MultiPartInputStream.class.getDeclaredField("sequencer");
        sequencer.setAccessible(true);
        Assert.assertEquals(2, ((AtomicInteger) sequencer.get(multiPartInputStream)).get());
    }

    @Test
    public void isClosedTest() throws IOException, NoSuchFieldException, IllegalAccessException {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "8192");
        mockCacheManager();
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_RANGES_SUPPORT)).thenReturn("bytes");
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_CONTENT_LENGTH)).thenReturn("163840");
        String url = "my-proto://111.1.1.1.1/close";
        mockUrlConnection(url, urlConnection);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        MultiPartInputStream multiPartInputStream = Mockito.mock(MultiPartInputStream.class,
            withSettings().useConstructor("my-proto://111.1.1.1.1/close", 8192L, "test-dn", "my-bin.001",
                executorService));
        when(multiPartInputStream.isClosed()).thenCallRealMethod();
        doCallRealMethod().when(multiPartInputStream).close();
        doCallRealMethod().when(multiPartInputStream).closePartStream();
        multiPartInputStream.close();
        Assert.assertTrue(multiPartInputStream.isClosed());
    }

    private CacheManager mockCacheManager() throws NoSuchFieldException, IllegalAccessException {
        CacheManager cacheManager = Mockito.mock(CacheManager.class, withSettings().useConstructor());
        doCallRealMethod().when(cacheManager).registerStorage(anyString());
        cacheManager.registerStorage("test");
        registerSpringObject("cacheManager", cacheManager);
        return cacheManager;
    }

    @Test
    public void testMultiDownload() throws NoSuchFieldException, IOException, IllegalAccessException {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "8192");
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_RANGES_SUPPORT)).thenReturn("bytes");
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_CONTENT_LENGTH)).thenReturn("163840");
        String url = "my-proto://111.1.1.1.1/f1";
        mockUrlConnection(url, urlConnection);
        CacheManager manager = mockCacheManager();
        ExecutorService executorService = Mockito.mock(ExecutorService.class);
        InputStream mis = MultiPartInputStreamFactory.create(url, 163840, "test-dn", "my-bin.001", executorService);

        verify(executorService, times(163840 / 8192)).submit(any(Runnable.class));
        Assert.assertNotNull(mis);
        Field fileSizeField = MultiPartInputStream.class.getDeclaredField("fileSize");
        fileSizeField.setAccessible(true);

        Assert.assertEquals(163840L, fileSizeField.get(mis));
        Assert.assertTrue(SearchMode.isSearchInQuickMode() && BinlogDumpContext.isSearch());
    }

    @Test
    public void testFileSize() throws IOException, NoSuchFieldException, IllegalAccessException {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
        String url = "my-proto://111.1.1.1.1/f2";
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_RANGES_SUPPORT)).thenReturn("");
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_CONTENT_LENGTH))
            .thenReturn(Long.MAX_VALUE + "");
        mockUrlConnection(url, urlConnection);
        ExecutorService executorService = Mockito.mock(ExecutorService.class);
        InputStream mis =
            MultiPartInputStreamFactory.create(url, Long.MAX_VALUE, "test-dn", "my-bin.001", executorService);

        Assert.assertNotNull(mis);
        Field fileSizeField = MultiPartInputStream.class.getDeclaredField("fileSize");
        fileSizeField.setAccessible(true);
        Assert.assertEquals(Long.MAX_VALUE, fileSizeField.get(mis));
        Assert.assertTrue(SearchMode.isSearchInQuickMode() && BinlogDumpContext.isSearch());

    }

    @Test
    public void testSingleSkip() throws IOException, NoSuchFieldException, IllegalAccessException {
        MultiPartInputStream multiPartInputStream = Mockito.mock(MultiPartInputStream.class);
        Mockito.when(multiPartInputStream.skip(anyLong())).thenCallRealMethod();
        InputStream is = Mockito.mock(InputStream.class);
        Field partCountField = MultiPartInputStream.class.getDeclaredField("partCount");
        partCountField.setAccessible(true);
        partCountField.set(multiPartInputStream, 1);
        Field finField = MultiPartInputStream.class.getDeclaredField("fin");
        finField.setAccessible(true);
        finField.set(multiPartInputStream, is);
        Mockito.when(is.skip(anyLong())).thenReturn(1L);
        long skip = multiPartInputStream.skip(1);
        Assert.assertEquals(1, skip);
    }

    @Test
    public void testConnectRetryWhenTimeout() throws IOException {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "8192");
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
        // 不支持分段，走单分区下载路径
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_RANGES_SUPPORT)).thenReturn("");
        // 首次建连超时，重试后成功
        Mockito.doThrow(new SocketTimeoutException("connect timed out")).doNothing().when(urlConnection).connect();
        byte[] data = new byte[16];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) i;
        }
        Mockito.when(urlConnection.getInputStream()).thenReturn(new ByteArrayInputStream(data));
        String url = "my-proto://111.1.1.1.1/timeout-retry";
        mockUrlConnection(url, urlConnection);
        ExecutorService executorService = Mockito.mock(ExecutorService.class);
        // 若建连没有重试逻辑，构造器会直接抛出 SocketTimeoutException
        MultiPartInputStream multiPartInputStream =
            new MultiPartInputStream(url, data.length, "test-dn", "my-bin.001", executorService);
        byte[] buffer = new byte[data.length];
        int total = 0;
        int len;
        while (total < buffer.length && (len = multiPartInputStream.read(buffer, total, buffer.length - total)) != -1) {
            total += len;
        }
        Assert.assertEquals(data.length, total);
        // 共 3 次建连：partCount 探测首次超时 + 重试成功，单分区流打开再建连 1 次
        Mockito.verify(urlConnection, times(3)).connect();
    }

    /**
     * 建连重试次数可配置：配为 0 时首次建连失败就直接上抛，不再重试
     */
    @Test
    public void testConnectRetryCountConfigurable() throws IOException {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "8192");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CONNECT_RETRY_COUNT, "0");
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_RANGES_SUPPORT)).thenReturn("");
        // 建连恒定超时
        Mockito.doThrow(new SocketTimeoutException("connect timed out")).when(urlConnection).connect();
        String url = "my-proto://111.1.1.1.1/no-connect-retry";
        mockUrlConnection(url, urlConnection);
        ExecutorService executorService = Mockito.mock(ExecutorService.class);
        try {
            new MultiPartInputStream(url, 16, "test-dn", "my-bin.001", executorService);
            Assert.fail("retry count 0 should not retry");
        } catch (SocketTimeoutException e) {
            Assert.assertEquals("connect timed out", e.getMessage());
        }
        // 仅建连一次，说明没有发生退避重试
        Mockito.verify(urlConnection, times(1)).connect();
    }

    /**
     * 断言确定性建连异常不会触发退避重试：即使重试次数配为 3，也只建连一次并原样上抛
     */
    private void assertDeterministicExceptionNotRetried(IOException exception, String url) throws IOException {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "8192");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CONNECT_RETRY_COUNT, "3");
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_RANGES_SUPPORT)).thenReturn("");
        Mockito.doThrow(exception).when(urlConnection).connect();
        mockUrlConnection(url, urlConnection);
        ExecutorService executorService = Mockito.mock(ExecutorService.class);
        try {
            new MultiPartInputStream(url, 16, "test-dn", "my-bin.001", executorService);
            Assert.fail("deterministic exception should not retry : " + exception.getClass().getSimpleName());
        } catch (IOException e) {
            // 原样上抛，不得被包装成其它类型
            Assert.assertSame(exception, e);
        }
        Mockito.verify(urlConnection, times(1)).connect();
    }

    /**
     * 证书校验失败属于确定性错误，重试不会改变结果，应直接上抛而不进入退避重试
     */
    @Test
    public void testCertificateHandshakeFailureShouldNotRetry() throws IOException {
        SSLHandshakeException exception = new SSLHandshakeException("cert not trusted");
        exception.initCause(new CertificateException("unable to find valid certification path"));
        assertDeterministicExceptionNotRetried(exception, "my-proto://111.1.1.1.1/ssl-handshake");
    }

    /**
     * 握手期连接被对端中断（无证书类 cause）属于瞬时故障，不能因为它是 SSLHandshakeException 就放弃重试，
     * 否则会把可恢复的网络抖动升级成任务重启
     */
    @Test
    public void testHandshakeInterruptedShouldRetry() throws IOException {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "8192");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CONNECT_RETRY_COUNT, "2");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CONNECT_RETRY_BACKOFF_BASE_MS, "1");
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_RANGES_SUPPORT)).thenReturn("");
        Mockito.doThrow(new SSLHandshakeException("Remote host terminated the handshake"))
            .when(urlConnection).connect();
        String url = "my-proto://111.1.1.1.1/handshake-interrupted";
        mockUrlConnection(url, urlConnection);
        ExecutorService executorService = Mockito.mock(ExecutorService.class);
        try {
            new MultiPartInputStream(url, 16, "test-dn", "my-bin.001", executorService);
            Assert.fail("handshake interrupted should finally throw");
        } catch (SSLHandshakeException e) {
            Assert.assertEquals("Remote host terminated the handshake", e.getMessage());
        }
        // 重试次数配为 2，共建连 3 次，证明瞬时握手故障仍保留了重试能力
        Mockito.verify(urlConnection, times(3)).connect();
    }

    /**
     * 重定向过多属于确定性错误，同样不应重试
     */
    @Test
    public void testProtocolExceptionShouldNotRetry() throws IOException {
        assertDeterministicExceptionNotRetried(new ProtocolException("Server redirected too many times"),
            "my-proto://111.1.1.1.1/too-many-redirects");
    }

    /**
     * 5xx 属于服务端瞬时故障（如 OSS 503 SlowDown 限流），必须在建连重试边界内被识别并重试，
     * 而不能拖到后续 getInputStream 阶段才以 IOException 暴露（那时已在重试边界之外）
     */
    @Test
    public void testServerErrorShouldRetry() throws IOException {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "8192");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CONNECT_RETRY_COUNT, "2");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CONNECT_RETRY_BACKOFF_BASE_MS, "1");
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_RANGES_SUPPORT)).thenReturn("");
        // 建连本身成功，但服务端恢复 503
        Mockito.when(urlConnection.getResponseCode()).thenReturn(503);
        String url = "my-proto://111.1.1.1.1/server-error";
        mockUrlConnection(url, urlConnection);
        ExecutorService executorService = Mockito.mock(ExecutorService.class);
        try {
            new MultiPartInputStream(url, 16, "test-dn", "my-bin.001", executorService);
            Assert.fail("5xx should be exposed as IOException");
        } catch (IOException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains("503"));
        }
        // 重试次数配为 2，共建连 3 次（首次 + 2 次重试）
        Mockito.verify(urlConnection, times(3)).connect();
    }

    /**
     * 响应头缺失时必须抛出上层能识别为可重试网络异常的 EOFException，
     * 而不是 Long.parseLong(null) 抛出的、cause 链为空的 NumberFormatException
     */
    @Test
    public void testMissingContentLength() throws IOException {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "8192");
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_RANGES_SUPPORT)).thenReturn("bytes");
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_CONTENT_LENGTH)).thenReturn(null);
        String url = "my-proto://111.1.1.1.1/no-content-length";
        mockUrlConnection(url, urlConnection);
        ExecutorService executorService = Mockito.mock(ExecutorService.class);
        try {
            new MultiPartInputStream(url, 163840, "test-dn", "my-bin.001", executorService);
            Assert.fail("missing content length should fail");
        } catch (EOFException e) {
            Assert.assertTrue(e.getMessage(), e.getMessage().contains(MultiPartInputStream.HEADER_CONTENT_LENGTH));
        }
    }

    /**
     * Content-Length 取值非法属于服务端协议异常，重试无意义，抛普通 IOException 并保留原因
     */
    @Test
    public void testIllegalContentLength() throws IOException {
        mockConfig(ConfigKeys.TASK_RECOVER_SEARCH_TSO_IN_QUICK_MODE, "true");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "8192");
        BinlogDumpContext.setDumpStage(BinlogDumpContext.DumpStage.STAGE_SEARCH);
        HttpURLConnection urlConnection = Mockito.mock(HttpURLConnection.class);
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_RANGES_SUPPORT)).thenReturn("bytes");
        Mockito.when(urlConnection.getHeaderField(MultiPartInputStream.HEADER_CONTENT_LENGTH))
            .thenReturn("not-a-number");
        String url = "my-proto://111.1.1.1.1/illegal-content-length";
        mockUrlConnection(url, urlConnection);
        ExecutorService executorService = Mockito.mock(ExecutorService.class);
        try {
            new MultiPartInputStream(url, 163840, "test-dn", "my-bin.001", executorService);
            Assert.fail("illegal content length should fail");
        } catch (IOException e) {
            Assert.assertFalse(e instanceof EOFException);
            Assert.assertTrue(e.getCause() instanceof NumberFormatException);
        }
    }
}
