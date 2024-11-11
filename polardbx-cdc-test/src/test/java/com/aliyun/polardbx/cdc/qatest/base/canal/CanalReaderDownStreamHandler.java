/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.base.canal;

import com.alibaba.otter.canal.sink.AbstractCanalEventDownStreamHandler;
import com.alibaba.otter.canal.store.model.Event;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public class CanalReaderDownStreamHandler extends AbstractCanalEventDownStreamHandler<List<Event>> {

    @Override
    public List<Event> before(List<Event> events) {
        if (log.isDebugEnabled()) {
            events.forEach(e -> log.debug(
                "Event Dump in Handler : EntryType is {}, EventType is {},SchemaName is {},TableName is {}.",
                e.getEntry().getEntryType(),
                e.getEntry().getHeader().getEventType(),
                e.getEntry().getHeader().getSchemaName(),
                e.getEntry().getHeader().getTableName()));
        }
        return super.before(events);
    }
}

