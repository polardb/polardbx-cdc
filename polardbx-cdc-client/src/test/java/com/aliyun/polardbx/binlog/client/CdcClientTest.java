/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client;

import com.aliyun.polardbx.binlog.client.filter.LogBufferFilter;
import com.aliyun.polardbx.binlog.client.handler.LogEventPreHandler;
import com.aliyun.polardbx.binlog.client.handler.RowTableNameFilter;
import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class CdcClientTest {
    @Test
    @SneakyThrows
    public void testSetVariables() {
        IMetaDBDataSourceProvider provider = Mockito.mock(IMetaDBDataSourceProvider.class);
        CdcClientParser cdcClientParser = Mockito.mock(CdcClientParser.class, InvocationOnMock::callRealMethod);
        cdcClientParser.filter = new RowTableNameFilter();
        cdcClientParser.logBufferFilter = new LogBufferFilter(null, true);
        CdcClient cdcClient = new CdcClient(provider);
        cdcClient.setCdcClientParser(cdcClientParser);
        Set<String> tableSet = new HashSet<>();
        tableSet.add("zimian.pm_user_bill");
        cdcClient.setAcceptTable(tableSet);
        Assert.assertEquals(tableSet, cdcClientParser.logBufferFilter.getTableNameSet());
        Assert.assertTrue(cdcClientParser.logBufferFilter.isWhiteListMode());
        cdcClient.setIgnoreTable(tableSet);
        Assert.assertFalse(cdcClientParser.logBufferFilter.isWhiteListMode());
        cdcClient.setFilterOptimizeEnabled(false);
        Assert.assertFalse(cdcClientParser.logBufferFilter.isEnabled());

        LogEventPreHandler logEventPreHandler = new LogEventPreHandler();
        cdcClientParser.logEventPreHandler = logEventPreHandler;
        cdcClientParser.decode64Enabled = new AtomicBoolean(false);
        cdcClient.setDecode64Enabled(true);
        Assert.assertTrue(logEventPreHandler.getDecode64Enabled().get());
        Assert.assertTrue(cdcClientParser.decode64Enabled.get());
    }
}
