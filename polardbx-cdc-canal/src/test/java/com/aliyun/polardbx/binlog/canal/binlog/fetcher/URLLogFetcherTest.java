/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheManager;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;

public class URLLogFetcherTest extends BaseTest {

    private void registerCacheManager(CacheManager cacheManager) throws NoSuchFieldException, IllegalAccessException {
        cacheManager.registerStorage("test1");
        cacheManager.registerStorage("test2");
        registerSpringObject("cacheManager", cacheManager);
    }

    @Test
    public void test() throws IOException, NoSuchFieldException, IllegalAccessException {

        registerCacheManager(new CacheManager());
        try (MockedStatic<MultiPartInputStreamFactory> mockedStatic = Mockito.mockStatic(
            MultiPartInputStreamFactory.class);
            URLLogFetcher urlLogFetcher = new URLLogFetcher("test-storage", "test-file");) {
            InputStream is = URLLogFetcherTest.class.getResourceAsStream("/mysql_bin.19_1");
            MultiPartInputStream mockedInputStream = new MockedInputStream("", 512, "", "", is);
            mockedStatic.when(
                () -> MultiPartInputStreamFactory.create(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(),
                    Mockito.anyString(), Mockito.any())).thenReturn(mockedInputStream);
            ExecutorService executorService = Mockito.mock(ExecutorService.class);
            urlLogFetcher.open("", 0, 512, executorService);
            LogDecoder decoder = new LogDecoder();
            decoder.handle(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
            LogContext lc = new LogContext();
            lc.setServerCharactorSet(new ServerCharactorSet());
            lc.setLogPosition(new LogPosition("", 0));
            int count = 0;
            while (urlLogFetcher.fetch()) {
                LogEvent le = decoder.decode(urlLogFetcher.buffer(), lc);
                count++;
            }
            urlLogFetcher.close();
            Assert.assertTrue(count > 0);
        }

    }

    @Test
    public void testRetryOnTimeout() throws IOException, NoSuchFieldException, IllegalAccessException {

        registerCacheManager(new CacheManager());
        try (MockedStatic<MultiPartInputStreamFactory> mockedStatic = Mockito.mockStatic(
            MultiPartInputStreamFactory.class);
            URLLogFetcher urlLogFetcher = new URLLogFetcher("test-storage", "test-file");) {
            // 第一个流：open 阶段返回 binlog 文件头，之后读取抛超时异常，模拟分段下载连接超时经 check() 包装后上抛
            MultiPartInputStream firstStream = new MockedInputStream("", 512, "", "", new TimeoutAfterHeaderStream());
            // 重连后拿到的第二个流：正常数据
            InputStream dataStream = URLLogFetcherTest.class.getResourceAsStream("/mysql_bin.19_1");
            MultiPartInputStream secondStream = new MockedInputStream("", 512, "", "", dataStream);
            mockedStatic.when(
                () -> MultiPartInputStreamFactory.create(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(),
                    Mockito.anyString(), Mockito.any())).thenReturn(firstStream, secondStream);
            ExecutorService executorService = Mockito.mock(ExecutorService.class);
            urlLogFetcher.open("", 0, 512, executorService);
            LogDecoder decoder = new LogDecoder();
            decoder.handle(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
            LogContext lc = new LogContext();
            lc.setServerCharactorSet(new ServerCharactorSet());
            lc.setLogPosition(new LogPosition("", 0));
            int count = 0;
            while (urlLogFetcher.fetch()) {
                LogEvent le = decoder.decode(urlLogFetcher.buffer(), lc);
                count++;
            }
            urlLogFetcher.close();
            Assert.assertTrue(count > 0);
        }

    }

    /**
     * 重连后 skip 无法跳到目标位点时，必须显式失败，否则已消费过的字节会被再次交付，静默产生重复数据
     */
    @Test
    public void testShortSkipOnReconnectShouldFail() throws IOException, NoSuchFieldException, IllegalAccessException {

        registerCacheManager(new CacheManager());
        try (MockedStatic<MultiPartInputStreamFactory> mockedStatic = Mockito.mockStatic(
            MultiPartInputStreamFactory.class);
            URLLogFetcher urlLogFetcher = new URLLogFetcher("test-storage", "test-file");) {
            MultiPartInputStream firstStream = new MockedInputStream("", 512, "", "", new TimeoutAfterHeaderStream());
            // 重连后拿到的流已到末尾，无法跳回目标位点
            MultiPartInputStream secondStream = new MockedInputStream("", 512, "", "", new ExhaustedStream());
            mockedStatic.when(
                () -> MultiPartInputStreamFactory.create(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(),
                    Mockito.anyString(), Mockito.any())).thenReturn(firstStream, secondStream);
            ExecutorService executorService = Mockito.mock(ExecutorService.class);
            urlLogFetcher.open("", 0, 512, executorService);
            try {
                urlLogFetcher.fetch();
                Assert.fail("short skip should fail");
            } catch (IOException e) {
                Assert.assertTrue(e.getMessage(),
                    e.getMessage().contains("failed to skip to pos : " + URLLogFetcher.BIN_LOG_HEADER_SIZE));
            }
        }

    }

    /**
     * 重连后的流按缓冲区边界短跳时，skipFully 应循环补齐并恢复位点，而不是直接判定失败
     */
    @Test
    public void testShortSkipOnReconnectShouldBeCompleted()
        throws IOException, NoSuchFieldException, IllegalAccessException {

        registerCacheManager(new CacheManager());
        try (MockedStatic<MultiPartInputStreamFactory> mockedStatic = Mockito.mockStatic(
            MultiPartInputStreamFactory.class);
            URLLogFetcher urlLogFetcher = new URLLogFetcher("test-storage", "test-file");) {
            MultiPartInputStream firstStream = new MockedInputStream("", 512, "", "", new TimeoutAfterHeaderStream());
            PartialSkipStream partialSkipStream = new PartialSkipStream();
            MultiPartInputStream secondStream = new MockedInputStream("", 512, "", "", partialSkipStream);
            mockedStatic.when(
                () -> MultiPartInputStreamFactory.create(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(),
                    Mockito.anyString(), Mockito.any())).thenReturn(firstStream, secondStream);
            ExecutorService executorService = Mockito.mock(ExecutorService.class);
            urlLogFetcher.open("", 0, 512, executorService);
            Assert.assertTrue(urlLogFetcher.fetch());
            // 每次只跳 1 字节，需要调用 BIN_LOG_HEADER_SIZE 次才能补齐到目标位点
            Assert.assertEquals(URLLogFetcher.BIN_LOG_HEADER_SIZE, partialSkipStream.getSkipCalls());
        }

    }

    /**
     * FDE 读取到的字节数不足事件头长度时，应抛出带 url 与实际读取长度的 IOException，
     * 而不是 getUint32 边界检查抛出的、不含上下文的 IllegalArgumentException
     */
    @Test
    public void testIncompleteFdeShouldFail() throws IOException, NoSuchFieldException, IllegalAccessException {

        registerCacheManager(new CacheManager());
        try (MockedStatic<MultiPartInputStreamFactory> mockedStatic = Mockito.mockStatic(
            MultiPartInputStreamFactory.class);
            URLLogFetcher urlLogFetcher = new URLLogFetcher("test-storage", "test-file");) {
            MultiPartInputStream stream = new MockedInputStream("", 512, "", "", new ShortFdeStream());
            mockedStatic.when(
                () -> MultiPartInputStreamFactory.create(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(),
                    Mockito.anyString(), Mockito.any())).thenReturn(stream);
            ExecutorService executorService = Mockito.mock(ExecutorService.class);
            try {
                urlLogFetcher.open("", 100, 512, executorService);
                Assert.fail("incomplete fde should fail");
            } catch (IOException e) {
                Assert.assertTrue(e.getMessage(), e.getMessage().contains("incomplete format description event"));
            }
        }

    }

    /**
     * 重试预算耗尽后，必须抛出原始异常而不是被包装成其它类型，否则上层无法按异常类型决策
     */
    @Test
    public void testRetryExhaustedShouldThrowOriginalException()
        throws IOException, NoSuchFieldException, IllegalAccessException {

        registerCacheManager(new CacheManager());
        try (MockedStatic<MultiPartInputStreamFactory> mockedStatic = Mockito.mockStatic(
            MultiPartInputStreamFactory.class);
            URLLogFetcher urlLogFetcher = new URLLogFetcher("test-storage", "test-file");) {
            // 每次重连都拿到同一个流，读取恒定超时，直到重试预算耗尽
            MultiPartInputStream stream = new MockedInputStream("", 512, "", "", new TimeoutAfterHeaderStream());
            mockedStatic.when(
                () -> MultiPartInputStreamFactory.create(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(),
                    Mockito.anyString(), Mockito.any())).thenReturn(stream);
            ExecutorService executorService = Mockito.mock(ExecutorService.class);
            urlLogFetcher.open("", 0, 512, executorService);
            try {
                urlLogFetcher.fetch();
                Assert.fail("retry exhausted should throw original exception");
            } catch (PolardbxException e) {
                Assert.assertTrue(e.getCause() instanceof SocketTimeoutException);
            }
        }

    }

    /**
     * 读到流末尾时 read 返回 -1，此时不能把 -1 累加进已读位点，否则位点会被减 1，
     * 重连后从错误位点开始读取会导致数据重复
     */
    @Test
    public void testReadPosNotDecreasedOnEof() throws IOException, NoSuchFieldException, IllegalAccessException {

        registerCacheManager(new CacheManager());
        int bodyLen = 100;
        long fileSize = URLLogFetcher.BIN_LOG_HEADER_SIZE + bodyLen;
        byte[] content = new byte[(int) fileSize];
        System.arraycopy(URLLogFetcher.BINLOG_MAGIC, 0, content, 0, URLLogFetcher.BINLOG_MAGIC.length);
        try (MockedStatic<MultiPartInputStreamFactory> mockedStatic = Mockito.mockStatic(
            MultiPartInputStreamFactory.class);
            URLLogFetcher urlLogFetcher = new URLLogFetcher("test-storage", "test-file");) {
            MultiPartInputStream stream =
                new MockedInputStream("", fileSize, "", "", new ByteArrayInputStream(content));
            mockedStatic.when(
                () -> MultiPartInputStreamFactory.create(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(),
                    Mockito.anyString(), Mockito.any())).thenReturn(stream);
            ExecutorService executorService = Mockito.mock(ExecutorService.class);
            urlLogFetcher.open("", 0, fileSize, executorService);
            //noinspection StatementWithEmptyBody
            while (urlLogFetcher.fetch()) {
            }
            Assert.assertEquals(fileSize, urlLogFetcher.readSize());
        }

    }

    /**
     * 读取重试次数可配置：配为 0 时首次异常就直接上抛，不再重连
     */
    @Test
    public void testReadRetryCountConfigurable()
        throws IOException, NoSuchFieldException, IllegalAccessException {

        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_READ_RETRY_COUNT, "0");
        registerCacheManager(new CacheManager());
        try (MockedStatic<MultiPartInputStreamFactory> mockedStatic = Mockito.mockStatic(
            MultiPartInputStreamFactory.class);
            URLLogFetcher urlLogFetcher = new URLLogFetcher("test-storage", "test-file");) {
            MultiPartInputStream stream = new MockedInputStream("", 512, "", "", new TimeoutAfterHeaderStream());
            mockedStatic.when(
                () -> MultiPartInputStreamFactory.create(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(),
                    Mockito.anyString(), Mockito.any())).thenReturn(stream);
            ExecutorService executorService = Mockito.mock(ExecutorService.class);
            urlLogFetcher.open("", 0, 512, executorService);
            try {
                urlLogFetcher.fetch();
                Assert.fail("retry count 0 should not retry");
            } catch (PolardbxException e) {
                Assert.assertTrue(e.getCause() instanceof SocketTimeoutException);
            }
            // 只有 open 阶段建过一次流，说明读取失败后没有发生重连
            mockedStatic.verify(
                () -> MultiPartInputStreamFactory.create(Mockito.anyString(), Mockito.anyLong(), Mockito.anyString(),
                    Mockito.anyString(), Mockito.any()), Mockito.times(1));
        }

    }

    /**
     * 返回 binlog 文件头后，后续读取均抛出被 PolardbxException 包装的 SocketTimeoutException，
     * 模拟分段下载模式下分片连接超时后经 PartStream.check() 上抛的异常形态
     */
    private static class TimeoutAfterHeaderStream extends InputStream {
        private int pos = 0;

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            if (read(one, 0, 1) == -1) {
                return -1;
            }
            return one[0] & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (pos < URLLogFetcher.BINLOG_MAGIC.length) {
                int n = Math.min(len, URLLogFetcher.BINLOG_MAGIC.length - pos);
                System.arraycopy(URLLogFetcher.BINLOG_MAGIC, pos, b, off, n);
                pos += n;
                return n;
            }
            throw new PolardbxException(new SocketTimeoutException("read timed out"));
        }

        @Override
        public long skip(long n) {
            // 精确跳过，让重连后的位点校验不成为验证重试行为时的干扰因素
            return n;
        }
    }

