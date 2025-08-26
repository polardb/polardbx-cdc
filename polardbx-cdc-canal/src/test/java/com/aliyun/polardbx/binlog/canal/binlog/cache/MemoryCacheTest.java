/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class MemoryCacheTest extends BaseTest {

    @Test
    public void multiAllocateTest()
        throws IOException, InterruptedException, ExecutionException, NoSuchFieldException, IllegalAccessException {
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_AUTO_ADJUST, "false");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_LIMIT, "8192");
        mockConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "1024");
        CacheManager cacheManager = new CacheManager();
        registerCacheManager(cacheManager);
        List<MemoryCache> memoryCacheList = new ArrayList<>();
        InputStream in = new ByteArrayInputStream(new byte[0]);
        AtomicInteger sequencer = new AtomicInteger();
        for (int i = 0; i < 32; i++) {
            MemoryCache memoryCache = new MemoryCache("test-dn", "test-url", i, 1024, sequencer);
            memoryCache.setProgressListener(listener);
            memoryCacheList.add(memoryCache);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(16);
        List<Future<?>> futureList = new ArrayList<>();
        for (MemoryCache memoryCache : memoryCacheList) {
            Future<?> f = executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        memoryCache.fetchData(in);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        try {
                            memoryCache.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }

                }
            });
            futureList.add(f);
        }
        for (Future future : futureList) {
            future.get();
        }

        Assert.assertEquals(32, sequencer.get());
    }

    private void registerCacheManager(CacheManager cacheManager) throws NoSuchFieldException, IllegalAccessException {
        cacheManager.registerStorage("test1");
        cacheManager.registerStorage("test2");
        registerSpringObject("cacheManager", cacheManager);
    }

    @Test
    public void testReadCache() throws Exception {
        CacheManager cacheManager = Mockito.mock(CacheManager.class);
        registerCacheManager(cacheManager);
        byte[] buffer = new byte[512];
        String dnName = "test-dn";
        Mockito.when(cacheManager.allocateBuffer(dnName, "test-url", 0, 0)).thenReturn(buffer);
        String inputData = "this is test data!";
        byte[] inputBytes = inputData.getBytes("utf8");
        InputStream is = new ByteArrayInputStream(inputBytes, 0, inputBytes.length);
        AtomicInteger sequencer = new AtomicInteger();
        Cache cache = new MemoryCache("test-dn", "test-url", 0, 0, sequencer);
        cache.setProgressListener(listener);
        cache.fetchData(is);
        byte[] readData = new byte[512];
        int readLen = cache.read(readData, 0, 512);
        String str = new String(readData, 0, readLen);
        Assert.assertEquals(inputData, str);
        cache.close();
        Mockito.verify(cacheManager, Mockito.times(1)).releaseBuffer(dnName, buffer);
    }

    @Test
    public void testSkipCache() throws Exception {
        CacheManager cacheManager = Mockito.mock(CacheManager.class);
        registerCacheManager(cacheManager);
        byte[] buffer = new byte[512];
        String dnName = "test-dn";
        Mockito.when(cacheManager.allocateBuffer(dnName, "test-url", 0, 0)).thenReturn(buffer);
        String inputData = "this is test data!";
        byte[] inputBytes = inputData.getBytes("utf8");
        byte[] headerBytes = new byte[4 + inputBytes.length];
        System.arraycopy(inputBytes, 0, headerBytes, 4, inputBytes.length);
        InputStream is = new ByteArrayInputStream(headerBytes, 0, headerBytes.length);
        AtomicInteger sequencer = new AtomicInteger();
        Cache cache = new MemoryCache("test-dn", "test-url", 0, 0, sequencer);
        cache.setProgressListener(listener);
        cache.fetchData(is);
        byte[] readData = new byte[512];
        cache.skip(4);
        int readLen = cache.read(readData, 0, 512);
        String str = new String(readData, 0, readLen);
        Assert.assertEquals(inputData, str);
        cache.close();
        Mockito.verify(cacheManager, Mockito.times(1)).releaseBuffer(dnName, buffer);
    }

    CacheProgressListener listener = new CacheProgressListener() {
        @Override
        public void onStart() {

        }

        @Override
        public void onAllocateBuffer() {

        }

        @Override
        public void onProgress(long bytesRead) {

        }

        @Override
        public void onFinish() {

        }
    };
}
