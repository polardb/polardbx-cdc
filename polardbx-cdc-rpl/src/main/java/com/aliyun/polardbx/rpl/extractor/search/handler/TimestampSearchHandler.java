/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.search.handler;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.rpl.extractor.search.SearchContext;

public class TimestampSearchHandler extends PositionSearchHandler {
    private final long searchTimestamp;

    public TimestampSearchHandler(long searchTimestamp) {
        super(null);
        this.searchTimestamp = searchTimestamp;
    }

    @Override
    public boolean isEnd(LogEvent event, SearchContext context) {
        if (event.getHeader().getWhen() >= searchTimestamp) {
            return true;
        }

        return false;
    }

}
