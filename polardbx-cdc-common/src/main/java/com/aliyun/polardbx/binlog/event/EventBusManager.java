/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
