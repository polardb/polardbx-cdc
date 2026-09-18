/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMetaCache;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;
import com.aliyun.polardbx.rpl.filter.BaseFilter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_DDL_PARSE_ERROR_PROCESS_MODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class LogEventConvertReplicaTest {

    private BaseFilter filter;
    private TableMetaCache tableMetaCache;
    private LogEventConvert converter;
    private QueryLogEvent event;
    private MockedStatic<DynamicApplicationConfig> config;

    @Before
    public void setUp() {
        config = Mockito.mockStatic(DynamicApplicationConfig.class);
        config.when(() -> DynamicApplicationConfig.getBoolean(any(String.class))).thenReturn(false);
        config.when(() -> DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_DDL_STRIP_LEADING_COMMENTS))
            .thenReturn(true);
        config.when(() -> DynamicApplicationConfig.getInt(any(String.class))).thenReturn(0);
        Assert.assertTrue(DynamicApplicationConfig.getBoolean(ConfigKeys.RPL_DDL_STRIP_LEADING_COMMENTS));
        Assert.assertEquals(0, DynamicApplicationConfig.getInt(RPL_DDL_PARSE_ERROR_PROCESS_MODE).intValue());

        filter = mock(BaseFilter.class);
        tableMetaCache = mock(TableMetaCache.class);
        converter = new LogEventConvert(null, filter, new BinlogPosition("mysql-bin.000001", "4"), null);
        converter.tableMetaCache = tableMetaCache;

        event = mock(QueryLogEvent.class);
        LogHeader header = mock(LogHeader.class);
        when(event.getQuery()).thenReturn("/* audit */ /*+TDDL:CMD_EXTRA(TEST=true)*/ ALTER TABLE t ADD COLUMN c INT");
        when(event.getDbName()).thenReturn("src");
        when(event.getServerId()).thenReturn(88L);
        when(event.getHeader()).thenReturn(header);
        when(header.getWhen()).thenReturn(123L);
        when(header.getLogPos()).thenReturn(100L);
        when(header.getServerId()).thenReturn(88L);
        when(header.getEventLen()).thenReturn(64);
        when(filter.ignoreEventByTso(any())).thenReturn(false);
        when(filter.getRewriteDb(eq("src"), any())).thenReturn("dst");
        when(filter.ignoreEvent(eq("dst"), eq("t"), any(), eq(88L))).thenReturn(true);
    }

    @After
    public void tearDown() {
        config.close();
    }

    @Test
    public void serverIdFilteredDdl_RefreshesMetaAndReturnsRefreshOnlyEvent() {
        when(filter.isFilteredByServerId(88L)).thenReturn(true);

        MySQLDBMSEvent result = converter.parseQueryEvent(event, false);

        Assert.assertNotNull(result);
        Assert.assertTrue(result.getDbmsEventPayload() instanceof DefaultQueryLog);
        DefaultQueryLog queryLog = (DefaultQueryLog) result.getDbmsEventPayload();
        Assert.assertTrue(queryLog.isSchemaRefreshOnly());
        Assert.assertEquals("dst", queryLog.getSchema());
        Assert.assertEquals(event.getQuery(), queryLog.getQuery());

        verify(tableMetaCache).apply(any(BinlogPosition.class), eq("dst"),
            argThat(sql -> sql.startsWith("/*+TDDL:") && !sql.contains("audit")));
        verify(filter).isFilteredByServerId(88L);
    }

    @Test
    public void nonServerFilter_StillRefreshesMetaBeforeDroppingDdl() {
        when(filter.isFilteredByServerId(anyLong())).thenReturn(false);

        Assert.assertNull(converter.parseQueryEvent(event, false));

        verify(tableMetaCache).apply(any(BinlogPosition.class), eq("dst"), any(String.class));
        verify(filter).isFilteredByServerId(88L);
    }

}
