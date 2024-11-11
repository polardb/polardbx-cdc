/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;

import java.util.Random;
import java.util.concurrent.TimeUnit;

public class CnInstConfigUtil {
    private static final LoadingCache<String, String> CACHE = CacheBuilder.newBuilder()
        .maximumSize(4096)
        .expireAfterWrite(120, TimeUnit.SECONDS)
        .build(new CacheLoader<String, String>() {
            @Override
            public String load(String key) {
                return SystemDbConfig.getInstConfig(key);
            }
        });

    public static String getValue(String key) {
        return CACHE.getUnchecked(key);
    }

    public static Boolean getBoolean(String key) {
        String value = getValue(key);
        if ("RANDOM".equalsIgnoreCase(value)) {
            return new Random().nextBoolean();
        } else if ("ON".equalsIgnoreCase(value)) {
            return true;
        } else if ("OFF".equalsIgnoreCase(value)) {
            return false;
        } else {
            return Boolean.parseBoolean(value);
        }
    }
}
