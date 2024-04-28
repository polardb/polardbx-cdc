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
