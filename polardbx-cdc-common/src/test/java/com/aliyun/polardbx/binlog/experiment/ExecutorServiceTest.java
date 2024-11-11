/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.experiment;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-09-01 16:56
 **/
public class ExecutorServiceTest {

    @Test
    public void testFuture() {
        AtomicLong count = new AtomicLong();
        ExecutorService executor = Executors.newCachedThreadPool();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(executor.submit(() -> {
                throw new RuntimeException();
            }));
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                count.incrementAndGet();
            }
        }

        Assert.assertEquals(10L, count.get());
    }
}
