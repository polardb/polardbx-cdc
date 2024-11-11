/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;

public class RotateEventBuilder extends BinlogBuilder {
    private final String filename;
    private final long position;

    public RotateEventBuilder(int timestamp, long serverId, String filename, long position) {
        super(timestamp, LogEvent.ROTATE_EVENT, serverId);
        this.filename = filename;
        this.position = position;
    }

    @Override
    protected void writePayload(AutoExpandBuffer outputData) throws Exception {
        numberToBytes(outputData, position, INT64);
        writeFixString(outputData, filename, 50, "utf8");
    }

    @Override
    protected void writePostHeader(AutoExpandBuffer outputData) throws Exception {

    }
}
