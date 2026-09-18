/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.binlog.canal.exception.CanalParseException;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowsQueryLog;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author zm
 */
public class ImportLogEventConvertTest extends BaseTest {
    private final BinlogPosition binlogPosition = new BinlogPosition("mysql-bin.000001", "4");
    private ImportLogEventConvert importLogEventConvert;

    @Before
    public void setUp() {
        // 初始化ImportLogEventConvert实例
        importLogEventConvert = new ImportLogEventConvert(null, null, binlogPosition, null);
    }

    @Test
    public void testParseRowsQueryEvent_ValidCharset() {
        // 测试正常使用有效字符集的情况
        importLogEventConvert.setCharset("UTF-8");

        RowsQueryLogEvent event = Mockito.mock(RowsQueryLogEvent.class);
        LogHeader header = Mockito.mock(LogHeader.class);

        Mockito.when(event.getRowsQuery()).thenReturn("INSERT INTO test_table VALUES (1, 'test')");
        Mockito.when(event.getHeader()).thenReturn(header);
        Mockito.when(header.getWhen()).thenReturn(1234567890L);
        Mockito.when(header.getLogPos()).thenReturn(100L);
        Mockito.when(header.getServerId()).thenReturn(1L);
        Mockito.when(header.getEventLen()).thenReturn(100);
        Mockito.when(header.getType()).thenReturn(0);

        MySQLDBMSEvent result = importLogEventConvert.parseRowsQueryEvent(event);

        Assert.assertNotNull(result);
        Assert.assertNotNull(result.getDbmsEventPayload());
        Assert.assertTrue(result.getDbmsEventPayload() instanceof DefaultRowsQueryLog);

        DefaultRowsQueryLog rowsQueryLog = (DefaultRowsQueryLog) result.getDbmsEventPayload();
        Assert.assertEquals("INSERT INTO test_table VALUES (1, 'test')", rowsQueryLog.getRowsQuery());
        Assert.assertEquals(DBMSAction.ROWQUERY, rowsQueryLog.getAction());
    }

    @Test(expected = CanalParseException.class)
    public void testParseRowsQueryEvent_UnsupportedCharset() {
        // 测试使用不支持的字符集时抛出异常
        importLogEventConvert.setCharset("invalid-charset");

        RowsQueryLogEvent event = Mockito.mock(RowsQueryLogEvent.class);
        LogHeader header = Mockito.mock(LogHeader.class);

        Mockito.when(event.getRowsQuery()).thenReturn("INSERT INTO test_table VALUES (1, 'test')");
        Mockito.when(event.getHeader()).thenReturn(header);
        Mockito.when(header.getWhen()).thenReturn(1234567890L);
        Mockito.when(header.getLogPos()).thenReturn(100L);
        Mockito.when(header.getServerId()).thenReturn(1L);
        Mockito.when(header.getEventLen()).thenReturn(100);
        Mockito.when(header.getType()).thenReturn(0);

        importLogEventConvert.parseRowsQueryEvent(event);
    }
}