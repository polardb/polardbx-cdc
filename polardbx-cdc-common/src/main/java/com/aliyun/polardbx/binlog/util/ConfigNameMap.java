/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Map;

/**
 * 背景：config.properties中的配置名命令不规范，需要将配置名统一成a_b_c的形式
 * 因为某些配置项可能保存在metaDB中（名字为a.b.c），所以需要维护一个a_b_c到a.b.c的映射关系
 * 当使用a_b_c读不到配置时，尝试使用a.b.c读取
 *
 * @author yudong
 * @since 2023/2/20 14:41
 **/
@Slf4j
public class ConfigNameMap {
    private static final Map<String, String> CONFIG_MAP;

    static {
        try {
            CONFIG_MAP = ConfigFileParser.parse("classpath:configmap.properties");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getOldConfigName(String newConfigName) {
        return StringUtils.defaultIfBlank(CONFIG_MAP.get(newConfigName), "");
    }

    public static boolean isOldConfigName(String configName) {
        return CONFIG_MAP.containsValue(configName);
    }

    public static String getNewConfigName(String oldConfigName) {
        for (Map.Entry<String, String> entry : CONFIG_MAP.entrySet()) {
            if (entry.getValue().equals(oldConfigName)) {
                return entry.getKey();
            }
        }
        return null;
    }
}
