/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.testing;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.h2.H2Util;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.net.URLStreamHandlerFactory;
import java.sql.Connection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getValue;
import static com.aliyun.polardbx.binlog.testing.h2.H2Util.executeUpdate;
import static com.aliyun.polardbx.binlog.util.CommonUtils.escape;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class BaseTest {

    protected MockedStatic<DynamicApplicationConfig> mockedAppConfig;
    protected boolean autoMock = true;
    private static volatile SpringContextBootStrap springContextBootStrap;
    private static final Map<String, URLConnection> urlConnectionMap = new ConcurrentHashMap<>();
    private static volatile URLStreamHandlerFactory urlStreamHandlerFactory;
    private static final ConcurrentHashMap<String, Object> springOriginObjHolder = new ConcurrentHashMap<>();
    private static final Object NULL_OBJECT = new Object();

    public BaseTest() {
        initSpringContext();
    }

    @Before
    public void baseBefore() {
        if (autoMock) {
            mockedAppConfig = mockStatic(DynamicApplicationConfig.class, CALLS_REAL_METHODS);
        }
        resetGmsTables();
    }

    @After
    public final void baseAfter() {
        if (autoMock) {
            mockedAppConfig.close();
        }
        for (Map.Entry<String, Object> entry : springOriginObjHolder.entrySet()) {
            unregisterSpringObject(entry.getKey(), entry.getValue());
        }
        springOriginObjHolder.clear();
    }

    public <T> void registerSpringObject(String name, T object) {
        try {
            if (springOriginObjHolder.containsKey(name)) {
                throw new PolardbxException("spring object already registered ! name : " + name);
            }

            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) springContextBootStrap
                .getApplicationContext().getAutowireCapableBeanFactory();

            try {
                Object target = beanFactory.getBean(name);
                springOriginObjHolder.put(name, target);
            } catch (NoSuchBeanDefinitionException ignored) {
                springOriginObjHolder.put(name, NULL_OBJECT);
            }

            beanFactory.destroySingleton(name);
            beanFactory.registerSingleton(name, object);
        } catch (Exception e) {
            throw new PolardbxException("register spring object failed ! name : " + name, e);
        }
    }

    public <T> void unregisterSpringObject(String name, Object object) {
        try {
            DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) springContextBootStrap
                .getApplicationContext().getAutowireCapableBeanFactory();

            beanFactory.destroySingleton(name);
            if (object != NULL_OBJECT) {
                beanFactory.registerSingleton(name, object);
            }
        } catch (Exception e) {
            log.error("Error unregistering spring object with name: {}", name, e);
            throw new PolardbxException("unregister spring object failed ! name : " + name, e);
        }
    }

    public void mockUrlConnection(String url, URLConnection urlConnection) {
        if (urlStreamHandlerFactory == null) {
            synchronized (BaseTest.class) {
                if (urlStreamHandlerFactory == null) {
                    urlStreamHandlerFactory = Mockito.mock(URLStreamHandlerFactory.class);
                    when(urlStreamHandlerFactory.createURLStreamHandler(anyString()))
                        .thenReturn(new URLStreamHandler() {
                            @Override
                            protected URLConnection openConnection(URL u) throws IOException {
                                return urlConnectionMap.get(u.toString());
                            }
                        });
                    URL.setURLStreamHandlerFactory(urlStreamHandlerFactory);
                }
            }
        }
        urlConnectionMap.put(url, urlConnection);
    }

    private void initSpringContext() {
        if (springContextBootStrap == null) {
            synchronized (BaseTest.class) {
                if (springContextBootStrap == null) {
                    SpringContextBootStrap tmp = new SpringContextBootStrap("testing-conf/spring-test.xml");
                    tmp.boot();
                    initGmsInfo();
                    springContextBootStrap = tmp;
                }
            }
        }
    }

    @SneakyThrows
    protected void initGmsInfo() {
        long start = System.currentTimeMillis();
        try (Connection connection = getGmsDataSource().getConnection()) {
            Resource resource = new DefaultResourceLoader().getResource("classpath:testing-conf/gms_tables.sql");
            H2Util.executeBatchSql(connection, resource.getFile());

            Resource resource2 = new DefaultResourceLoader().getResource(
                "classpath:testing-conf/gms_additional.sql");
            H2Util.executeBatchSql(connection, resource2.getFile());
        }
        log.warn("successfully init gms tables, cost time {} (ms)", System.currentTimeMillis() - start);
    }

    protected DataSource getGmsDataSource() {
        return SpringContextHolder.getObject("metaDataSource");
    }

    @SneakyThrows
    public void resetGmsTables() {
        if (truncateGmsTableAtEachBefore()) {
            truncateGmsTables();
        }
    }

    protected void truncateGmsTables() throws Exception {
        long start = System.currentTimeMillis();
        try (Connection connection = getGmsDataSource().getConnection()) {
            List<String> tables = H2Util.showTables(connection, null);
            tables.forEach(
                t -> executeUpdate(connection, String.format("truncate table `%s`", escape(t))));
        }
        log.warn("successfully truncate gms tables , cost time {} (ms)", System.currentTimeMillis() - start);
    }

    protected boolean truncateGmsTableAtEachBefore() {
        return true;
    }

    protected void setConfig(String key, String value) {
        DynamicApplicationConfig.setValue(key, value);
    }

    protected void mockConfig(String key, String value) {
        if (autoMock) {
            mockedAppConfig.when(() -> getValue(key)).thenReturn(value);
            Assert.assertEquals(value, DynamicApplicationConfig.getString(key));
        } else {
            throw new RuntimeException("not support mock for app config!");
        }
    }

}
