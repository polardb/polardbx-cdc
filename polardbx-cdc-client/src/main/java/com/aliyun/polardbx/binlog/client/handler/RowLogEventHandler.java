/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.handler;

import com.aliyun.polardbx.binlog.canal.binlog.BinlogParser;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.client.LogEventWrapper;
import com.lmax.disruptor.WorkHandler;

import java.io.UnsupportedEncodingException;
import java.util.Collections;

public class RowLogEventHandler implements WorkHandler<LogEventWrapper> {

    private final ServerCharactorSet serverCharset;
    private final RowTableNameFilter rowTableNameFilter;

    public RowLogEventHandler(ServerCharactorSet serverCharset, RowTableNameFilter rowTableNameFilter) {
        this.serverCharset = serverCharset;
        this.rowTableNameFilter = rowTableNameFilter;
    }

    private DefaultRowChange processRowsLogEvent(RowsLogEvent rowsLogEvent) throws UnsupportedEncodingException {
        BinlogParser parser = new BinlogParser();
        parser.setBinaryLog(true);
        return parser.parse(null,
            rowsLogEvent,
            serverCharset.getCharacterSetDatabase());
    }

    public boolean filter(RowsLogEvent rowsLogEvent) {
        TableMapLogEvent tableMapLogEvent = rowsLogEvent.getTable();
        String fullTableName = tableMapLogEvent.getDbName() + "." + tableMapLogEvent.getTableName();
        return rowTableNameFilter != null && rowTableNameFilter.filter(fullTableName);
    }

    @Override
    public void onEvent(LogEventWrapper event) throws Exception {
        LogEvent logEvent = event.getLogEvent();
        String traceInfo = event.getTraceInfo();
        if (logEvent instanceof RowsLogEvent) {
            RowsLogEvent rowsLogEvent = (RowsLogEvent) logEvent;
            DefaultRowChange dbmsEvent = null;
            if (!filter(rowsLogEvent)) {
                dbmsEvent = processRowsLogEvent(rowsLogEvent);
            }
            if (dbmsEvent != null) {
                dbmsEvent.setTraceInfo(traceInfo);
                event.setDbmsEventList(Collections.singletonList(dbmsEvent));
            }
        }
        event.setTraceInfo(null);
        event.setLogEvent(null);
    }
}
