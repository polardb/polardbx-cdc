/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor;

import com.aliyun.polardbx.binlog.canal.LogEventUtil;
import com.aliyun.polardbx.binlog.extractor.binlog.BinlogGenerator;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

@Slf4j
public class TraceExtractTest {

    @Test
    public void grapTraceTest() throws Exception {
        BinlogGenerator generator = new BinlogGenerator("test_db",
            "test_tb",
            "create table tt(id bigint(20) default 1)");
        Assert.assertArrayEquals(new String[] {"00000000020000000002", "1024", null, "2"},
            LogEventUtil.buildTrace(generator.generateRowQueryLogEvent()));
    }

    @Test
    public void testTrace() throws Exception {
        BinlogGenerator generator = new BinlogGenerator("test_db",
            "test_tb",
            "create table tt(id bigint(20) default 1)");

        String trace = "/*DRDS /127.0.0.1/19df98715b401000/0// */";

        Assert.assertArrayEquals(new String[] {"00000000000000000000", "1390188355", null, "0"},
            LogEventUtil.buildTrace(
                generator.generateRowQueryLogEvent("/*DRDS /192.0.2.1/1322a3c35bc01000/0/1390188355/ */")));

        Assert.assertArrayEquals(new String[] {"00000000030000000001", null, null, "3"}, LogEventUtil.buildTrace(
            generator.generateRowQueryLogEvent("/*DRDS /192.0.2.1/1322a3c37f401001-3/1// */")));
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
