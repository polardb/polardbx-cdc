/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.search.handler;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.rpl.extractor.search.SearchContext;

public interface ISearchHandler {
    boolean isEnd(LogEvent event, SearchContext context);

    boolean accept(LogEvent event, SearchContext context);

    void handle(LogEvent event, SearchContext context);
}