    /**
     * 先给出合法的 binlog magic，随后 FDE 读取只返回不足事件头长度的字节数
     */
    private static class ShortFdeStream extends InputStream {

        private static final int INCOMPLETE_FDE_LEN = 5;

        private int pos = 0;

        @Override
        public int read() {
            return -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (pos < URLLogFetcher.BINLOG_MAGIC.length) {
                int n = Math.min(len, URLLogFetcher.BINLOG_MAGIC.length - pos);
                System.arraycopy(URLLogFetcher.BINLOG_MAGIC, pos, b, off, n);
                pos += n;
                return n;
            }
            // 真实写入数据再返回长度，确保失败来自长度校验而非缓冲区中的残留内容；
            // 不足 EVENT_LEN_OFFSET + 4，不够解析出事件长度
            int shortLen = Math.min(len, INCOMPLETE_FDE_LEN);
            Arrays.fill(b, off, off + shortLen, (byte) 0xff);
            return shortLen;
        }

        @Override
        public long skip(long n) {
            return n;
        }
    }

    /**
     * 已到流末尾：skip 跳不动、read 也读不到数据，对应服务端提前结束响应导致无法回到目标位点。
     * 真实流不会出现“能读到数据却跳不动”的组合，所以 read 与 skip 保持一致的 EOF 语义
     */
    private static class ExhaustedStream extends InputStream {

        @Override
        public int read() {
            return -1;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return -1;
        }

        @Override
        public long skip(long n) {
            return 0;
        }
    }

    /**
     * skip 每次只跳 1 字节，模拟 HTTP/Zstd 等包装流按缓冲区边界短跳；read 正常返回数据。
     * 短跳是 InputStream#skip 允许的合法行为，不应被当成无法恢复位点
     */
    private static class PartialSkipStream extends InputStream {

        private int skipCalls = 0;

        @Override
        public int read() {
            return 0;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return len;
        }

        @Override
        public long skip(long n) {
            skipCalls++;
            return n > 0 ? 1 : 0;
        }

        int getSkipCalls() {
            return skipCalls;
        }
    }

    public class MockedInputStream extends MultiPartInputStream {

        private InputStream is;

        public MockedInputStream(String url, long fileSize, String storageInstanceId, String fileName, InputStream is)
            throws IOException {
            super(url, fileSize, storageInstanceId, fileName, Mockito.mock(ExecutorService.class));
            this.is = is;
        }

        @Override
        protected void open() throws IOException {

        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            return is.read(b, off, len);
        }

        @Override
        public long skip(long n) throws IOException {
            return is.skip(n);
        }
    }
}
