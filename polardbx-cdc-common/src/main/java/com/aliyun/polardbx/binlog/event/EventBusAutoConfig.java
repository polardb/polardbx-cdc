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
