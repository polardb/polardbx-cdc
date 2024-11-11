/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.event.IEventListener;
import com.aliyun.polardbx.binlog.event.source.LatestFileCursorChangeEvent;
import com.aliyun.polardbx.binlog.remote.io.IFileCursorProvider;
import com.google.common.collect.Maps;
import com.google.common.eventbus.Subscribe;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class LogFileCursorProvider implements IFileCursorProvider, IEventListener<LatestFileCursorChangeEvent> {

    private final Map<String, BinlogCursor> cursorMap = Maps.newConcurrentMap();

    public LogFileCursorProvider() {
    }

    @Override
    @Subscribe
    public void onEvent(LatestFileCursorChangeEvent event) {
        BinlogCursor cursor = event.getCursor();
        cursorMap.put(cursor.getStream(), cursor);
    }

    @Override
    public BinlogCursor getCursor(String stream) {
        return cursorMap.get(stream);
    }
}
