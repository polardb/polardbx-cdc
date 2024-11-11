/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;

/**
 * Log row deletions. The event contain several delete rows for a table. Note that each event contains only rows for one
 * table.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class DeleteRowsLogEvent extends RowsLogEvent {

    public DeleteRowsLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header, buffer, descriptionEvent);
    }

    @Override
    public String info() {
        if (getFlags(STMT_END_F) == 1) {
            return String.format("table_id: %s flags: STMT_END_F", getTableId());
        } else {
            return String.format("table_id: %s", getTableId());
        }
    }
}
