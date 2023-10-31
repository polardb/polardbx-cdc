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
