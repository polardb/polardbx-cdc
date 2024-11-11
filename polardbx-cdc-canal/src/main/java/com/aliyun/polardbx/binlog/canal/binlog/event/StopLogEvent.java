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
 * Stop_log_event. The Post-Header and Body for this event type are empty; it only has the Common-Header.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class StopLogEvent extends LogEvent {

    public StopLogEvent(LogHeader header, LogBuffer buffer, FormatDescriptionLogEvent description_event) {
        super(header);
    }
}
