/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle.processor;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XACommitEventProcessor implements ILogEventProcessor {

    private static final Logger log = LoggerFactory.getLogger("searchLogger");

    private long searchTSO;
    private long startCmdTSO;

    public XACommitEventProcessor(long searchTSO, long startCmdTSO) {
        this.searchTSO = searchTSO;
        this.startCmdTSO = startCmdTSO;
    }

    private void processCommit(LogEvent event, String xid, ProcessorContext context) {
        if (LogEventUtil.containsCommitGCN(event)) {
            context.setLastTSO(((QueryLogEvent) event).getCommitGCN());
        }

        context.onComplete(xid, buildPosition(event, context));
        // reset last tso
        context.setLastTSO(null);
    }

    @Override
    public void handle(LogEvent event, ProcessorContext context) {
        String xid = LogEventUtil.getXid(event);
        if (StringUtils.isNotBlank(xid) && LogEventUtil.isValidXid(xid)) {
            processCommit(event, xid, context);
        } else {
            context.setCurrentTran(null);
        }
    }

    private BinlogPosition buildPosition(LogEvent event, ProcessorContext context) {
        return new BinlogPosition(context.getLogPosition().getFileName(), event.getLogPos() - event.getEventLen(),
            event.getServerId(), event.getWhen());
    }
}
