/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.experiment;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import org.junit.Assert;
import org.junit.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * created by ziyang.lb
 **/
public class LoadingCacheTest {

    @Test
    public void testRemovalListener() {
        AtomicLong count = new AtomicLong(0);
        LoadingCache<String, String> loadingCache = CacheBuilder
            .newBuilder()
            .maximumSize(100)
            .removalListener((RemovalListener<String, String>) notification -> {
                count.incrementAndGet();
            })
            .build(new CacheLoader<String, String>() {

                @Override
                public String load(String key) {
                    return UUID.randomUUID().toString();
                }
            });

        for (int i = 0; i < 200; i++) {
            loadingCache.put(UUID.randomUUID().toString(), UUID.randomUUID().toString());
        }

        Assert.assertEquals(100, count.get());
    }
}
