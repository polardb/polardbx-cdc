/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;

/**
 * Logs xid of the transaction-to-be-committed in the 2pc protocol. Has no meaning in replication, slaves ignore it.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class XidLogEvent extends LogEvent {

    private final long xid;

    public XidLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        /* The Post-Header is empty. The Variable Data part begins immediately. */
        buffer.position(descriptionEvent.commonHeaderLen + descriptionEvent.postHeaderLen[XID_EVENT - 1]);
        xid = buffer.getLong64(); // !uint8korr
    }

    public final long getXid() {
        return xid;
    }

    @Override
    public String info() {
        return "COMMIT /* xid=" + getXid() + " */";
    }
}
