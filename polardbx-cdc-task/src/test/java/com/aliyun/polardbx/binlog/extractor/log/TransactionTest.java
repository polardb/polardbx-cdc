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
