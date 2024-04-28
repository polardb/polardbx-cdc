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
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOG_WRITE_CHECK_SERVER_ID_TARGET_VALUE;

/**
 * Created by ziyang.lb
 */
@Slf4j
public class ServerConfigUtil {
    public static final String SERVER_ID = "SERVER_ID";

    private static final CacheLoader<String, String> GLOBAL_SYSTEM_VARIABLE_LOADER = new CacheLoader<String, String>() {
        @Override
        public String load(String key) {
            return get(key);
        }
    };
    private static final LoadingCache<String, String> CACHE = CacheBuilder.newBuilder()
        .maximumSize(1024)
        .expireAfterWrite(30, TimeUnit.SECONDS)
        .build(GLOBAL_SYSTEM_VARIABLE_LOADER);

    public static String getGlobalVar(String var) {
        return CACHE.getUnchecked(var);
    }

    public static long getGlobalNumberVar(String var) {
        return Long.parseLong(CACHE.getUnchecked(var));
    }

    public static long getGlobalNumberVarDirect(String var) {
        return Long.parseLong(get(var));
    }

    private static String get(String key) {
        // change 'select @@GLOBAL.xxx' to 'show global variables like ...'
        // @see https://aone.alibaba-inc.com/v2/project/860366/bug/51153559
        JdbcTemplate template = SpringContextHolder.getObject("polarxJdbcTemplate");
        List<String> list = template.query("show global variables like '" + key + "'",
            (rs, rowNum) -> rs.getString(2));

        if (list.size() == 1) {
            return list.get(0);
        } else {
            throw new PolardbxException("invalid variables result set " + list);
        }
    }

    public static Set<Long> getTargetServerIds() {
        String targetServerIds = DynamicApplicationConfig.getString(BINLOG_WRITE_CHECK_SERVER_ID_TARGET_VALUE);
        if (StringUtils.isBlank(targetServerIds)) {
            return Sets.newHashSet();
        } else {
            return Lists.newArrayList(targetServerIds.split(",")).stream().map(Long::parseLong)
                .collect(Collectors.toSet());
        }
    }
}
