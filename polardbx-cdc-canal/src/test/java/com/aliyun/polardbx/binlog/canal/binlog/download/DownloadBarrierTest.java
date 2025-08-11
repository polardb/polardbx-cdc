/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.ThreadPoolExecutor;

public class DownloadBarrierTest extends BaseTest {
    @Test
    public void testBarrier() throws InterruptedException {
        final String storageInstance = "ss-1";
        ThreadPoolExecutor executor = Mockito.mock(ThreadPoolExecutor.class);
        Mockito.when(executor.getCorePoolSize()).thenReturn(10);
        DownloadBarrier barrier = new DownloadBarrier(DownloadBarrierTest.class.getResource("/").getPath(), executor);
        for (int i = 0; i < 10; i++) {
            barrier.beginDownload(storageInstance);
        }
        Assert.assertFalse(barrier.waitDownload(storageInstance));
        barrier.endDownload(storageInstance);
        Assert.assertTrue(barrier.waitDownload(storageInstance));
        Mockito.when(executor.getCorePoolSize()).thenReturn(1);
        Assert.assertFalse(barrier.waitDownload(storageInstance));
    }
}
