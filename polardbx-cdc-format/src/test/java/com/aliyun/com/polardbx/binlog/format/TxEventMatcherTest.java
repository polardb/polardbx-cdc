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
package com.aliyun.com.polardbx.binlog.format;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.canal.binlog.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import org.junit.Test;

import java.io.File;

public class TxEventMatcherTest {

    @Test
    public void matchTxEvent() throws Exception {
        String dir = "/Users/yanfenglin/Downloads/tmp";
        String binlogFile = "mysql-bin.000001";
        FileLogFetcher fetcher = new FileLogFetcher(1024);
        fetcher.open(dir + File.separator + binlogFile, 48120);
        LogDecoder decoder = new LogDecoder();
        decoder.handle(LogEvent.ROTATE_EVENT);
        decoder.handle(LogEvent.FORMAT_DESCRIPTION_EVENT);
        decoder.handle(LogEvent.QUERY_EVENT);
        decoder.handle(LogEvent.XID_EVENT);
        decoder.handle(LogEvent.XA_PREPARE_LOG_EVENT);
        LogContext context = new LogContext();
        LogPosition logPosition = new LogPosition(binlogFile, 0);
        context.setLogPosition(logPosition);
        boolean begin = false;
        while (fetcher.fetch()) {
            LogEvent logEvent = decoder.decode(fetcher, context);

            if (Thread.interrupted()) {
                break;
            }

            if (logEvent == null) {
                continue;
            }
            if (LogEventUtil.isStart(logEvent)) {
                String xid = LogEventUtil.getXid(logEvent);
                if (xid != null) {
                    Long tranId = LogEventUtil.getTranIdFromXid(xid, "utf8");
                    if (tranId == 1466199907692974080L) {
                        System.out.println(xid);
                        System.out.println(tranId);
                    }
                }
            }
            if (LogEventUtil.isCommit(logEvent) || LogEventUtil.isPrepare(logEvent)) {
                begin = false;
            }
        }
        System.out.println(begin);
    }
}
