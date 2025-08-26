/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.lmax.disruptor.EventHandler;

public class LogEventHandler implements EventHandler<LogEventWrapper> {

    private final int id;

    public LogEventHandler(int id) {
        this.id = id;
    }

    @Override
    public void onEvent(LogEventWrapper event, long sequence, boolean endOfBatch) throws Exception {
        if (event.getId() == this.id){

        }
    }
}
