/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle.processor;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;

public class SequenceEventProcessor implements ILogEventProcessor {

    @Override
    public void handle(LogEvent event, ProcessorContext context) {
        SequenceLogEvent sequenceLogEvent = (SequenceLogEvent) event;
        if (sequenceLogEvent.isCommitSequence()) {
            context.setLastTSO(sequenceLogEvent.getSequenceNum());
        }

    }
}
