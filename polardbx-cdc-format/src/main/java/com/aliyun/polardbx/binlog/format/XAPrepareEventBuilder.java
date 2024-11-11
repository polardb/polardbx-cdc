/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;

public class XAPrepareEventBuilder extends BinlogBuilder {

    private boolean onPhase;
    /**
     * -1 means that the XID is null
     */
    private int formatId;
    /**
     * value from 1 through 64
     */
    private int gtridLength;
    /**
     * value from 1 through 64
     */
    private int bqualLength;
    private byte[] data;

    public XAPrepareEventBuilder(int timestamp, int serverId, boolean onPhase, int formatId, int gtridLength,
                                 int bqualLength, byte[] data) {
        super(timestamp, BinlogEventType.XA_PREPARE_LOG_EVENT.getType(), serverId);
        this.onPhase = onPhase;
        this.formatId = formatId;
        this.gtridLength = gtridLength;
        this.bqualLength = bqualLength;
        this.data = data;
    }

    @Override
    protected void writePayload(AutoExpandBuffer outputData) throws Exception {
        numberToBytes(outputData, onPhase ? 1 : 0, INT8);
        numberToBytes(outputData, formatId, INT32);
        numberToBytes(outputData, gtridLength, INT32);
        numberToBytes(outputData, bqualLength, INT32);
        writeBytes(outputData, data);
    }

    @Override
    protected void writePostHeader(AutoExpandBuffer outputData) throws Exception {

    }
}
