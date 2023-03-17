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
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.dao.SystemConfigMapperExtend;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
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
    implements Runnable, ApplicationListener {

    private static final Logger logger = LoggerFactory.getLogger(CustomPropertySourcesPlaceholderConfigurer.class);
    private static final String SCAN_PERIOD = "scanPeriod";
    private static final String CHECKSUM = "Checksum";
    private static final AtomicBoolean start = new AtomicBoolean(false);
    private static ScheduledExecutorService scheduledExecutorService = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "scan-period");
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
//            logger.warn("props : [ " + placeholderName + " : " + value + " ] ");
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
                logger.info("init or detected db system config change");
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
            String scanPeriod = props.getProperty(SCAN_PERIOD);
            if (StringUtils.isNotBlank(scanPeriod)) {
                long period = Long.valueOf(scanPeriod);
                scheduledExecutorService.scheduleAtFixedRate(this, period, period, TimeUnit.SECONDS);
            }
        }
    }
}
