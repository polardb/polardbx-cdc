/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle.processor;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;

public class XARollbackEventProcessor implements ILogEventProcessor<QueryLogEvent> {
    @Override
    public void handle(QueryLogEvent event, ProcessorContext context) {
        String xid = LogEventUtil.getXid(event);
        if (xid != null && LogEventUtil.isValidXid(xid)) {
            context.completeTranPos(xid);
        }
    }
}
