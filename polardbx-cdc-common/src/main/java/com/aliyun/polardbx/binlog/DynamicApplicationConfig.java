/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.enums.ClusterRole;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.util.AddressUtil;
import com.aliyun.polardbx.binlog.util.PropertyChangeListener;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 1、优先从binlog_system_config表获取数据。 <br>
 * 2、如果获取到的数据为null或者空白字符串，在从config.properties获取
 */
@Slf4j
public class DynamicApplicationConfig {

    private static final Map<String, List<PropertyChangeListener>> changeListenerMap = Maps.newHashMap();
    private static final Map<String, String> propBeforeImageMap = Maps.newHashMap();
    private static final List<String> watchPropList = Lists.newArrayList();
    private static IConfigDataProvider provider = new DbConfigDataProvider();

    private static AtomicBoolean started = new AtomicBoolean(false);

    public static String getValue(String key) {
        return provider.getValue(key);
    }

    public static void setConfigDataProvider(IConfigDataProvider provider) {
        DynamicApplicationConfig.provider = provider;
    }

    /**
     * @param value 禁止设置为null和空白字符串，cache不认
     */
    public static void setValue(String key, String value) {
        Preconditions.checkArgument(StringUtils.isNotEmpty(key), "key should not be null or empty!");
        Preconditions.checkArgument(StringUtils.isNotEmpty(value), "value should not be null or empty!");
        SystemDbConfig.upsertSystemDbConfig(key, value);
    }

    public static String getString(String key) {
        if (StringUtils.equals(key, ConfigKeys.INST_IP)) {
            String value = getValue(key);
            return StringUtils.isEmpty(value) ? AddressUtil.getHostAddress().getHostAddress() : value;
        }
        return getValue(key);
    }

    public static String getString(String key, String defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : value;
    }

    public static Integer getInt(String key) {
        return Integer.parseInt(getValue(key));
    }

    public static Integer getInt(String key, int defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Integer.parseInt(value);
    }

    public static Long getLong(String key) {
        return Long.parseLong(getValue(key));
    }

    public static Long getLong(String key, long defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Long.parseLong(value);
    }

    public static Boolean getBoolean(String key) {
        String value = getValue(key);
        if ("RANDOM".equalsIgnoreCase(value)) {
            return new Random().nextBoolean();
        } else if ("ON".equalsIgnoreCase(value)) {
            return true;
        } else if ("OFF".equalsIgnoreCase(value)) {
            return false;
        } else {
            return Boolean.parseBoolean(value);
        }
    }

    public static Boolean getBoolean(String key, boolean defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Boolean.parseBoolean(value);
    }

    public static Double getDouble(String key) {
        return Double.parseDouble(getValue(key));
    }

    public static Double getDouble(String key, double defaultValue) {
        String value = getValue(key);
        return StringUtils.isBlank(value) ? defaultValue : Double.parseDouble(value);
    }

    public static String getClusterType() {
        String clusterType = getString(ConfigKeys.CLUSTER_TYPE);
        if (StringUtils.isBlank(clusterType)) {
            clusterType = ClusterType.BINLOG.name();
        }
        return clusterType;
    }

    public static String getClusterRole() {
        String result = getString(ConfigKeys.CLUSTER_ROLE);
        if (StringUtils.isBlank(result)) {
            result = ClusterRole.master.name();
        }
        return result;
    }

    public static void addPropListener(String prop, PropertyChangeListener listener) {
        synchronized (changeListenerMap) {
            List<PropertyChangeListener> propertyChangeListenerList =
                changeListenerMap.computeIfAbsent(prop, k -> Lists.newCopyOnWriteArrayList());
            propertyChangeListenerList.add(listener);
            watchPropList.add(prop);
        }
        propBeforeImageMap.put(prop, getValue(prop));
    }

    public static void removePropListener(String prop, PropertyChangeListener listener) {
        synchronized (changeListenerMap) {
            List<PropertyChangeListener> propertyChangeListenerList =
                changeListenerMap.get(prop);
            if (!CollectionUtils.isEmpty(propertyChangeListenerList)) {
                propertyChangeListenerList.remove(listener);
            }
            if (CollectionUtils.isEmpty(propertyChangeListenerList)) {
                watchPropList.remove(prop);
            }
        }

    }

    public static void afterPropSet() {
        started.set(true);
        for (String prop : watchPropList) {
            propBeforeImageMap.put(prop, getValue(prop));
        }
        firePropInit();
    }

    public static void firePropInit() {
        synchronized (changeListenerMap) {
            for (String prop : watchPropList) {
                final String newValue = getValue(prop);
                List<PropertyChangeListener> listeners = changeListenerMap.get(prop);
                if (!CollectionUtils.isEmpty(listeners)) {
                    listeners.stream().forEach(l -> {
                        try {
                            l.onInit(prop, newValue);
                        } catch (Throwable e) {
                            log.error(
                                "execute prop change listener error [" + prop + ": (" + newValue
                                    + ")", e);
                        }
                    });
                }
            }
        }
    }

    public static void firePropChange() {
        synchronized (changeListenerMap) {
            for (Map.Entry<String, String> propEntry : propBeforeImageMap.entrySet()) {
                final String prop = propEntry.getKey();
                final String oldValue = propEntry.getValue();
                final String newValue = getValue(prop);
                if (StringUtils.equals(oldValue, newValue)) {
                    continue;
                }
                List<PropertyChangeListener> listeners = changeListenerMap.get(prop);
                if (!CollectionUtils.isEmpty(listeners)) {
                    listeners.stream().forEach(l -> {
                        try {
                            l.onPropertyChange(prop, oldValue, newValue);
                        } catch (Throwable e) {
                            log.error(
                                "execute prop change listener error [" + prop + ": (" + oldValue + "->" + newValue
                                    + ")", e);
                        }
                    });
                }
                propBeforeImageMap.put(prop, newValue);
            }
        }
    }

    public static void invalidateCache() {
        SystemDbConfig.invalidateCache();
    }
}
