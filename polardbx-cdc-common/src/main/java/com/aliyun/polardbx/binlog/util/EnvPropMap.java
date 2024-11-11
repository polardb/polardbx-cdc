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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yudong
 * @since 2023/8/3 11:43
 **/
@Slf4j
public class EnvPropMap {
    private static final Map<String, String> CONFIG_MAP = new HashMap<>();
    private static final String ENV_CONFIG_FILE_PATH = "/home/admin/env/env.properties";

    // 无法复用ConfigFileParser
    static {
        try {
            Map<String, String> systemEnv = System.getenv();
            CONFIG_MAP.putAll(systemEnv);
        } catch (Exception e) {
            log.error("get system env error", e);
        }

        try {
            Path filePath = Paths.get(ENV_CONFIG_FILE_PATH);
            List<String> lines = Files.readAllLines(filePath, StandardCharsets.UTF_8);
            for (String line : lines) {
                // 注释
                if (line.startsWith("#")) {
                    continue;
                }

                String[] kv = StringUtils.split(line, "=");
                if (kv.length != 2) {
                    continue;
                }
                CONFIG_MAP.putIfAbsent(kv[0], kv[1]);
            }
        } catch (IOException e) {
            // 除了公有云，其他环境下可能没有env.properities
            // 由调用者根据getPropertyValue的返回结果进行处理
            log.info("/home/admin/env/env.properties not exist");
        }
    }

    public static String getPropertyValue(String configName) {
        String res = CONFIG_MAP.get(configName);
        if (res == null) {
            String oldConfigName = ConfigNameMap.getOldConfigName(configName);
            if (oldConfigName != null) {
                res = CONFIG_MAP.get(oldConfigName);
            }
        }
        return res;
    }

}
