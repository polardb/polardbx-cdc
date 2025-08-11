/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

public class CacheManagerTest extends BaseTest {

    final String url = "test";

    @Test
    public void testGetFileName() {
        String url =
            "http://rdslog-hz-v3-3az.oss-cn-hangzhou-internal.aliyuncs.com/custins85838872/hostins34345966/mysql-bin.000070?OSSAccessKeyId=STS.NTrWsvuoXVHB3qRBPjaYHQUF5&Expires=1736079622&security-token=CAIS%2BwJ1q6Ft5B2yfSjIr5fHHMnCmLB54YqpMVfjplA%2FbdZkvpDt1zz2IHpNfXZuAuEctvo%2BnGtR5%2F8clq5ZSpFfQlfYNXfvJE%2F8q1HPWZHInuDox1dt6vT8a37xZjf%2F2MjNGaqbKPrWZvaqbX3diyZ32sGUXD6%2BXlujQ%2Frr7Jl8dYY4UxWfZzhLD8ssAmkEksIBMmbLPvuAKwPjhnGqbHBloQ1hk2hym%2FzdhMSX8UjZl0aoiL1X9Z79OZyjYIxsLtJhCJLu1f4xfbfazC9W8EgXpPwm3PMevnXlxojNXwQKs0TfaLuNroA%2FfVBDC%2FJkS%2FIenp%2FVjuZlv%2BHfrYPzxitWMPtdOyalH9r5mZSaQ7LzbohoLe2iai2TyLeUKoKwqRxhZmkAcRlNf9cx6%2F7pwYaPgVowQ47QGzCiCm%2FLI8DtW3GMRxPchh6he1bekrwjlylbNbIie%2F5aIQphfGUtxjf6CYxNGFd3WFgET5F8ZGSejzm%2BAEqZ1YhKOSU%2Bphk%2FGoABXurMqZ7tYUX4Wbr0S%2FNM7eQrDEAfGv30urkQ4AR33S8%2BBktxyc7Is8EyZ%2BpU6%2F8PJejFQg5CN55HnxefOryIglpeY0IYb%2B84%2Brs5u7ml%2BmxQKGsEyMSHvFZ9E4d8g%2FD%2F36hmYuEaERy%2FUT93iUDOsTaYQCbhguuklmB3dykjtRkgAA%3D%3D&Signature=6CkmIJC0Zy0FB3rxpqiXW%2B%2By3%2Fc%3D";
        CacheManager cacheManager = new CacheManager();
        String fileName = cacheManager.getFileName(url);
        Assert.assertEquals("mysql-bin.000070", fileName);
    }

    @Test(timeout = 30000)
    public void cacheSequenceTest() throws ExecutionException, InterruptedException {
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_AUTO_ADJUST, "false");
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "16");
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_LIMIT, "8");
        AtomicInteger sequencer = new AtomicInteger();
        LinkedBlockingQueue<Integer> sequenceList = new LinkedBlockingQueue<>();
        CacheManager cacheManager = new CacheManager();
        cacheManager.registerStorage("test1");
        cacheManager.registerStorage("test2");
        List<Task> taskList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            taskList.add(new Task(i, sequenceList, sequencer, cacheManager));
        }
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future> futureList = new ArrayList<>();
        for (Task t : taskList) {
            futureList.add(executorService.submit(t));
        }
        for (Future f : futureList) {
            f.get();
        }
        executorService.shutdown();
        for (int i = 0; i < 100; i++) {
            Assert.assertEquals(i, sequenceList.take().intValue());
        }
    }

    @Test
    public void cacheSequenceNotPrepareExceptionTest() throws InterruptedException {
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_AUTO_ADJUST, "false");
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "16");
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_LIMIT, "8");
        String dnName = "test-dn-1";
        CacheManager cacheManager = new CacheManager();
        byte[] size = cacheManager.allocateBuffer(dnName, url, 0, 16);
        Assert.assertEquals(16, size.length);
        cacheManager.releaseBuffer(dnName, size);
    }

    @Test
    public void testAutoMaxSizeLimit() {
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_AUTO_ADJUST, "true");
        List<String> urlList = new ArrayList<>();
        CacheManager cacheManager = new CacheManager();
        for (int i = 0; i < 1; i++) {
            urlList.add(url + i);
        }
        for (String u : urlList) {
            cacheManager.registerStorage(u);
        }
        double useRatio =
            DynamicApplicationConfig.getDouble(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_AUTO_ADJUST_USE_RATIO);
        long maxMemory = (long) (Runtime.getRuntime().maxMemory() * useRatio);
        long maxSize = cacheManager.maxCacheSize();
        long expectedSize = Math.min(
            DynamicApplicationConfig.getLong(ConfigKeys.TASK_DUMP_DN_DEFAULT_BINLOG_FILE_SIZE) * urlList.size(),
            maxMemory);
        Assert.assertEquals(expectedSize, maxSize);
        for (String u : urlList) {
            cacheManager.unregisterStorage(u);
        }
    }

    @Test(timeout = 30000)
    public void testAutoReleaseBuffer()
        throws ExecutionException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_AUTO_ADJUST, "false");
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_UNIT_SIZE, "16");
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_SIZE_LIMIT, "2000");
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_CLEAN_INTERVAL_SECOND, "1");
        setConfig(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_CACHE_KEEP_ALIVE_TIMEOUT_SECOND, "1");
        CacheManager cacheManager = new CacheManager();
        cacheManager.registerStorage(url);
        AtomicInteger sequencer = new AtomicInteger();
        LinkedBlockingQueue<Integer> sequenceList = new LinkedBlockingQueue<>();
        List<Task> taskList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            taskList.add(new Task(i, sequenceList, sequencer, cacheManager));
        }
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Future> futureList = new ArrayList<>();
        for (Task t : taskList) {
            futureList.add(executorService.submit(t));
        }
        for (Future f : futureList) {
            f.get();
        }
        executorService.shutdown();
        cacheManager.unregisterStorage(url);

        //50 25 13 7 4 2 1 0
        //8次
        Thread.sleep(10000);

        CacheDistribution distribution = cacheManager.getDistribution();
        Field bufferPoolsField = CacheManager.class.getDeclaredField("bufferPools");
        bufferPoolsField.setAccessible(true);

        LinkedBlockingQueue<byte[]> bufferPools = (LinkedBlockingQueue<byte[]>) bufferPoolsField.get(cacheManager);

        Assert.assertEquals(0, bufferPools.size());
        Assert.assertEquals(0, distribution.getRefCount());

    }

    public class Task implements Runnable {

        final int seq;
        final LinkedBlockingQueue<Integer> sequenceList;
        final AtomicInteger sequencer;
        private CacheManager cacheManager;

        public Task(int seq, LinkedBlockingQueue<Integer> sequenceList, AtomicInteger sequencer,
                    CacheManager cacheManager) {
            this.seq = seq;
            this.sequenceList = sequenceList;
            this.sequencer = sequencer;
            this.cacheManager = cacheManager;
        }

        @Override
        public void run() {
            byte[] buf = null;
            try {
                while (sequencer.get() < seq) {
                    LockSupport.parkNanos(100000);
                    if (Thread.currentThread().isInterrupted()) {
                        throw new PolardbxException("thread interrupted when wait for allocate cache buffer!");
                    }
                }
                Thread.sleep(RandomUtils.nextInt(10, 20));
                sequenceList.add(seq);
                buf = cacheManager.allocateBuffer("test-dn-1", url, seq, 16);
                sequencer.incrementAndGet();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            } finally {
                cacheManager.releaseBuffer("test-dn-1", buf);
            }
        }
    }
}
