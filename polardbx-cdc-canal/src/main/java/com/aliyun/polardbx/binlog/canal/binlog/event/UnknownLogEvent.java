/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.event;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;

/**
 * Unknown_log_event
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public final class UnknownLogEvent extends LogEvent {

    public UnknownLogEvent(LogHeader header) {
        super(header);
    }
}
