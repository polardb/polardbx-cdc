/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.concurrent.TimeUnit;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class ServerConfigUtil {
    private static final CacheLoader<String, String> GLOBAL_SYSTEM_VARIABLE_LOADER = new CacheLoader<String, String>() {
        @Override
        public String load(String key) {
            JdbcTemplate template = SpringContextHolder.getObject("metaJdbcTemplate");
            return template.queryForObject("SELECT @@GLOBAL." + key, String.class);
        }
    };
    private static final LoadingCache<String, String> CACHE = CacheBuilder.newBuilder()
        .maximumSize(1024)
        .expireAfterWrite(120, TimeUnit.SECONDS)
        .build(GLOBAL_SYSTEM_VARIABLE_LOADER);

    public static Object getGlobalVar(String var) {
        return CACHE.getUnchecked(var);
    }

    public static long getGlobalNumberVar(String var) {
        return Long.valueOf(CACHE.getUnchecked(var));
    }

}
