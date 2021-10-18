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

package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.common.base.Preconditions;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * 1、优先从binlog_system_config表获取数据。 <br>
 * 2、如果获取到的数据为null或者空白字符串，在从config.properties获取
 */
@Slf4j
public class DynamicApplicationConfig {

    public static String getValue(String key) {
        return SystemDbConfig.getCachedSystemDbConfig(key);
    }

    /**
     * @param value 禁止设置为null和空白字符串，cache不认
     */
    public static void setValue(String key, String value) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(key), "key should not be null or empty!");
        Preconditions.checkArgument(StringUtils.isNotEmpty(value), "value should not be null or empty!");
        SystemDbConfig.upsertSystemDbConfig(key, value);
    }

    public static String getString(String key) {
        if (StringUtils.equals(key, ConfigKeys.INST_IP)) {
            String value = getValue(key);
            return StringUtils.isEmpty(value) ? AddressUtil.getHostAddress().getHostAddress() : value;
        }
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
}
