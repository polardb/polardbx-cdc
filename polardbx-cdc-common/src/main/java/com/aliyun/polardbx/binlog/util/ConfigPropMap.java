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

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;

/**
 * 在spring初始化的时候无法使用DynamicApplicationConfig类获得config.properties文件中的配置
 * 所以封装该类，用于系统初始化时读取config.properties文件
 *
 * @author yudong
 * @since 2023/8/2 16:14
 **/
@Slf4j
public class ConfigPropMap {
    private static final Map<String, String> CONFIG_MAP;

    static {
        try {
            CONFIG_MAP = ConfigFileParser.parse("classpath:config.properties");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getPropertyValue(String configName) {
        return CONFIG_MAP.get(configName);
    }

}
