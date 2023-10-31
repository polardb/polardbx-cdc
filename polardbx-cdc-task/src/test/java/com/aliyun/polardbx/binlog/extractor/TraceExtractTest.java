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
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.extractor.binlog.BinlogGenerator;
import org.junit.Assert;
import org.junit.Test;

public class TraceExtractTest {

    @Test
    public void grapTraceTest() throws Exception {
        BinlogGenerator generator = new BinlogGenerator("test_db",
            "test_tb",
            "create table tt(id bigint(20) default 1)");
        Assert.assertArrayEquals(new String[] {"00000000020000000002", "1024"},
            LogEventUtil.buildTrace(generator.generateRowQueryLogEvent()));
    }

    @Test
    public void testTrace() throws Exception {
        BinlogGenerator generator = new BinlogGenerator("test_db",
            "test_tb",
            "create table tt(id bigint(20) default 1)");
        Assert.assertArrayEquals(new String[] {"00000000000000000000", "1390188355"}, LogEventUtil.buildTrace(
            generator.generateRowQueryLogEvent("/*DRDS /11.196.59.141/1322a3c35bc01000/0/1390188355/ */")));

        Assert.assertArrayEquals(new String[] {"00000000030000000001", null}, LogEventUtil.buildTrace(
            generator.generateRowQueryLogEvent("/*DRDS /11.196.49.49/1322a3c37f401001-3/1// */")));
    }

    @Test
    public void buildTraceTest() {
        Assert.assertEquals("00000000010000000000", LogEventUtil.buildTraceId("1", null));
        Assert.assertEquals("00000000020000000000", LogEventUtil.buildTraceId("2", null));
        Assert.assertEquals("00000000030000000000", LogEventUtil.buildTraceId("3", null));
        Assert.assertEquals("00000000040000000001", LogEventUtil.buildTraceId("4", "1"));
        Assert.assertEquals("00000000040000000002", LogEventUtil.buildTraceId("4", "2"));
        Assert.assertEquals("00000000050000000000", LogEventUtil.buildTraceId("5", null));
        Assert.assertEquals("00000001000000000128", LogEventUtil.buildTraceId("100", "128"));
    }
}
