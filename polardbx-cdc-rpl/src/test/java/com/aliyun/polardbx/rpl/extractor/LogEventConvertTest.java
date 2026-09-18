/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.mariadb.AnnotateRowsEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author zm
 */
public class LogEventConvertTest extends RplWithGmsTablesBaseTest {

    private LogEventConvert logEventConvert;

    @Before
    public void setUp() {
        // 初始化LogEventConvert实例
        BinlogPosition binlogPosition = new BinlogPosition("mysql-bin.000001", "4");
        logEventConvert = new LogEventConvert(null, null, binlogPosition, null);
    }

    @Test
    public void heartbeatTest() {
        Assert.assertTrue(logEventConvert.isRDSHeartBeat("mysql", "ha_health_check"));
        Assert.assertTrue(logEventConvert.isRDSHeartBeat("__polardbx2__", "__system__mysql__heartbeat__"));
        Assert.assertTrue(logEventConvert.isRDSHeartBeat("zimian", "__drds__systable__leadership__"));
    }

    @Test
    public void testParseRowsQueryEvent_ValidCharset() {
        // 测试正常使用有效字符集的情况
        logEventConvert.filterQueryDml = false;
        logEventConvert.filterRowsQuery = false;
        logEventConvert.setCharset("UTF-8");

        RowsQueryLogEvent event = Mockito.mock(RowsQueryLogEvent.class);
        LogHeader header = Mockito.mock(LogHeader.class);

        Mockito.when(event.getRowsQuery()).thenReturn("INSERT INTO test_table VALUES (1, 'test')");
        Mockito.when(event.getHeader()).thenReturn(header);
        Mockito.when(header.getWhen()).thenReturn(1234567890L);
        Mockito.when(header.getLogPos()).thenReturn(100L);
        Mockito.when(header.getServerId()).thenReturn(1L);
        Mockito.when(header.getEventLen()).thenReturn(100);

        MySQLDBMSEvent result = logEventConvert.parseRowsQueryEvent(event);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getDbmsEventPayload());
    }

    @Test(expected = CanalParseException.class)
    public void testParseRowsQueryEvent_UnsupportedCharset() {
        // 测试使用不支持的字符集时抛出异常
        logEventConvert.filterQueryDml = false;
        logEventConvert.filterRowsQuery = false;
        logEventConvert.setCharset("invalid-charset");

        RowsQueryLogEvent event = Mockito.mock(RowsQueryLogEvent.class);
        LogHeader header = Mockito.mock(LogHeader.class);

        Mockito.when(event.getRowsQuery()).thenReturn("INSERT INTO test_table VALUES (1, 'test')");
        Mockito.when(event.getHeader()).thenReturn(header);

        logEventConvert.parseRowsQueryEvent(event);
    }

    @Test
    public void testParseAnnotateRowsEvent_ValidCharset() {
        // 测试正常使用有效字符集的情况
        logEventConvert.filterQueryDml = false;
        logEventConvert.setCharset("UTF-8");

        AnnotateRowsEvent event = Mockito.mock(AnnotateRowsEvent.class);
        LogHeader header = Mockito.mock(LogHeader.class);

        // 模拟返回 ISO-8859-1 编码的查询字符串
        Mockito.when(event.getRowsQuery()).thenReturn("INSERT INTO test_table VALUES (1, 'test')");
        Mockito.when(event.getHeader()).thenReturn(header);
        Mockito.when(header.getWhen()).thenReturn(1234567890L);
        Mockito.when(header.getLogPos()).thenReturn(100L);
        Mockito.when(header.getServerId()).thenReturn(1L);
        Mockito.when(header.getEventLen()).thenReturn(100);

        MySQLDBMSEvent result = logEventConvert.parseAnnotateRowsEvent(event);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getDbmsEventPayload());
    }

    @Test(expected = CanalParseException.class)
    public void testParseAnnotateRowsEvent_UnsupportedCharset() {
        // 测试使用不支持的字符集时抛出异常
        logEventConvert.filterQueryDml = false;
        logEventConvert.setCharset("invalid-charset");

        AnnotateRowsEvent event = Mockito.mock(AnnotateRowsEvent.class);
        LogHeader header = Mockito.mock(LogHeader.class);

        Mockito.when(event.getRowsQuery()).thenReturn("INSERT INTO test_table VALUES (1, 'test')");
        Mockito.when(event.getHeader()).thenReturn(header);
        Mockito.when(header.getWhen()).thenReturn(1234567890L);

        logEventConvert.parseAnnotateRowsEvent(event);
    }
}