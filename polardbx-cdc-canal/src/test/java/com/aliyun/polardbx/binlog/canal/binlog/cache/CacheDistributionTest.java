/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

@Slf4j
public class CacheDistributionTest {

    @Test
    public void tryAcquireAtLeastOnceWithPreferDn()
        throws ExecutionException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        acquireTest(CacheDistributionType.PREEMPTIVE, "dn-1");
    }

    @Test
    public void tryAcquireAtLeastOnce()
        throws ExecutionException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        acquireTest(CacheDistributionType.PREEMPTIVE, null);
    }

    @Test
    public void tryAcquireAverageWithPreferDn()
        throws ExecutionException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        acquireTest(CacheDistributionType.AVERAGE, "dn-0");
    }

    @Test
    public void tryAcquireAverage()
        throws ExecutionException, InterruptedException, NoSuchFieldException, IllegalAccessException {
        acquireTest(CacheDistributionType.AVERAGE, null);
    }

    private void acquireTest(CacheDistributionType distributionType, String preferDn)
        throws InterruptedException, ExecutionException, NoSuchFieldException, IllegalAccessException {
        final AtomicInteger dn0Count = new AtomicInteger();
        final AtomicInteger dn1Count = new AtomicInteger();
        Map<String, AtomicInteger> dnCountMap = new HashMap<>();
        dnCountMap.put("dn-0", dn0Count);
        dnCountMap.put("dn-1", dn1Count);
        long bufferSize = 5;
        long maxSize = 10;
        CacheDistributionStrategy distributionStrategy = CacheDistributionStrategyFactory.create(distributionType);
        CacheDistribution distribution = new CacheDistribution(2, bufferSize, maxSize, distributionStrategy);
        Assert.assertEquals(distributionType, distributionStrategy.getType());
        ExecutorService service = Executors.newFixedThreadPool(10);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        AtomicInteger dn0Down = new AtomicInteger(10);
        AtomicInteger dn1Down = new AtomicInteger(10);
        Map<String, AtomicInteger> countDownMap = new HashMap<>();
        Assert.assertEquals(bufferSize, distribution.getUnitBuffer());
        countDownMap.put("dn-0", dn0Down);
        countDownMap.put("dn-1", dn1Down);
        List<String> errorMsg = new CopyOnWriteArrayList<>();
        for (int i = 0; i < 10; i++) {
            final String dnName = "dn-0";
            tasks.add(() -> {
                while (!distribution.tryAcquireBuffer(dnName)) {
                    LockSupport.parkNanos(100000);
                }
                if (distribution.getRefCount() > 2) {
                    errorMsg.add("dn-0 refCount is " + distribution.getRefCount());
                }
                dnCountMap.get(dnName).incrementAndGet();
                dn0Down.decrementAndGet();
                return true;
            });
        }

        for (int i = 0; i < 10; i++) {
            final String dnName = "dn-1";
            tasks.add(() -> {
                while (!distribution.tryAcquireBuffer(dnName)) {
                    LockSupport.parkNanos(100000);
                }
                if (distribution.getRefCount() > 2) {
                    errorMsg.add("dn-1 refCount is " + distribution.getRefCount());
                }
                dnCountMap.get(dnName).incrementAndGet();
                dn1Down.decrementAndGet();
                return true;
            });
        }

        AtomicBoolean runnable = new AtomicBoolean(true);
        Thread t = new Thread(() -> {
            while (runnable.get()) {
                int dnCount = 0;
                for (Map.Entry<String, AtomicInteger> entry : dnCountMap.entrySet()) {
                    if (entry.getValue().get() > 0) {
                        dnCount++;
                    }
                }
                if (StringUtils.isNotBlank(preferDn) && countDownMap.get(preferDn).get() > 0) {
                    if (dnCountMap.get(preferDn).get() > 0) {
                        distribution.releaseBuffer(preferDn);
                        dnCountMap.get(preferDn).decrementAndGet();
                        log.info("release dn : " + preferDn);
                    }
                } else {
                    for (Map.Entry<String, AtomicInteger> entry : dnCountMap.entrySet()) {
                        if (entry.getValue().get() > 0) {
                            distribution.releaseBuffer(entry.getKey());
                            entry.getValue().decrementAndGet();
                            log.info("release dn : " + entry.getKey());
                        }
                    }
                }
            }
        });
        t.setDaemon(true);
        t.start();
        List<Future<Boolean>> futureList = service.invokeAll(tasks);
        for (Future f : futureList) {
            f.get();
        }
        runnable.set(false);
        service.shutdown();
        Assert.assertEquals(JSON.toJSONString(errorMsg), 0, errorMsg.size());
    }

    @Test
    public void buildDetailTest() {
        CacheDistribution distribution = new CacheDistribution(2, 5, 10,
            CacheDistributionStrategyFactory.create(CacheDistributionType.PREEMPTIVE));
        String detail = distribution.buildDetail();
        String expected = "strategy: PREEMPTIVE\n"
            + "useBufferCounter: 0\n";
        Assert.assertEquals(expected, detail);
    }

    @Test
    public void checkBufferLimitAndStrategyTest() {
        CacheDistribution distribution = new CacheDistribution(2, 5, 10,
            CacheDistributionStrategyFactory.create(CacheDistributionType.PREEMPTIVE));
        distribution.checkBufferLimitAndStrategy(2, 5, CacheDistributionType.AVERAGE, 2);
        String detail = distribution.buildDetail();
        String expected = "strategy: AVERAGE\n"
            + "useBufferCounter: 0\n";
        Assert.assertEquals(expected, detail);
    }

    @Test
    public void checkAcquireLogger() throws NoSuchFieldException, IllegalAccessException {
        Field logField = CacheDistribution.class.getDeclaredField("log");
        logField.setAccessible(true);
        Logger logger = (Logger) logField.get(null);
        logger.setLevel(Level.DEBUG);
        CacheDistributionStrategy strategy = Mockito.mock(CacheDistributionStrategy.class);
        CacheDistribution distribution = new CacheDistribution(2, 5, 10,
            strategy);
        Mockito.when(strategy.tryAcquireBuffer(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt(),
            Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt())).thenReturn(true);
        String dn = "test-dn-random";
        try (MockedStatic<CacheLoggerContext> mock = Mockito.mockStatic(CacheLoggerContext.class)) {
            mock.when(CacheLoggerContext::getFileName).thenReturn("test-file");
            mock.when(CacheLoggerContext::getSeq).thenReturn(1);
            mock.when(CacheLoggerContext::getUuid).thenReturn("test-uuid");
            Assert.assertTrue(distribution.tryAcquireBuffer(dn));
            distribution.releaseBuffer(dn);
            mock.verify(CacheLoggerContext::getFileName, Mockito.times(2));
            mock.verify(CacheLoggerContext::getSeq, Mockito.times(2));
            mock.verify(CacheLoggerContext::getUuid, Mockito.times(2));
        }
        logger.setLevel(Level.INFO);
    }
}
