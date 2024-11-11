/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author yudong
 * @since 2023/8/3 11:29
 **/
@Slf4j
public class ConfigFileParser {

    public static Map<String, String> parse(String resourceLocation) throws IOException {
        Map<String, String> res = new HashMap<>();
        ResourceLoader resourceLoader = new DefaultResourceLoader();
        Resource resource = resourceLoader.getResource(resourceLocation);
        List<String> lines = FileUtils.readLines(resource.getFile(), StandardCharsets.UTF_8);
        for (String line : lines) {
            // 注释
            if (line.startsWith("#")) {
                continue;
            }

            String[] kv = StringUtils.split(line, "=");
            if (kv.length != 2) {
                continue;
            }
            res.put(kv[0], kv[1]);
        }
        return res;
    }

}
