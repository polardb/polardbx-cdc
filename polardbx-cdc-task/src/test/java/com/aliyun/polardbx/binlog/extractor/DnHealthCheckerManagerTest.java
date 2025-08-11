/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DnHealthCheckerManagerTest extends BaseTest {

    @Test
    public void testCheck() {
        DnHealthCheckerManager manager = new DnHealthCheckerManager();
        manager.scheduleCheck();
    }

    @Test
    public void testInjectError() {
        mockConfig(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_ERROR_INJECT_INTERVAL_MIN, "5");
        try (MockedStatic<RandomUtils> mockRandomUtils = Mockito.mockStatic(RandomUtils.class)) {
            mockRandomUtils.when(() -> RandomUtils.nextBoolean()).thenReturn(true);
            DnHealthCheckerManager manager = Mockito.mock(DnHealthCheckerManager.class);
            Mockito.when(manager.injectError()).thenCallRealMethod();
            Mockito.when(manager.getLastInjectTime()).thenReturn(0L);
            Assert.assertTrue(manager.injectError());
            Mockito.when(manager.getLastInjectTime()).thenReturn(System.currentTimeMillis());
            Assert.assertFalse(manager.injectError());
        }
    }

    @Test
    public void testInjectError2() {
        mockConfig(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_ERROR_INJECT_INTERVAL_MIN, "5");
        try (MockedStatic<RandomUtils> mockRandomUtils = Mockito.mockStatic(RandomUtils.class)) {
            mockRandomUtils.when(() -> RandomUtils.nextBoolean()).thenReturn(false, true);
            DnHealthCheckerManager manager = Mockito.mock(DnHealthCheckerManager.class);
            Mockito.when(manager.injectError()).thenCallRealMethod();
            Mockito.when(manager.getLastInjectTime()).thenReturn(System.currentTimeMillis());
            Assert.assertFalse(manager.injectError());
            Assert.assertFalse(manager.injectError());
        }
    }

    @Test
    public void testSchedule() throws InterruptedException {
        mockConfig(ConfigKeys.TASK_DUMP_DN_HEALTH_CHECKER_INTERVAL_SEC, "1");
        DnHealthCheckerManager manager = new DnHealthCheckerManager();
        manager.start();
        AuthenticationInfo auth = new AuthenticationInfo();
        auth.setStorageInstId("test-dn");
        auth.setAddress(new InetSocketAddress("127.0.0.1", 3306));
        AtomicInteger counter = new AtomicInteger(0);
        DnHealthChecker checker = new DnHealthChecker(auth) {
            @Override
            public void check() throws IOException {
                counter.incrementAndGet();
            }
        };
        manager.registerTask(checker);
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        Assert.assertTrue(counter.get() > 0);
        manager.stop();
        int expect = counter.get();
        Thread.sleep(TimeUnit.SECONDS.toMillis(2));
        Assert.assertEquals(expect, counter.get());
    }
}
