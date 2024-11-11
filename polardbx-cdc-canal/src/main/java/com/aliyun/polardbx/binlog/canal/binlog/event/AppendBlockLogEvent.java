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
 * Append_block_log_event.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class AppendBlockLogEvent extends LogEvent {

    /* AB = "Append Block" */
    public static final int AB_FILE_ID_OFFSET = 0;
    private final LogBuffer blockBuf;
    private final int blockLen;
    private final long fileId;

    public AppendBlockLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header);

        final int commonHeaderLen = descriptionEvent.commonHeaderLen;
        final int postHeaderLen = descriptionEvent.postHeaderLen[header.type - 1];
        final int totalHeaderLen = commonHeaderLen + postHeaderLen;

        buffer.position(commonHeaderLen + AB_FILE_ID_OFFSET);
        fileId = buffer.getUint32();

        buffer.position(postHeaderLen);
        blockLen = buffer.limit() - totalHeaderLen;
        blockBuf = buffer.duplicate(blockLen);
    }

    public final long getFileId() {
        return fileId;
    }

    public final LogBuffer getBuffer() {
        return blockBuf;
    }

    public final byte[] getData() {
        return blockBuf.getData();
    }
}
