/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile.parallel;

import com.lmax.disruptor.EventFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * created by ziyang.lb
 **/
@Slf4j
public class EventDataFactory implements EventFactory<EventData> {

    private final int eventDataBufferSize;

    public EventDataFactory(int eventDataBufferSize) {
        this.eventDataBufferSize = eventDataBufferSize;
    }

    @Override
    public EventData newInstance() {
        return new EventData(eventDataBufferSize);
    }
}
