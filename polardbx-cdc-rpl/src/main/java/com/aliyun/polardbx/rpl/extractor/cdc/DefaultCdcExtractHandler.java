/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.rpl.extractor.cdc;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.core.handle.EventHandle;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.extractor.LogEventConvert;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.pipeline.MessageEvent;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Set;

public class DefaultCdcExtractHandler implements EventHandle {

    protected final LogEventConvert binlogParser;
    protected BasePipeline pipeline;
    protected List<MessageEvent> datas = new ArrayList<>();

    public DefaultCdcExtractHandler(LogEventConvert binlogParser, BasePipeline pipeline) {
        this.binlogParser = binlogParser;
        this.pipeline = pipeline;
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
        if (dbmsEvent instanceof DBMSQueryLog) {
            return true;
        }
        return false;
    }

    @Override
    public void handle(LogEvent event, LogPosition position) {
        try {
            MySQLDBMSEvent sqldbmsEvent = binlogParser.parse(event, false);
            if (sqldbmsEvent == null) {
                return;
            }
            long now = System.currentTimeMillis();
            Timestamp extractTimestamp = new Timestamp(System.currentTimeMillis());
            DBMSEvent dbmsEvent = sqldbmsEvent.getDbMessageWithEffect();
            StatMetrics.getInstance().setReceiveDelay(now - event.getWhen() * 1000);
            StatMetrics.getInstance().addInMessageCount(1);
            StatMetrics.getInstance().addInBytes(dbmsEvent.getEventSize());
            if (!acceptEvent(dbmsEvent)) {
                return;
            }
            datas.clear();
            dbmsEvent.setEventSize(event.getEventLen());
            dbmsEvent.setSourceTimeStamp(now);

            if (dbmsEvent instanceof DefaultRowChange) {
                DefaultRowChange rowChange = (DefaultRowChange) dbmsEvent;
                if (rowChange.getRowSize() == 1) {
                    MessageEvent e = new MessageEvent();
                    e.setDbmsEvent(dbmsEvent);
                    e.setPosition(sqldbmsEvent.getPosition().toString());
                    e.setSourceTimestamp(new Timestamp(sqldbmsEvent.getPosition().getTimestamp() * 1000));
                    e.setExtractTimestamp(extractTimestamp);
                    rowChange.putOption(
                        new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_TIMESTAMP, e.getSourceTimestamp()));
                    rowChange.putOption(
                        new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_POSITION, e.getPosition()));
                    datas.add(e);
                } else {
                    BinlogPosition nowPosition = sqldbmsEvent.getPosition();
                    long innerOffset = 0;
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
                        e.setPosition(nowPosition.toString());
                        e.setSourceTimestamp(new Timestamp(sqldbmsEvent.getPosition().getTimestamp() * 1000));
                        e.setExtractTimestamp(extractTimestamp);
                        split.putOption(
                            new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_TIMESTAMP, e.getSourceTimestamp()));
                        split.putOption(
                            new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_POSITION, e.getPosition()));
                        datas.add(e);
                    }
                }
            } else {
                MessageEvent e = new MessageEvent();
                e.setDbmsEvent(dbmsEvent);
                e.setPosition(sqldbmsEvent.getPosition().toString());
                e.setSourceTimestamp(new Timestamp(sqldbmsEvent.getPosition().getTimestamp() * 1000));
                e.setExtractTimestamp(extractTimestamp);
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
