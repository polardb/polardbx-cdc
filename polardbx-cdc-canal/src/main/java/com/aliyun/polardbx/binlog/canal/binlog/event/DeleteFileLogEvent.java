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
 * Delete_file_log_event.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class DeleteFileLogEvent extends LogEvent {

    /* DF = "Delete File" */
    public static final int DF_FILE_ID_OFFSET = 0;
    private final long fileId;

    public DeleteFileLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        final int commonHeaderLen = descriptionEvent.commonHeaderLen;
        buffer.position(commonHeaderLen + DF_FILE_ID_OFFSET);
        fileId = buffer.getUint32(); // DF_FILE_ID_OFFSET
    }

    public final long getFileId() {
        return fileId;
    }
}
