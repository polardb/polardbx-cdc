/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;

public class RowsQueryEventBuilder extends BinlogBuilder {

    private String text;

    public RowsQueryEventBuilder(int timestamp, int serverId, String text) {
        super(timestamp, BinlogEventType.ROWS_QUERY_LOG_EVENT.getType(), serverId);
        this.text = text;
    }

    @Override
    protected void writePayload(AutoExpandBuffer outputData) throws Exception {
        byte[] data = text.getBytes(ISO_8859_1);
        numberToBytes(outputData, data.length, INT8);
        writeBytes(outputData, data);
    }

    @Override
    protected void writePostHeader(AutoExpandBuffer outputData) throws Exception {

    }
}
