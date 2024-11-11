/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * created by ziyang.lb
 **/
@RunWith(Parameterized.class)
@Ignore
public class CacheBuilderTest extends BaseTest {

    @Parameterized.Parameters
    public static List<String[]> getTestParameters() {
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
            txnKeys.add(new TxnKey(seed++, "111"));
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
            txnKeys.add(new TxnKey(seed++, "111"));
        }

        txnKeys.forEach(txnCache::getUnchecked);
        long start = System.currentTimeMillis();
        txnKeys.forEach(txnCache::invalidate);
        long end = System.currentTimeMillis();
        System.out.println("cost time is:" + (end - start));
        System.out.println("tps is:" + ((double) count / (end - start)));
        System.out.println("cache size:" + txnCache.size());
    }
}
