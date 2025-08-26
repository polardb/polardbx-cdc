/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class Rds8DecodeTest extends BaseTest {

    @Test
    public void parseTest() throws URISyntaxException, IOException {
        FileLogFetcher fetcher = new FileLogFetcher();
        fetcher.open(new File(Rds8DecodeTest.class.getClassLoader().getResource("mysql_bin.19_1").toURI()), 4);
        LogDecoder logDecoder = new LogDecoder(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("mysql_bin.19", 0));
        logContext.setServerCharactorSet(new ServerCharactorSet());
        boolean parseQueryEvent = false;
        while (fetcher.fetch()) {
            LogEvent logEvent = logDecoder.decode(fetcher.buffer(), logContext);
            if (logEvent == null) {
                continue;
            }
            if (logEvent instanceof QueryLogEvent) {
                parseQueryEvent = true;
            }
        }
        Assert.assertTrue(parseQueryEvent);
    }

    /**
     * 该文件来源：
     * dd if=mysql_bin.000002 of=p1 bs=1 count=462
     * 0-462
     * dd if=mysql_bin.000002 of=p2 bs=1 skip=11159660 count=1147
     * 11159660-11160807
     * cat p1 p2 > mysql_bin_panda.2
     */
    @Test
    public void testParsePanda() {
        parse("mysql_bin_panda.2");
    }

    @Test
    public void testGetPandaCodeName() {
        String name = QueryLogEvent.findCodeName(QueryLogEvent.Q_OPT_INDEX_FORMAT_PANDA_ENABLED);
        Assert.assertEquals("Q_OPT_INDEX_FORMAT_PANDA_ENABLED", name);
    }

    @SneakyThrows
    private void parse(String fileName) {
        FileLogFetcher fetcher = new FileLogFetcher();
        fetcher.open(new File(Rds8DecodeTest.class.getClassLoader().getResource(fileName).toURI()), 4);
        LogDecoder logDecoder = new LogDecoder(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition(fileName, 0));
        logContext.setServerCharactorSet(new ServerCharactorSet());
        boolean parseQueryEvent = false;
        while (fetcher.fetch()) {
            LogEvent logEvent = logDecoder.decode(fetcher.buffer(), logContext);
            if (logEvent == null) {
                continue;
            }
            if (logEvent instanceof QueryLogEvent) {
                parseQueryEvent = true;
            }
        }
        Assert.assertTrue(parseQueryEvent);

    }

}
