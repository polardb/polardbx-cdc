/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.testing;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.IConfigDataProvider;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BaseTest {

    protected ConfigProvider configProvider;
    protected static final AtomicBoolean SPRING_BOOTED = new AtomicBoolean();

    public BaseTest() {
        init();
    }

    private void init() {
        bootSpring();
        initConfig();
    }

    public void before() {

    }

    public void setConfig(String key, String value) {
        configProvider.setValue(key, value);
    }

    private void bootSpring() {
        if (SPRING_BOOTED.compareAndSet(false, true)) {
            preBootSpring();
            SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap(getStringConfigFile());
            appContextBootStrap.boot();
            log.info("spring context is started!");
        }
    }

    protected void preBootSpring() {

    }

    protected String getStringConfigFile() {
        return "testing-conf/spring-test.xml";
    }

    protected DataSource getGmsDataSource() {
        return SpringContextHolder.getObject("metaDataSource");
    }

    private void initConfig() {
        configProvider = new ConfigProvider();
    }

    public static class ConfigProvider implements IConfigDataProvider {
        private static final ConcurrentHashMap<String, String> CONFIG_MAP = new ConcurrentHashMap<>();

        @Override
        public String getValue(String key) {
            return DynamicApplicationConfig.getValue(key);
        }

        public void setValue(String key, String value) {
            DynamicApplicationConfig.setValue(key, value);
        }
    }
}
