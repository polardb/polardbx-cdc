/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class TransactionTest extends BaseTest {

    @Test
    public void testTraceIdOrder() throws Exception {
        testInternal();
    }

    private void testInternal() throws Exception {
        FileLogFetcher fetcher = new FileLogFetcher();
        fetcher.open(new File(TransactionTest.class.getClassLoader().getResource("binlog/mysql_bin.1").toURI()), 4);
        LogDecoder logDecoder = new LogDecoder(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("mysql_bin.1", 0));
        logContext.setServerCharactorSet(new ServerCharactorSet());

        LogEvent lastLogEvent = null;
        Transaction transaction = null;
        RuntimeContext runtimeContext = new RuntimeContext(new ThreadRecorder("pxc-xxx"));
        final List<String> traceIdList = new ArrayList<>();
        final List<String> compareList =
            Lists.newArrayList("00000000030000000000", "00000000030000000000", "00000000030000000000",
                "00000000030000000000", "00000000030000000000", "00000000030000000000", "00000000030000000000",
                "00000000030000000000");

        while (fetcher.fetch()) {
            LogEvent logEvent = logDecoder.decode(fetcher.buffer(), logContext);
            if (logEvent == null) {
                continue;
            }

            if (logEvent.getHeader().getType() == LogEvent.FORMAT_DESCRIPTION_EVENT) {
                lastLogEvent = logEvent;
                continue;
            }

            if (lastLogEvent != null && lastLogEvent.getHeader().getType() == LogEvent.FORMAT_DESCRIPTION_EVENT) {
                transaction = new Transaction(logEvent, runtimeContext) {
                    String lastTraceId;
                    int eventCount;

                    @Override
                    void buildBuffer() {
                    }

                    @Override
                    void addTxnBuffer(LogEvent logEvent) {
                        if (StringUtils.isNotBlank(lastTraceId) && logEvent.getTrace().compareTo(lastTraceId) < 0) {
                            throw new PolardbxException(
                                "detected disorderly traceId，current traceId is " + logEvent.getTrace()
                                    + ",last traceId is " + lastTraceId);
                        }
                        lastTraceId = logEvent.getTrace();
                        eventCount++;
                        traceIdList.add(logEvent.getTrace());
                    }

                    @Override
                    public int getEventCount() {
                        return eventCount;
                    }
                };
            } else {
                transaction.processEvent(logEvent, runtimeContext);
            }

            lastLogEvent = logEvent;
        }

        Assert.assertEquals(compareList, traceIdList);
    }
}
