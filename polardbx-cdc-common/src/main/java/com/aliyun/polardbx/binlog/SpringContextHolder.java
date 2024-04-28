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

import com.aliyun.polardbx.binlog.error.ConfigKeyNotExistException;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.stereotype.Service;
import org.springframework.util.StringValueResolver;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by ziyang.lb
 */
@Service
public class SpringContextHolder implements ApplicationContextAware, EmbeddedValueResolverAware {

    private static volatile ApplicationContext applicationContext;
    private static StringValueResolver stringValueResolver;
    private static ConcurrentHashMap<Object, Object> objectMap = new ConcurrentHashMap<>();

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringContextHolder.applicationContext = applicationContext;
    }

    @Override
    public void setEmbeddedValueResolver(StringValueResolver stringValueResolver) {
        SpringContextHolder.stringValueResolver = stringValueResolver;
    }

    @SuppressWarnings("unchecked")
    public static <T> T getObject(String name) {
        if (objectMap.containsKey(name)) {
            return (T) objectMap.get(name);
        } else {
            return (T) applicationContext.getBean(name);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T getObject(Class<? extends T> clazz) {
        if (objectMap.containsKey(clazz)) {
            return (T) objectMap.get(clazz);
        } else {
            return (T) applicationContext.getBean(clazz);
        }
    }

    public static String getPropertiesValue(String name) {
        try {
            return stringValueResolver.resolveStringValue("${" + name + "}");
        } catch (IllegalArgumentException e) {
            throw new ConfigKeyNotExistException("Get property error for " + name, e);
        }
    }

    public static boolean isInitialize() {
        return applicationContext != null;
    }
}
