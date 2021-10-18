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

package com.aliyun.polardbx.binlog.canal.core;

import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogFetcher;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.handle.EventHandle;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BinlogEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(BinlogEventProcessor.class);
    private EventHandle handle;
    private LogFetcher fetcher;
    private String binlogFileName;

    private boolean run;

    public void setHandle(EventHandle handle) {
        this.handle = handle;
    }

    public void init(ErosaConnection connection, String binlogFileName, long position, boolean search)
        throws IOException {
        connection.connect();
        if (this.fetcher != null) {
            this.fetcher.close();
        }
        this.fetcher = connection.providerFetcher(binlogFileName, position, search);
        this.binlogFileName = binlogFileName;
    }

    public void start() throws Exception {
        run = true;
        handle.onStart();
        LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        LogContext context = new LogContext();
        LogFetcher buffer = fetcher;
        LogPosition logPosition = new LogPosition(binlogFileName, 0);
        context.setLogPosition(logPosition);
        while (run && fetcher.fetch()) {
            LogEvent event = decoder.decode(buffer, context);

            if (event == null) {
                throw new CanalParseException("parse failed");
            }
            handle.handle(event, context.getLogPosition());
            if (handle.interupt()) {
                logger.error(" handler interupt");
                break;
            }
        }
        logger.error("event process or end run : " + run);
        fetcher.close();
        handle.onEnd();
    }

    public void stop() {
        if (!run) {
            return;
        }
        if (handle != null) {
            handle.onEnd();
        }
        run = false;
    }
}
