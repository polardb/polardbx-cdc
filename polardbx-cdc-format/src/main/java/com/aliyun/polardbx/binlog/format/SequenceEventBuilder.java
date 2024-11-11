/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;

public class SequenceEventBuilder extends BinlogBuilder {

    private int type;
    private long tso;

    public SequenceEventBuilder(int timestamp, int type, int serverId, long tso) {
        super(timestamp, 80, serverId);
        this.type = type; // commit ts
        this.tso = tso;
    }

    @Override
    protected void writePayload(AutoExpandBuffer outputData) throws Exception {
        numberToBytes(outputData, type, INT8);
        numberToBytes(outputData, tso, INT64);
    }

    @Override
    protected void writePostHeader(AutoExpandBuffer outputData) throws Exception {

    }
}
