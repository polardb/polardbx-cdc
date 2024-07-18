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

