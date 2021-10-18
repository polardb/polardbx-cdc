/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.SystemConfigInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.SystemConfigInfo;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.aliyun.polardbx.binlog.dao.SystemConfigInfoDynamicSqlSupport.configKey;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by ziyang.lb
 */
public class SystemDbConfig {
    private static final String UPSERT_SQL =
        "replace into `binlog_system_config`(`config_key`, `config_value`) values(?, ?)";

    private static final String UPDATE_SQL =
        "update `binlog_system_config` set `config_value`=? where config_key=?";

    private static final SystemConfigInfoMapper systemConfigInfoMapper =
        SpringContextHolder.getObject(SystemConfigInfoMapper.class);

    private static final JdbcTemplate jdbcTemplate =
        SpringContextHolder.getObject("metaJdbcTemplate");

    // 2分钟过期时间，通过upsertSystemDbConfig，updateSystemDbConfig 进程内可立即失效,value 禁止设置为空字符串，毫无意义
    private static final LoadingCache<String, String> CACHE = CacheBuilder.newBuilder()
        .maximumSize(2048)
        .expireAfterWrite(120, TimeUnit.SECONDS)
        .build(new CacheLoader<String, String>() {
            @Override
            public String load(String s) {
                try {
                    String dbValue = getSystemDbConfig(s);
                    return StringUtils.isNotBlank(dbValue) ? dbValue : SpringContextHolder.getPropertiesValue(s);
                } catch (Exception e) {
                    // 极端情况，如果访问DB出现异常，做个容错处理
                    return SpringContextHolder.getPropertiesValue(s);
                }
            }
        });

    public static String getSystemDbConfig(String sysKey) {
        List<SystemConfigInfo> list = systemConfigInfoMapper.select(s -> s.where(configKey, isEqualTo(sysKey)));
        return list.isEmpty() ? "" : list.get(0).getConfigValue();
    }

    public static String getCachedSystemDbConfig(String sysKey) {
        return CACHE.getUnchecked(sysKey);
    }

    public static void upsertSystemDbConfig(String key, String value) {
        jdbcTemplate.update(UPSERT_SQL, key, value);
        CACHE.invalidate(key);
    }

    public static void updateSystemDbConfig(String key, String value) {
        jdbcTemplate.update(UPDATE_SQL, value, key);
        CACHE.invalidate(key);
    }
}
