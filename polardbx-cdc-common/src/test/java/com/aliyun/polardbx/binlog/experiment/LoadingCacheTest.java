/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
