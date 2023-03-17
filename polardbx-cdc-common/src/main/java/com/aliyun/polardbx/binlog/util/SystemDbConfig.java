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

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.InstConfigDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.InstConfigMapper;
import com.aliyun.polardbx.binlog.dao.SystemConfigInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.InstConfig;
import com.aliyun.polardbx.binlog.domain.po.SystemConfigInfo;
import com.aliyun.polardbx.binlog.error.ConfigKeyNotExistException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.UncheckedExecutionException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.aliyun.polardbx.binlog.dao.SystemConfigInfoDynamicSqlSupport.configKey;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

/**
 * Created by ziyang.lb
 */
public class SystemDbConfig {
    private static final Logger logger = LoggerFactory.getLogger(SystemDbConfig.class);

    private static final String UPSERT_SQL =
        "replace into `binlog_system_config`(`config_key`, `config_value`) values(?, ?)";
    private static final String UPDATE_SQL =
        "update `binlog_system_config` set `config_value`=? where config_key=?";

    private static final SystemConfigInfoMapper SYSTEM_CONFIG_INFO_MAPPER =
        SpringContextHolder.getObject(SystemConfigInfoMapper.class);
    private static final InstConfigMapper INST_CONFIG_MAPPER = SpringContextHolder.getObject(InstConfigMapper.class);
    private static final JdbcTemplate JDBC_TEMPLATE = SpringContextHolder.getObject("metaJdbcTemplate");

    private static final LoadingCache<String, String> CACHE = CacheBuilder.newBuilder()
        .maximumSize(4096)
        .expireAfterWrite(120, TimeUnit.SECONDS)
        .build(new ConfigCacheLoader());

    @ParametersAreNonnullByDefault
    private static class ConfigCacheLoader extends CacheLoader<String, String> {
        /**
         * 读取顺序:
         * --------- --------------where -------------- | ------------ key ---------
         * 1. binlog_system_config                             cluster_id:config_name
         * 2. binlog_system_config                             config_name
         * 3. 环境变量->env.properties->config.properties       config_name
         * 4. inst_config                                      config_name
         */
        @Override
        public String load(String configName) throws Exception {
            String res;

            try {
                // step1. read from binlog_system_config by cluster_id:config_name
                String clusterId = SpringContextHolder.getPropertiesValue(ConfigKeys.CLUSTER_ID);
                String clusterKey = clusterId + ":" + configName;
                res = getSystemDbConfig(clusterKey);
                if (StringUtils.isNotBlank(res)) {
                    return res;
                }
                String oldConfigName = ConfigMapUtil.getOldConfigName(configName);
                if (StringUtils.isNotBlank(oldConfigName)) {
                    clusterKey = clusterId + ":" + oldConfigName;
                    res = getSystemDbConfig(clusterKey);
                    if (StringUtils.isNotBlank(res)) {
                        return res;
                    }
                }

                // step2. read from binlog_system_config by config_name
                res = getConfigValue(configName, SystemDbConfig::getSystemDbConfig);
                if (StringUtils.isNotBlank(res)) {
                    return res;
                }

                // step3. read from SprintContext
                res = SpringContextHolder.getPropertiesValue(configName);
                if (StringUtils.isNotBlank(res)) {
                    return res;
                }

                // step4. read from inst_config
                res = getConfigValue(configName, SystemDbConfig::getInstConfig);
                return res;
            } catch (Exception e) {
                logger.error("read config value error, will try to read from Spring", e);
                // 极端情况，如果访问DB出现异常，做个容错处理
                return getConfigValue(configName, SpringContextHolder::getPropertiesValue);
            }
        }

        private String getConfigValue(String configName, Function<String, String> func) {
            String res;
            res = func.apply(configName);
            if (StringUtils.isNotBlank(res)) {
                return res;
            }
            String oldConfigName = ConfigMapUtil.getOldConfigName(configName);
            if (StringUtils.isNotBlank(oldConfigName)) {
                res = func.apply(oldConfigName);
            }
            return res;
        }
    }

    public static String getSystemDbConfig(String sysKey) {
        List<SystemConfigInfo> list = SYSTEM_CONFIG_INFO_MAPPER.select(s -> s.where(configKey, isEqualTo(sysKey)));
        return list.isEmpty() ? "" : list.get(0).getConfigValue();
    }

    public static String getInstConfig(String sysKey) {
        List<InstConfig> list =
            INST_CONFIG_MAPPER.select(s -> s.where(InstConfigDynamicSqlSupport.paramKey, isEqualTo(sysKey)));
        return list.isEmpty() ? "" : list.get(0).getParamVal();
    }

    public static String getCachedSystemDbConfig(String sysKey) {
        try {
            return CACHE.getUnchecked(sysKey);
        } catch (UncheckedExecutionException e) {
            if (e.getCause() instanceof ConfigKeyNotExistException) {
                throw (ConfigKeyNotExistException) e.getCause();
            }
            throw e;
        }
    }

    public static void invalidateCache() {
        CACHE.invalidateAll();
    }

    public static void upsertSystemDbConfig(String key, String value) {
        JDBC_TEMPLATE.update(UPSERT_SQL, key, value);
        CACHE.invalidate(key);
    }

    public static void updateSystemDbConfig(String key, String value) {
        JDBC_TEMPLATE.update(UPDATE_SQL, value, key);
        CACHE.invalidate(key);
    }
}
