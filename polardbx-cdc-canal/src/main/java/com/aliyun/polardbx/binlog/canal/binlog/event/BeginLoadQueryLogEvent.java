/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;

/**
 * Event for the first block of file to be loaded, its only difference from Append_block event is that this event
 * creates or truncates existing file before writing data.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class BeginLoadQueryLogEvent extends AppendBlockLogEvent {

    public BeginLoadQueryLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent descriptionEvent) {
        super(header, buffer, descriptionEvent);
    }
}
