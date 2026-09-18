/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.position;

import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.dump.ErosaConnection;
import com.aliyun.polardbx.binlog.canal.core.dump.SinkFunction;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.format.utils.ErosaConnectionAdapter;
import com.aliyun.polardbx.rpl.extractor.search.PositionFinder;
import com.aliyun.polardbx.rpl.extractor.search.handler.PositionSearchHandler;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

@Ignore
public class LocalRplPositionFinderTest {

    @Test
    @Ignore
    public void testRotatePosition() throws Exception {

        ErosaConnection mysqlConnection = new ErosaConnectionAdapter() {
            @Override
            public void connect() throws IOException {
                super.connect();
            }

            @Override
            public void disconnect() throws IOException {
                super.disconnect();
            }

            @Override
            public void seek(String binlogfilename, Long binlogPosition, SinkFunction func) throws Exception {
                LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
                LogContext context = new LogContext();
                context.setServerCharactorSet(new ServerCharactorSet());
                context.setLogPosition(new LogPosition(binlogfilename, binlogPosition));
                FileLogFetcher fetcher = new FileLogFetcher(1024 * 1024, 0.5f);
                fetcher.open(new File("/Users/yanfenglin/binlog/binlog.000004"));
                while (fetcher.fetch()) {
                    LogEvent event = decoder.decode(fetcher.buffer(), context);
                    if (event != null) {
                        if (!func.sink(event, new LogPosition(binlogfilename, event.getLogPos()))) {
                            break;
                        }
                    }
                }
            }

            @Override
            public long binlogFileSize(String searchFileName) throws IOException {
                return super.binlogFileSize(searchFileName);
            }
        };
        BinlogPosition entryPosition = new BinlogPosition("binlog.000004", 80362L, -1, -1);
        BinlogPosition targetPos = search(entryPosition, mysqlConnection);
        Assert.assertNotNull(targetPos);
    }

    private BinlogPosition search(BinlogPosition entryPosition, ErosaConnection mysqlConnection)
        throws IOException {
        PositionFinder positionFinder = new PositionFinder(entryPosition, null,
            new PositionSearchHandler(entryPosition), mysqlConnection);
        positionFinder.setPolarx(true);
        return positionFinder.findPos();
    }
}
