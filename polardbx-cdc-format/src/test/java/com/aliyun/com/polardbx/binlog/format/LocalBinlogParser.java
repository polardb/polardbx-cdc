/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
 */
package com.aliyun.com.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.canal.binlog.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;

import java.io.IOException;

public class LocalBinlogParser {

    private int bufferSize = 8192;

    private String binlogfilename;

    public LocalBinlogParser(String binlogfilename) {
        this.binlogfilename = binlogfilename;
    }

    public void dump(SinkFunction sinkFunction) throws IOException, TableIdNotFoundException {
        FileLogFetcher fetcher = new FileLogFetcher(bufferSize);
        fetcher.open(binlogfilename);
        LogDecoder decoder = new LogDecoder();
        decoder.handle(LogEvent.ROTATE_EVENT);
        decoder.handle(LogEvent.FORMAT_DESCRIPTION_EVENT);
        decoder.handle(LogEvent.QUERY_EVENT);
        decoder.handle(LogEvent.TABLE_MAP_EVENT);
        decoder.handle(LogEvent.XID_EVENT);
        decoder.handle(LogEvent.SEQUENCE_EVENT);
        decoder.handle(LogEvent.GCN_EVENT);
        decoder.handle(LogEvent.WRITE_ROWS_EVENT);
        decoder.handle(LogEvent.UPDATE_ROWS_EVENT);
        decoder.handle(LogEvent.DELETE_ROWS_EVENT);
        decoder.handle(LogEvent.WRITE_ROWS_EVENT_V1);
        decoder.handle(LogEvent.UPDATE_ROWS_EVENT_V1);
        decoder.handle(LogEvent.DELETE_ROWS_EVENT_V1);

        decoder.handle(LogEvent.XA_PREPARE_LOG_EVENT);
        LogContext context = new LogContext();
        context.setServerCharactorSet(new ServerCharactorSet("utf8", "utf8", "utf8", "utf8"));
        LogPosition logPosition = new LogPosition(binlogfilename, 0);
        context.setLogPosition(logPosition);
        while (fetcher.fetch()) {
            LogEvent logEvent = decoder.decode(fetcher, context);

            if (Thread.interrupted()) {
                break;
            }

            if (logEvent == null) {
                throw new RuntimeException("parse failed null");
            }

            if (!sinkFunction.sink(logEvent, context.getLogPosition())) {
                break;
            }
        }
    }
}
