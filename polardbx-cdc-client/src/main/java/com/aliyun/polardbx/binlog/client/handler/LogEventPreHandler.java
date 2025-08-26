/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.handler;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSHeartbeatLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTsoEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.event.HeartbeatLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.client.LogEventWrapper;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.lmax.disruptor.EventHandler;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;

public class LogEventPreHandler implements EventHandler<LogEventWrapper> {

    private static final String POLARX_DDL_TSO_PREFIX = "# POLARX_TSO=";
    private static final String POLARX_DDL_ORIGIN_SQL_PREFIX = "# POLARX_ORIGIN_SQL=";

    public static final String PRIVATE_DDL_ENCODE_BASE64 = "# POLARX_ORIGIN_SQL_ENCODE=BASE64";

    private DBMSTransactionEnd commitEvent = null;
    private DBMSTransactionBegin beginEvent = null;
    private String lastTraceInfo;

    private void checkTransaction(LogPosition logPosition, List<DBMSEvent> dbmsEventList) {
        if (commitEvent != null) {
            throw new PolardbxException("last commit event not receive cts event! with " + logPosition);
        }
        if (beginEvent != null) {
            dbmsEventList.add(beginEvent);
            beginEvent = null;
        }
    }

    private DBMSEvent processQueryLogEvent(QueryLogEvent queryLogEvent) {
        String queryData = queryLogEvent.getQuery();
        DBMSEvent eventData = null;
        if (queryData.startsWith("BEGIN")) {
            beginEvent = new DBMSTransactionBegin();
        } else {
            String ddl = queryLogEvent.getQuery();
            Scanner scanner = new Scanner(ddl);
            StringBuilder newDdlBuilder = new StringBuilder();
            boolean needDecode = false;
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                line = StringUtils.trim(line);
                if (StringUtils.startsWith(line, PRIVATE_DDL_ENCODE_BASE64)) {
                    needDecode = true;
                } else if (StringUtils.startsWith(line, POLARX_DDL_ORIGIN_SQL_PREFIX)) {
                    ddl = line.substring(POLARX_DDL_ORIGIN_SQL_PREFIX.length());
                    if (needDecode) {
                        ddl = new String(Base64.getDecoder().decode(ddl));
                        newDdlBuilder.append(POLARX_DDL_ORIGIN_SQL_PREFIX).append(ddl).append("\n");
                    } else {
                        newDdlBuilder.append(line).append("\n");
                    }
                } else {
                    newDdlBuilder.append(line).append("\n");
                }
            }
            eventData = new DefaultQueryLog(queryLogEvent.getDbName(), newDdlBuilder.toString(),
                new Timestamp(queryLogEvent.getExecTime()), queryLogEvent.getErrorCode(), queryLogEvent.getExecTime());

        }
        return eventData;
    }

    private DBMSEvent processRowsQueryLogEvent(RowsQueryLogEvent rowsQueryLogEvent) throws SQLException {
        String rowsQuery = rowsQueryLogEvent.getRowsQuery();
        String[] multiLine = rowsQuery.split("\n");
        DBMSEvent eventData = null;

        for (String line : multiLine) {
            if (StringUtils.startsWith(line, "CTS") || StringUtils.startsWith(line, "# CTS")) {
                String[] parameters = line.split("::");
                String tso = parameters[1];
                boolean isArchive = false;
                if (parameters.length > 2) {
                    for (int i = 2; i < parameters.length; i++) {
                        if (StringUtils.equals(parameters[i], "ARCHIVE")) {
                            isArchive = true;
                            break;
                        }
                    }
                }
                if (commitEvent != null) {
                    commitEvent.setTso(tso);
                    eventData = commitEvent;
                    commitEvent = null;
                } else if (beginEvent != null) {
                    beginEvent.setTso(tso);
                    beginEvent.setArchive(isArchive);
                    eventData = beginEvent;
                    beginEvent = null;
                } else {
                    eventData = new DBMSTsoEvent(tso);
                }
            } else if (StringUtils.startsWith(line, "/*DRDS")) {
                lastTraceInfo = line;
            }
        }

        return eventData;
    }

    @Override
    public void onEvent(LogEventWrapper event, long sequence, boolean endOfBatch) throws Exception {
        final LogEvent logEvent = event.getLogEvent();
        if (logEvent != null) {
            final LogPosition logPosition = event.getLogPosition();
            List<DBMSEvent> eventDataList = new ArrayList<>();
            if (logEvent instanceof RowsLogEvent) {
                checkTransaction(logPosition, eventDataList);
                event.setTraceInfo(lastTraceInfo);
                //下一个阶段，并发parser
            } else if (logEvent instanceof QueryLogEvent) {
                checkTransaction(logPosition, eventDataList);
                QueryLogEvent queryLogEvent = (QueryLogEvent) logEvent;
                eventDataList.add(processQueryLogEvent(queryLogEvent));

            } else if (logEvent instanceof RowsQueryLogEvent) {
                RowsQueryLogEvent rowsQueryLogEvent = (RowsQueryLogEvent) logEvent;
                DBMSEvent eventData = processRowsQueryLogEvent(rowsQueryLogEvent);
                if (!(eventData instanceof DBMSTransactionBegin) && beginEvent != null) {
                    // 老版本，事务begin不支持获取tso
                    eventData = beginEvent;
                    beginEvent = null;
                }
                if (eventData != null) {
                    eventDataList.add(eventData);
                }
            } else if (logEvent instanceof XidLogEvent) {
                beginEvent = null;
                commitEvent = new DBMSTransactionEnd();
                lastTraceInfo = null;
            } else if (logEvent instanceof HeartbeatLogEvent) {
                HeartbeatLogEvent heartbeatLogEvent = (HeartbeatLogEvent) logEvent;
                eventDataList.add(new DBMSHeartbeatLog(heartbeatLogEvent.getLogIdent()));

            }
            if (CollectionUtils.isNotEmpty(eventDataList)) {
                event.setDbmsEventList(eventDataList);
            }
            if (!(logEvent instanceof RowsLogEvent)) {
                event.setLogEvent(null);
            }
        }
    }
}
