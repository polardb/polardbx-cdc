/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.format.utils.AutoExpandBuffer;
import com.aliyun.polardbx.binlog.format.utils.BinlogEventType;

public class XidEventBuilder extends BinlogBuilder {

    private long xid;

    public XidEventBuilder(int timestamp, int serverId, long xid) {
        super(timestamp, BinlogEventType.XID_EVENT.getType(), serverId);
        this.xid = xid;
    }

    @Override
    protected void writePayload(AutoExpandBuffer outputData) throws Exception {
        numberToBytes(outputData, xid, 8);
    }

    @Override
    protected void writePostHeader(AutoExpandBuffer outputData) throws Exception {

    }
}
