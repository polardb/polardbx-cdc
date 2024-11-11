/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle.processor;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.handle.ILogEventProcessor;
import com.aliyun.polardbx.binlog.canal.core.handle.ProcessorContext;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.TranPosition;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XAStartEventProcessor implements ILogEventProcessor<QueryLogEvent> {

    private static final Logger logger = LoggerFactory.getLogger("searchLogger");

    @Override
    public void handle(QueryLogEvent event, ProcessorContext context) {
        if (context.getCurrentTran() != null) {
            throw new PolardbxException(
                "new transaction start , but last tran not complete!" + context.getCurrentTran() + " this pos : "
                    + context
                    .getLogPosition());
        }

        String xid = LogEventUtil.getXid(event);

        if (xid == null || !LogEventUtil.isValidXid(xid)) {
            return;
        }

        LogPosition logPosition = context.getLogPosition();
        TranPosition tranPosition = new TranPosition();
        try {
            AuthenticationInfo authenticationInfo = context.getAuthenticationInfo();
            tranPosition.setTransId(LogEventUtil.getTranIdFromXid(xid, authenticationInfo.getCharset()));
        } catch (Exception e) {
            logger.error("process start event failed! pos : " + logPosition.toString(), e);
            throw new PolardbxException(e);
        }
        tranPosition.setXid(xid);
        tranPosition.setBegin(buildPosition(event, context));
        context.onStart(tranPosition);
    }

    private BinlogPosition buildPosition(LogEvent event, ProcessorContext context) {
        return new BinlogPosition(context.getLogPosition().getFileName(), event.getLogPos() - event.getEventLen(),
            event.getServerId(), event.getWhen());
    }
}
