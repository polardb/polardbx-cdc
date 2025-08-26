/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.handler;

import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.client.CdcEventData;
import com.aliyun.polardbx.binlog.client.LogEventWrapper;
import com.aliyun.polardbx.binlog.client.listener.IEventHandler;
import com.aliyun.polardbx.binlog.client.metrics.CdcClientMetricsManager;
import com.lmax.disruptor.EventHandler;
import org.apache.commons.lang.StringUtils;

import java.util.List;

public class OutputHandler implements EventHandler<LogEventWrapper> {

    private final IEventHandler handle;
    private boolean shouldSkip = true;
    private volatile LogPosition lastPushLogPosition;
    private final BinlogPosition startPosition;

    public OutputHandler(IEventHandler handle, BinlogPosition startPosition) {
        this.handle = handle;
        this.startPosition = startPosition;
        // 第一次要赋值一下，因为外部会有调用
        this.lastPushLogPosition = new LogPosition(startPosition.getFileName(), startPosition.getPosition());
    }

    @Override
    public void onEvent(LogEventWrapper event, long sequence, boolean endOfBatch) throws Exception {
        List<DBMSEvent> dbmsEventList = event.getDbmsEventList();
        if (dbmsEventList != null) {
            for (DBMSEvent dbmsEvent : dbmsEventList) {
                pushEvent(dbmsEvent, event.getLogPosition());
            }
        }
        event.setLogEvent(null);
        event.setDbmsEventList(null);
        event.setLogEvent(null);
    }

    private boolean shouldSkip(final LogPosition logPosition) {
        if (StringUtils.equalsIgnoreCase(logPosition.getFileName(), startPosition.getFileName()) &&
            logPosition.getPosition() <= startPosition.getPosition()) {
            return true;
        }
        shouldSkip = false;
        return false;
    }

    private void pushEvent(DBMSEvent eventData, LogPosition logPosition) {
        this.lastPushLogPosition = logPosition;
        if (shouldSkip && shouldSkip(logPosition)) {
            return;
        }
        long begin = System.currentTimeMillis();
        CdcEventData cdcEventData =
            new CdcEventData(logPosition.getFileName(), logPosition.getPosition(), eventData);
        handle.onHandle(cdcEventData);
        long rt = System.currentTimeMillis() - begin;
        CdcClientMetricsManager.getInstance().recordRt(rt);
        CdcClientMetricsManager.getInstance().addEvent(1);
    }

    public LogPosition getLastPushLogPosition() {
        return lastPushLogPosition;
    }
}
