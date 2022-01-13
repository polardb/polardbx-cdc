/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.error.ConfigKeyNotExistException;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.stereotype.Service;
import org.springframework.util.StringValueResolver;

import java.util.Map;

/**
 * Created by ziyang.lb
 */
@Service
public class SpringContextHolder implements ApplicationContextAware, EmbeddedValueResolverAware {

    private static volatile ApplicationContext applicationContext;
    private static StringValueResolver stringValueResolver;

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
        return (T) applicationContext.getBean(name);
    }

    public static <T> T getObject(Class<? extends T> clazz) {
        return (T) applicationContext.getBean(clazz);
    }

    public static <T> Map<String, T> getBeansOfType(Class<T> clazz) {
        return applicationContext.getBeansOfType(clazz);
    }

    public static String getPropertiesValue(String name) {
        try {
            return stringValueResolver.resolveStringValue("${" + name + "}");
        } catch (IllegalArgumentException e) {
            throw new ConfigKeyNotExistException("Get property error for " + name, e);
        }
    }
}
