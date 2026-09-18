/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.handler;

import com.aliyun.polardbx.binlog.canal.binlog.BinlogParser;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.TableMapLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.client.CdcEventData;
import com.aliyun.polardbx.binlog.client.LogEventWrapper;
import com.lmax.disruptor.WorkHandler;

import java.io.UnsupportedEncodingException;
import java.util.Collections;

/**
 * 行事件解析处理器，负责将RowsLogEvent解析为DefaultRowChange。
 * <p>
 * 支持两种工作模式：
 * - Disruptor模式：实现WorkHandler接口，多线程并行处理RingBuffer中的LogEventWrapper
 * - 优化模式：通过handle(CdcEventData)方法，由parser线程池调用
 * <p>
 * 解析完成后会清空原始LogEvent引用以释放内存。
 */
public class RowLogEventHandler implements WorkHandler<LogEventWrapper> {

    /**
     * 服务端字符集，用于解析行数据中的字符串
     */
    private final ServerCharactorSet serverCharset;
    /**
     * 表名过滤器，可为null表示不过滤
     */
    private final RowTableNameFilter rowTableNameFilter;

    public RowLogEventHandler(ServerCharactorSet serverCharset, RowTableNameFilter rowTableNameFilter) {
        this.serverCharset = serverCharset;
        this.rowTableNameFilter = rowTableNameFilter;
    }

    /**
     * 将RowsLogEvent解析为DefaultRowChange对象。
     * 使用BinlogParser进行实际解析，含列元数据和行数据。
     */
    private DefaultRowChange processRowsLogEvent(RowsLogEvent rowsLogEvent) throws UnsupportedEncodingException {
        BinlogParser parser = new BinlogParser();
        parser.setBinaryLog(true);
        return parser.parse(null,
            rowsLogEvent,
            serverCharset.getCharacterSetDatabase());
    }

    /**
     * 根据表名过滤器判断是否应该跳过该行事件
     */
    public boolean filter(RowsLogEvent rowsLogEvent) {
        if (null == rowTableNameFilter) {
            return false;
        }
        TableMapLogEvent tableMapLogEvent = rowsLogEvent.getTable();
        String fullTableName = tableMapLogEvent.getDbName() + "." + tableMapLogEvent.getTableName();
        return rowTableNameFilter.filter(fullTableName);
    }

    /**
     * Disruptor模式的处理入口。
     * 从 LogEventWrapper 中取出 RowsLogEvent，解析后将结果放回Wrapper。
     * 解析完成后清空原始 LogEvent 引用以释放内存。
     */
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

    /**
     * 优化模式的处理入口，由parser线程池调用。
     * 从 CdcEventData 中取出 RowsLogEvent，解析后将结果设置回 CdcEventData。
     * 解析完成后清空原始 LogEvent 和 traceInfo 引用以释放内存。
     */
    public void handle(CdcEventData cdcEventData) throws Exception {
        LogEvent logEvent = cdcEventData.getLogEvent();
        if (logEvent instanceof RowsLogEvent) {
            String traceInfo = cdcEventData.getLastTraceInfo();
            RowsLogEvent rowsLogEvent = (RowsLogEvent) logEvent;
            DefaultRowChange dbmsEvent = null;
            if (!filter(rowsLogEvent)) {
                dbmsEvent = processRowsLogEvent(rowsLogEvent);
            }
            if (dbmsEvent != null) {
                dbmsEvent.setTraceInfo(traceInfo);
                cdcEventData.setEvent(dbmsEvent);
            }
        }
        cdcEventData.setLogEvent(null);
        cdcEventData.setLastTraceInfo(null);
    }
}
