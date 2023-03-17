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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.ArgumentPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicApplicationVersionConfig {

    private static ConcurrentHashMap<String, String> configMap = new ConcurrentHashMap();

    public static String getValue(String key) {
        String value = configMap.get(key);
        if (StringUtils.isEmpty(value)) {
            return DynamicApplicationConfig.getValue(key);
        }
        return value;
    }

    public static String getString(String key) {
        return getValue(key);
    }

    public static String getString(String key, String defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : value;
    }

    public static Integer getInt(String key) {
        return Integer.parseInt(getValue(key));
    }

    public static Integer getInt(String key, int defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Integer.parseInt(value);
    }

    public static Long getLong(String key) {
        return Long.parseLong(getValue(key));
    }

    public static Long getLong(String key, long defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Long.parseLong(value);
    }

    public static Boolean getBoolean(String key) {
        return Boolean.parseBoolean(getValue(key));
    }

    public static Boolean getBoolean(String key, boolean defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    public static Double getDouble(String key) {
        return Double.parseDouble(getValue(key));
    }

    public static Double getDouble(String key, double defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Double.parseDouble(value);
    }

    public static void setConfigByTso(String tso) {
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        String content = metaTemplate
            .query("select change_env_content from binlog_env_config_history where tso = ? ",
                new ArgumentPreparedStatementSetter(new Object[] {tso}),
                resultSet -> {
                    if (!resultSet.next()) {
                        return null;
                    }
                    return resultSet.getString(1);
                });
        JSONObject jsonObject = JSON.parseObject(content);
        for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
            configMap.put(entry.getKey(), (String) entry.getValue());
        }
    }

    public static void applyConfigByTso(String tso) {
        JdbcTemplate metaTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        List<String> contentList = metaTemplate
            .query(
                "select change_env_content from binlog_env_config_history where tso <= ? order by id asc",
                new ArgumentPreparedStatementSetter(new Object[] {tso}),
                resultSet -> {
                    List<String> resultList = Lists.newArrayList();
                    while (resultSet.next()) {
                        resultList.add(resultSet.getString(1));
                    }
                    return resultList;
                });
        contentList.stream().forEach(c -> {
            JSONObject jsonObject = JSON.parseObject(c);
            for (Map.Entry<String, Object> entry : jsonObject.entrySet()) {
                configMap.put(entry.getKey(), (String) entry.getValue());
            }
        });

    }
}
