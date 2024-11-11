/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.cdc;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowsQueryLog;
import com.aliyun.polardbx.binlog.canal.core.handle.EventHandle;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.extractor.CdcExtractor;
import com.aliyun.polardbx.rpl.extractor.LogEventConvert;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.pipeline.MessageEvent;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

public class DefaultCdcExtractHandler implements EventHandle {

    protected final LogEventConvert binlogParser;
    protected BasePipeline pipeline;
    protected CdcExtractor cdcExtractor;
    protected List<MessageEvent> datas = new ArrayList<>();

    public DefaultCdcExtractHandler(LogEventConvert binlogParser, BasePipeline pipeline, CdcExtractor cdcExtractor) {
        this.binlogParser = binlogParser;
        this.pipeline = pipeline;
        this.cdcExtractor = cdcExtractor;
    }

    @Override
    public boolean interrupt() {
        return false;
    }

    @Override
    public Set<Integer> interestEvents() {
        return null;
    }

    @Override
    public void onStart() {

    }

    private boolean acceptEvent(DBMSEvent dbmsEvent) {
        if (dbmsEvent instanceof DBMSTransactionEnd) {
            return true;
        }
        if (dbmsEvent instanceof DefaultRowChange) {
            return true;
        }
        if (dbmsEvent instanceof DefaultQueryLog) {
            return true;
        }
        if (dbmsEvent instanceof DefaultRowsQueryLog) {
            return true;
        }
        return false;
    }

    @Override
    public void handle(LogEvent logEvent, LogPosition position) {
        try {
            MySQLDBMSEvent sqldbmsEvent = binlogParser.parse(logEvent, false);
            long now = System.currentTimeMillis();
            if (logEvent.getHeader().getType() != LogEvent.HEARTBEAT_LOG_EVENT) {
                StatMetrics.getInstance().setReceiveDelay(now - logEvent.getWhen() * 1000);
                StatMetrics.getInstance().addInMessageCount(1);
                StatMetrics.getInstance().addInBytes(logEvent.getEventLen());
            }
            if (sqldbmsEvent == null) {
                return;
            }
            DBMSEvent dbmsEvent = sqldbmsEvent.getDbmsEventPayload();
            dbmsEvent.setEventSize(logEvent.getEventLen());
            dbmsEvent.setSourceTimeStamp(logEvent.getWhen() * 1000);
            dbmsEvent.setExtractTimeStamp(now);
            dbmsEvent.setPosition(sqldbmsEvent.getPosition().toString());
            dbmsEvent.setRtso(sqldbmsEvent.getPosition().getRtso());
            if (!acceptEvent(dbmsEvent)) {
                return;
            }
            datas.clear();
            if (dbmsEvent instanceof DefaultRowChange) {
                DefaultRowChange rowChange = (DefaultRowChange) dbmsEvent;
                if (rowChange.getRowSize() == 1) {
                    MessageEvent e = new MessageEvent();
                    e.setDbmsEvent(dbmsEvent);
                    datas.add(e);
                } else {
                    for (int rownum = 1; rownum <= rowChange.getRowSize(); rownum++) {
                        // 多行记录,拆分为单行进行处理
                        DefaultRowChange split = new DefaultRowChange(rowChange.getAction(),
                            rowChange.getSchema(),
                            rowChange.getTable(),
                            rowChange.getColumnSet(),
                            null,
                            (List<DBMSOption>) rowChange.getOptions());
                        if (DBMSAction.UPDATE == rowChange.getAction()) {
                            // 需要复制一份changeColumns,避免更新同一个引用
                            split.setChangeColumnsBitSet((BitSet) rowChange.getChangeIndexes().clone());
                            split.setChangeData(1, rowChange.getChangeData(rownum));
                        }
                        split.setRowData(1, rowChange.getRowData(rownum));
                        // 每一行一个事件
                        MessageEvent e = new MessageEvent();
                        e.setDbmsEvent(split);
                        // nowPosition.setInnerOffset(innerOffset++);
                        split.setSourceTimeStamp(rowChange.getSourceTimeStamp());
                        split.setExtractTimeStamp(rowChange.getExtractTimeStamp());
                        split.setPosition(dbmsEvent.getPosition());
                        split.setRtso(dbmsEvent.getRtso());
                        datas.add(e);
                    }
                }
            } else {
                MessageEvent e = new MessageEvent();
                e.setDbmsEvent(dbmsEvent);
                datas.add(e);
            }
            sqldbmsEvent.tryRelease();
            pipeline.writeRingbuffer(datas);
        } catch (Exception e) {
            if (position != null) {
                String message = String.format("binlog parser error at position %s:%s",
                    position.getFileName(), position.getPosition());
                throw new CanalParseException(message, e);
            } else {
                throw new PolardbxException("binlog parser error", e);
            }
        }
    }

    @Override
    public void onEnd() {

    }
}
