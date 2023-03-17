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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

@Slf4j
public class TestConfig {

    private static final LoadingCache<String, Properties> loadingCache = CacheBuilder.newBuilder().build(
        new CacheLoader<String, Properties>() {
            @Override
            public Properties load(String key) throws Exception {
                return parserProperties(key);
            }
        });

    public static String getConfig(Class<?> clazz, String key) {
        String path = clazz.getClassLoader().getResource(".").getPath() + "test_config.properties";
        return loadingCache.getUnchecked(path).getProperty(key);
    }

    public static Properties parserProperties(String path) {
        Properties serverProps = new Properties();
        InputStream in = null;
        try {
            in = new FileInputStream(path);
            if (in != null) {
                serverProps.load(in);
            }
            return serverProps;
        } catch (IOException e) {
            log.error("parser the file: " + path, e);
            throw new RuntimeException(e);
        } finally {
            IOUtils.closeQuietly(in);
        }
    }
}
