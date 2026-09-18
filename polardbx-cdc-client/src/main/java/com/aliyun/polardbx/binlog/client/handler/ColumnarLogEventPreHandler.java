/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.handler;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSHeartbeatLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSRotateEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionBegin;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.event.HeartbeatLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RotateLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XidLogEvent;
import com.aliyun.polardbx.binlog.client.CdcEventData;
import com.aliyun.polardbx.binlog.client.LogEventWrapper;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * 列存场景的binlog事件预处理器，继承自LogEventPreHandler。
 * <p>
 * 负责将原始LogEvent转换为DBMSEvent，包括：
 * - RowsLogEvent：仅记录traceInfo，保留原始LogEvent留给后续并行解析阶段处理
 * - QueryLogEvent：解析为DDL事件
 * - RowsQueryLogEvent：解析事务开始/traceInfo等
 * - XidLogEvent：生成事务提交事件
 * - HeartbeatLogEvent：生成心跳事件
 * - RotateLogEvent：生成文件轮转事件
 * <p>
 * 提供两种入口：
 * - onEvent(): 用于Disruptor模式，处理LogEventWrapper
 * - preHandle(): 用于优化模式，处理CdcEventData
 */
public class ColumnarLogEventPreHandler extends LogEventPreHandler {
    protected static final Logger logger = LoggerFactory.getLogger(ColumnarLogEventPreHandler.class);

    public ColumnarLogEventPreHandler() {
        super();
    }

    /**
     * Disruptor模式的事件预处理入口。
     * 相比父类新增了RotateLogEvent的处理，以支持多文件轮转。
     */
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
                DBMSEvent eventData = processQueryLogEvent(queryLogEvent);
                if (null != eventData) {
                    eventData.setEventSize(queryLogEvent.getEventLen());
                }
                eventDataList.add(eventData);
            } else if (logEvent instanceof RowsQueryLogEvent) {
                RowsQueryLogEvent rowsQueryLogEvent = (RowsQueryLogEvent) logEvent;
                DBMSEvent eventData = processRowsQueryLogEvent(rowsQueryLogEvent);
                if (null != eventData) {
                    eventData.setEventSize(rowsQueryLogEvent.getEventLen());
                }
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
                commitEvent.setEventSize(logEvent.getEventLen());
                lastTraceInfo = null;
            } else if (logEvent instanceof HeartbeatLogEvent) {
                HeartbeatLogEvent heartbeatLogEvent = (HeartbeatLogEvent) logEvent;
                DBMSHeartbeatLog heartbeatEvent = new DBMSHeartbeatLog(heartbeatLogEvent.getLogIdent());
                heartbeatEvent.setEventSize(heartbeatLogEvent.getEventLen());
                eventDataList.add(heartbeatEvent);
            } else if (logEvent instanceof RotateLogEvent) {
                RotateLogEvent rotateLogEvent = (RotateLogEvent) logEvent;
                DBMSRotateEvent DBMSRotateEvent =
                    new DBMSRotateEvent(rotateLogEvent.getFilename(), rotateLogEvent.getPosition());
                DBMSRotateEvent.setEventSize(rotateLogEvent.getEventLen());
                eventDataList.add(DBMSRotateEvent);
            }
            if (!eventDataList.isEmpty()) {
                event.setDbmsEventList(eventDataList);
            }
            if (!(logEvent instanceof RowsLogEvent)) {
                event.setLogEvent(null);
            }
        }
    }

    /**
     * 优化模式的事件预处理入口。
     * 与onEvent类似，但操作的是CdcEventData而非LogEventWrapper。
     * 对于RowsLogEvent仅设置traceInfo，其他类型的事件直接解析并设置到cdcEventData中。
     */
    public void preHandle(CdcEventData cdcEventData) throws Exception {
        final LogEvent logEvent = cdcEventData.getLogEvent();
        if (logEvent != null) {
            final String fileName = cdcEventData.getBinlogFileName();
            final long position = cdcEventData.getPosition();
            if (logEvent instanceof RowsLogEvent) {
                if (commitEvent != null) {
                    throw new PolardbxException(
                        "last commit event not receive cts event! with file: " + fileName + ", position: " + position);
                }
                if (beginEvent != null) {
                    beginEvent = null;
                }
                cdcEventData.setLastTraceInfo(lastTraceInfo);
                //下一个阶段，并发parser
            } else if (logEvent instanceof QueryLogEvent) {
                if (commitEvent != null) {
                    throw new PolardbxException(
                        "last commit event not receive cts event! with file: " + fileName + ", position: " + position);
                }
                if (beginEvent != null) {
                    beginEvent = null;
                }
                QueryLogEvent queryLogEvent = (QueryLogEvent) logEvent;
                DBMSEvent eventData = processQueryLogEvent(queryLogEvent);
                if (null != eventData) {
                    eventData.setEventSize(queryLogEvent.getEventLen());
                    cdcEventData.setEvent(eventData);
                }
            } else if (logEvent instanceof RowsQueryLogEvent) {
                RowsQueryLogEvent rowsQueryLogEvent = (RowsQueryLogEvent) logEvent;
                DBMSEvent eventData = processRowsQueryLogEvent(rowsQueryLogEvent);
                if (null != eventData) {
                    eventData.setEventSize(rowsQueryLogEvent.getEventLen());
                }
                if (!(eventData instanceof DBMSTransactionBegin) && beginEvent != null) {
                    // 老版本，事务begin不支持获取tso
                    eventData = beginEvent;
                    beginEvent = null;
                }
                if (eventData != null) {
                    cdcEventData.setEvent(eventData);
                }
            } else if (logEvent instanceof XidLogEvent) {
                beginEvent = null;
                commitEvent = new DBMSTransactionEnd();
                commitEvent.setEventSize(logEvent.getEventLen());
                lastTraceInfo = null;
            } else if (logEvent instanceof HeartbeatLogEvent) {
                HeartbeatLogEvent heartbeatLogEvent = (HeartbeatLogEvent) logEvent;
                DBMSHeartbeatLog heartbeatEvent = new DBMSHeartbeatLog(heartbeatLogEvent.getLogIdent());
                heartbeatEvent.setEventSize(heartbeatLogEvent.getEventLen());
                cdcEventData.setEvent(heartbeatEvent);
            } else if (logEvent instanceof RotateLogEvent) {
                RotateLogEvent rotateLogEvent = (RotateLogEvent) logEvent;
                DBMSRotateEvent DBMSRotateEvent =
                    new DBMSRotateEvent(rotateLogEvent.getFilename(), rotateLogEvent.getPosition());
                DBMSRotateEvent.setEventSize(rotateLogEvent.getEventLen());
                cdcEventData.setEvent(DBMSRotateEvent);
            }
            if (!(logEvent instanceof RowsLogEvent)) {
                cdcEventData.setLogEvent(null);
            }
        }
    }
}
