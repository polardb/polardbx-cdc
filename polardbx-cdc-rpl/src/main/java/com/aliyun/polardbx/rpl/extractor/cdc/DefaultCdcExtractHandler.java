/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.aliyun.polardbx.rpl.extractor.cdc;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSTransactionEnd;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultOption;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.core.handle.EventHandle;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.extractor.LogEventConvert;
import com.aliyun.polardbx.rpl.pipeline.BasePipeline;
import com.aliyun.polardbx.rpl.pipeline.MessageEvent;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

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
            Timestamp extractTimestamp = new Timestamp(System.currentTimeMillis());
            DBMSEvent dbmsEvent = sqldbmsEvent.getDbMessage();
            if (!acceptEvent(dbmsEvent)) {
                return;
            }
            datas.clear();
            MessageEvent e = new MessageEvent();
            e.setDbmsEvent(sqldbmsEvent.getDbMessage());
            e.setPosition(sqldbmsEvent.getPosition().toString());
            e.setSourceTimestamp(new Timestamp(sqldbmsEvent.getPosition().getTimestamp() * 1000));
            e.setExtractTimestamp(extractTimestamp);
            if (dbmsEvent instanceof DefaultRowChange) {
                DefaultRowChange rowChange = (DefaultRowChange) sqldbmsEvent.getDbMessage();
                rowChange.putOption(
                    new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_TIMESTAMP, e.getSourceTimestamp()));
                rowChange.putOption(
                    new DefaultOption(RplConstants.BINLOG_EVENT_OPTION_POSITION, e.getPosition()));

            }

            datas.add(e);
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
