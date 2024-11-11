/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.dao.SystemConfigMapperExtend;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class CustomPropertySourcesPlaceholderConfigurer extends PropertyPlaceholderConfigurer
    implements Runnable, ApplicationListener, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(CustomPropertySourcesPlaceholderConfigurer.class);
    private static final String CONFIG_SCAN_PERIOD_SECOND = "config_scan_period_second";
    private static final String CHECKSUM = "Checksum";
    private final AtomicBoolean start = new AtomicBoolean(false);
    private final ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "dynamic-config-scanner");
            t.setDaemon(true);
            return t;
        });
    private final Map<String, Long> fileModifedMap = Maps.newConcurrentMap();
    private Long lastCheckSum;
    private ResourceLoader resourceLoader;
    private Properties props;

    @Override
    protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess, Properties props)
        throws BeansException {

        resourceLoader = new DefaultResourceLoader();
        this.props = props;
        Resource resource = resourceLoader.getResource("classpath:config.properties");
        Resource envResource = resourceLoader.getResource("classpath:env.properties");
        try {
            reload(resource);
            reload(envResource);
            recordModify(resource);
            recordModify(envResource);
        } catch (Throwable e) {
            logger.error("reload config properties failed!", e);
            throw new PolardbxException(e);
        }

        super.processProperties(beanFactoryToProcess, props);
        printProps();
    }

    private void printProps() {
        Enumeration<String> enumeration = (Enumeration<String>) props.propertyNames();
        while (enumeration.hasMoreElements()) {
            String placeholderName = enumeration.nextElement();
            String value = props.getProperty(placeholderName);
        }
    }

    private void reload(Resource resource) throws IOException {
        if (!resource.isReadable()) {
            return;
        }
        props.load(resource.getInputStream());
        Enumeration<String> enumeration = (Enumeration<String>) props.propertyNames();
        while (enumeration.hasMoreElements()) {
            String placeholderName = enumeration.nextElement();
            String propVal = System.getProperty(placeholderName);
            if (propVal == null) {
                // Fall back to searching the system environment.
                propVal = System.getenv(placeholderName);
            }
            if (propVal != null) {
                props.setProperty(placeholderName, propVal);
            }

        }
    }

    boolean isModify(Resource resource) throws IOException {
        if (!resource.isReadable()) {
            return false;
        }
        Long lastModified = fileModifedMap.get(resource.getFilename());
        return !Objects.equals(lastModified, resource.lastModified());
    }

    void recordModify(Resource resource) throws IOException {
        if (resource.isReadable()) {
            fileModifedMap.put(resource.getFilename(), resource.lastModified());
        }
    }

    @Override
    public void run() {
        Resource resource = resourceLoader.getResource("classpath:config.properties");
        Resource envResource = resourceLoader.getResource("classpath:env.properties");
        try {

            if (isModify(resource) || isModify(envResource)) {
                logger.info("detected config properties change");
                reload(resource);
                reload(envResource);
                DynamicApplicationConfig.invalidateCache();
                DynamicApplicationConfig.firePropChange();
                recordModify(resource);
                recordModify(envResource);
                printProps();
                return;
            }

            if (isSystemDbChange()) {
                DynamicApplicationConfig.invalidateCache();
                DynamicApplicationConfig.firePropChange();
            }
        } catch (Throwable e) {
            logger.error("scan config change error!", e);
        }

    }

    private boolean isSystemDbChange() {
        SystemConfigMapperExtend systemConfigMapperExtend = SpringContextHolder.getObject(
            SystemConfigMapperExtend.class);
        Map<String, Object> checkSumResultMap = systemConfigMapperExtend.checksumTable();
        Long checkSum = (Long) checkSumResultMap.get(CHECKSUM);
        if (!Objects.equals(lastCheckSum, checkSum)) {
            lastCheckSum = checkSum;
            return true;
        }
        return false;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (start.compareAndSet(false, true)) {
            DynamicApplicationConfig.afterPropSet();
            String scanPeriod = props.getProperty(CONFIG_SCAN_PERIOD_SECOND);
            if (StringUtils.isNotBlank(scanPeriod)) {
                long period = Long.parseLong(scanPeriod);
                scheduledExecutorService.scheduleAtFixedRate(this, period, period, TimeUnit.SECONDS);
            }
            logger.info("dynamic config scanner started!");
        }
    }

    @Override
    public void destroy() throws Exception {
        scheduledExecutorService.shutdownNow();
        logger.info("dynamic config scanner stopped!");
    }
}
