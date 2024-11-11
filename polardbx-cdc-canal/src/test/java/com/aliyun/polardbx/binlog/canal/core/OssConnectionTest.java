/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core;

import com.aliyun.polardbx.binlog.api.RdsApi;
import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.LogFetcher;
import com.aliyun.polardbx.binlog.canal.core.dump.OssConnection;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;

@Ignore
public class OssConnectionTest extends BaseTest {

    @Test
    public void testUTC() throws ParseException {
        String utfHost = RdsApi.formatUTCTZ(new Date(1651782957000L));
        String utcBegin = RdsApi.formatUTCTZ(new Date(1651569048284L));
        System.out.println(utcBegin);
        System.out.println(BinlogFile.format(utcBegin));
        System.out.println(utfHost);
        System.out.println(BinlogFile.format(utfHost));

    }

    @Test
    public void testMulti() throws InterruptedException {
        Thread t = new Thread(() -> {
            try {
                test();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        Thread t2 = new Thread(() -> {
            try {
                test();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        t.start();
        t2.start();
        t.join();
        t2.join();
    }

    @Test
    public void test() throws IOException {
        System.setProperty("RDS_API", "");
        System.setProperty("taskName", "Final");
        OssConnection ossConnection =
            new OssConnection("", "", "", "", 100242035L, 5, null, 0L);
        ossConnection.setTest(true);
        ossConnection.connect();
        ossConnection.printBinlogQueue();
        LogFetcher fetcher = ossConnection.providerFetcher("mysql-bin.000765", 0, true);
        LogDecoder decoder = new LogDecoder();
        decoder.handle(LogEvent.ROTATE_EVENT);
        decoder.handle(LogEvent.FORMAT_DESCRIPTION_EVENT);
        decoder.handle(LogEvent.QUERY_EVENT);
        decoder.handle(LogEvent.XID_EVENT);
        decoder.handle(LogEvent.SEQUENCE_EVENT);
        decoder.handle(LogEvent.GCN_EVENT);
        decoder.handle(LogEvent.XA_PREPARE_LOG_EVENT);
        decoder.handle(LogEvent.WRITE_ROWS_EVENT_V1);
        decoder.handle(LogEvent.WRITE_ROWS_EVENT);
        decoder.handle(LogEvent.TABLE_MAP_EVENT);
        LogContext lc = new LogContext();
        lc.setServerCharactorSet(new ServerCharactorSet("utf8", "utf8", "utf8", "utf8"));
        lc.setLogPosition(new LogPosition("", 0));

        while (fetcher.fetch()) {
            LogEvent event = decoder.decode(fetcher.buffer(), lc);
            if (event == null) {
                continue;
            }
        }
    }
}
