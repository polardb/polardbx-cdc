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
 * Execute_load_log_event.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class ExecuteLoadLogEvent extends LogEvent {

    /* EL = "Execute Load" */
    public static final int EL_FILE_ID_OFFSET = 0;
    private final long fileId;

    public ExecuteLoadLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        final int commonHeaderLen = descriptionEvent.commonHeaderLen;
        buffer.position(commonHeaderLen + EL_FILE_ID_OFFSET);
        fileId = buffer.getUint32(); // EL_FILE_ID_OFFSET
    }

    public final long getFileId() {
        return fileId;
    }
}
