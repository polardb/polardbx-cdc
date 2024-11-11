/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle.processor;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XaPrepareLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;

public class XAPrepareEventProcessor implements ILogEventProcessor<XaPrepareLogEvent> {
    @Override
    public void handle(XaPrepareLogEvent event, ProcessorContext context) {
        if (event.isOnePhase()) {
            TranPosition currentTran = context.getCurrentTran();
            if (currentTran == null) {
                return;
            }
            currentTran.complete(buildPosition(event, context));
            context.completeTranPos(currentTran.getXid());
        }
        context.setCurrentTran(null);
    }

    private BinlogPosition buildPosition(LogEvent event, ProcessorContext context) {
        return new BinlogPosition(context.getLogPosition().getFileName(), event.getLogPos() - event.getEventLen(),
            event.getServerId(), event.getWhen());
    }
}
