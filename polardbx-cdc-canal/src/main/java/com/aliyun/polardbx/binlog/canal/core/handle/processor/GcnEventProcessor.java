/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle.processor;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.GcnLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;

public class GcnEventProcessor implements ILogEventProcessor {

    @Override
    public void handle(LogEvent event, ProcessorContext context) {
        GcnLogEvent gcnLogEvent = (GcnLogEvent) event;
        if (LogEventUtil.isHaveCommitSequence(gcnLogEvent)) {
            context.setLastTSO(gcnLogEvent.getGcn());
        }
    }
}
