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

import com.google.common.eventbus.EventBus;

public class EventBusManager {
    private static final EventBusManager instance = new EventBusManager();

    private EventBus eventBus = new EventBus();

    public static EventBusManager getInstance() {
        return instance;
    }

    public void post(BaseEvent event) {
        eventBus.post(event);
    }

    public void register(IEventListener listener) {
        eventBus.register(listener);
    }

    public void unregister(IEventListener listener) {
        eventBus.unregister(listener);
    }
}
