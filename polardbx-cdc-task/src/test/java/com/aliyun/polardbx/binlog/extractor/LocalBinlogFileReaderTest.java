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

package com.aliyun.polardbx.binlog.extractor;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.canal.binlog.LocalBinlogParser;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.exception.TableIdNotFoundException;
import org.junit.Test;

import java.io.IOException;

public class LocalBinlogFileReaderTest {
    @Test
    public void testRead() throws IOException, TableIdNotFoundException {
        LocalBinlogParser parser = new LocalBinlogParser("/Users/yanfenglin/Downloads/binlog.000001");
        LogDecoder decoder = new LogDecoder(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext(FormatDescriptionLogEvent.FORMAT_DESCRIPTION_EVENT_5_x);
        logContext.setLogPosition(new LogPosition("binlog.000001"));
        parser.dump(new SinkFunction() {
            @Override
            public boolean sink(LogEvent event, LogPosition logPosition)
                throws CanalParseException, TableIdNotFoundException {
                if (event instanceof RowsLogEvent) {
                    RowsLogEvent rowsLogEvent = (RowsLogEvent) event;
                    if (rowsLogEvent.getColumns().isEmpty()) {
                        this.getClass();
                    }
                    System.out.println(rowsLogEvent.getColumns().toString() + rowsLogEvent.getTable().getTableName());
                }
                return true;
            }
        });
    }

    @Test
    public void testEventBytesRecord() throws IOException {
        byte[] datas = {
            34, 124, 81, 97, 23, -121, 85, 41, 78, 60, 0, 0, 0, 0, 0, 0, 0, 0, 0, -35, 4, 0, 0, 0, 0, 1, 0, 4, -1, -16,
            61, 53, 12, 0, 10, 116, 101, 115, 116, 112, 32, 111, 52, 52, 52, -103, -86, -73, 2, 71, 0, 0, 77, 1, 0, 0,
            0, 0, 0, 0};
        LogDecoder decoder = new LogDecoder(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
        LogBuffer logBuffer = new LogBuffer(datas, 0, datas.length);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("binlog.000001"));
        LogEvent event = decoder.decode(logBuffer, logContext);
        System.out.println(JSON.toJSONString(event));
    }
}
