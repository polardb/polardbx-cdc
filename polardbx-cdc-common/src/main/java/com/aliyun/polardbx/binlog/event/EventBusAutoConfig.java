/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.event;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Configuration;

import javax.annotation.Resource;
import java.util.Map;

@Configuration
public class EventBusAutoConfig implements InitializingBean, DisposableBean {

    @Resource
    private ApplicationContext applicationContext;

    private Map<String, IEventListener> beans = null;

    @Override
    public void destroy() throws Exception {
        if (beans != null) {
            for (IEventListener eventAbstract : beans.values()) {
                EventBusManager.getInstance().unregister(eventAbstract);
            }
        }
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        beans = applicationContext.getBeansOfType(IEventListener.class);
        if (beans != null) {
            for (IEventListener eventAbstract : beans.values()) {
                EventBusManager.getInstance().register(eventAbstract);
            }
        }
    }
}
