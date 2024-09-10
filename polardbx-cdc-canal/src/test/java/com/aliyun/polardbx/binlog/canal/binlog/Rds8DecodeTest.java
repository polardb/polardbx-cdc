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
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.testing.BaseTest;
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
}
