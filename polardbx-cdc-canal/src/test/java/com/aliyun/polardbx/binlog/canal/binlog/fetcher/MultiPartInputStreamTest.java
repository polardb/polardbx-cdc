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

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
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
        MultiPartInputStream mis =
            MultiPartInputStreamFactory.create(url, 163840, "test-dn", "my-bin.001", executorService);

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
        MultiPartInputStream mis =
            MultiPartInputStreamFactory.create(url, Long.MAX_VALUE, "test-dn", "my-bin.001", executorService);

        Assert.assertNotNull(mis);
        Field fileSizeField = MultiPartInputStream.class.getDeclaredField("fileSize");
        fileSizeField.setAccessible(true);
        Assert.assertEquals(Long.MAX_VALUE, fileSizeField.get(mis));
        Assert.assertTrue(SearchMode.isSearchInQuickMode() && BinlogDumpContext.isSearch());

    }
}
