/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.testing;

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
