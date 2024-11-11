/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
