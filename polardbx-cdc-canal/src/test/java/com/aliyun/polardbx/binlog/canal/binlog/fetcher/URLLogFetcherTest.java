/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.cache.CacheManager;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.InputStream;
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
        public void skip(long n) throws IOException {
            is.skip(n);
        }
    }
}
