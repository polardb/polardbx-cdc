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
}
