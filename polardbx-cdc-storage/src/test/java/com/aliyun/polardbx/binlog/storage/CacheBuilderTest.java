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
package com.aliyun.polardbx.binlog.storage;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * created by ziyang.lb
 **/
@RunWith(Parameterized.class)
public class CacheBuilderTest {

    @Parameterized.Parameters
    public static List<String[]> getTestParameters() {
        //37610,14537,10658,6101
        //return Arrays.asList(new String[][] {{"Builder_Txn"}, {"Map_Txn"}, {"Builder_Long"}, {"Map_Long"}});
        return Arrays.asList(new String[][] {{"Map_Txn"}, {"Builder_Txn"}, {"Map_Long"}, {"Builder_Long"}});
    }

    private final String type;

    public CacheBuilderTest(String type) {
        this.type = type;
    }

    @Test
    public void multiThreadTest() {
        LoadingCache<TxnKey, TxnBuffer> txnCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<TxnKey, TxnBuffer>() {

                @Override
                public TxnBuffer load(TxnKey key) throws Exception {
                    return new TxnBuffer(key, null);
                }
            });
        LoadingCache<TxnKey, AtomicLong> longCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<TxnKey, AtomicLong>() {

                @Override
                public AtomicLong load(TxnKey key) throws Exception {
                    return new AtomicLong(0);
                }
            });
        ConcurrentHashMap<TxnKey, TxnBuffer> txnMap = new ConcurrentHashMap<>();
        ConcurrentHashMap<TxnKey, AtomicLong> longMap = new ConcurrentHashMap<>();

        // build seed
        int count = 10000000;
        List<TxnKey> txnKeys = new ArrayList<>();
        long seed = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            txnKeys.add(new TxnKey(String.valueOf(seed++), "111"));
        }

        // get
        long start = System.currentTimeMillis();
        if ("Builder_Txn".equals(type)) {
            txnKeys.forEach(k -> txnCache.getUnchecked(k));
        } else if ("Map_Txn".equals(type)) {
            txnKeys.forEach(k -> txnMap.computeIfAbsent(k, key -> new TxnBuffer(key, null)));
        } else if ("Builder_Long".equals(type)) {
            txnKeys.forEach(k -> longCache.getUnchecked(k));
        } else if ("Map_Long".equals(type)) {
            txnKeys.forEach(k -> longMap.computeIfAbsent(k, key -> new AtomicLong(0L)));
        }

        long end = System.currentTimeMillis();

        //print
        System.out.println("type:" + type);
        System.out.println("cost time is:" + (end - start));
        System.out.println("tps is:" + ((double) count / (end - start)));
        System.out.println("cache size:" + txnCache.size());
        System.out.println();
    }

    @Test
    public void singleThreadTest() {
        LoadingCache<TxnKey, TxnBuffer> txnCache = CacheBuilder.newBuilder()
            .build(new CacheLoader<TxnKey, TxnBuffer>() {

                @Override
                public TxnBuffer load(TxnKey key) throws Exception {
                    return new TxnBuffer(key, null);
                }
            });

        int count = 4000000;
        List<TxnKey> txnKeys = new ArrayList<>();
        long seed = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            txnKeys.add(new TxnKey(String.valueOf(seed++), "111"));
        }

        HashMap<TxnKey, TxnBuffer> map = new HashMap<>();
        txnKeys.forEach(k -> txnCache.getUnchecked(k));
        long start = System.currentTimeMillis();
        txnKeys.forEach(k -> txnCache.invalidate(k));
        long end = System.currentTimeMillis();
        System.out.println("cost time is:" + (end - start));
        System.out.println("tps is:" + ((double) count / (end - start)));
        System.out.println("cache size:" + txnCache.size());
    }
}
