/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.handle;

import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.GcnLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashSet;
import java.util.Set;

public class BinarySearchTsoEventHandleTest {
    @Test
    public void testInterestEvents(){
        Set<Integer> expectedFlagSet = new HashSet<>();
        expectedFlagSet.add(LogEvent.START_EVENT_V3);
        expectedFlagSet.add(LogEvent.ROTATE_EVENT);
        expectedFlagSet.add(LogEvent.SEQUENCE_EVENT);
        expectedFlagSet.add(LogEvent.GCN_EVENT);
        expectedFlagSet.add(LogEvent.QUERY_EVENT);
        expectedFlagSet.add(LogEvent.FORMAT_DESCRIPTION_EVENT);
        AuthenticationInfo authenticationInfo = Mockito.mock(AuthenticationInfo.class);
        BinarySearchTsoEventHandle handle = new BinarySearchTsoEventHandle(0L, "0", authenticationInfo);
        Set<Integer> interestEvents = handle.interestEvents();
        Assert.assertEquals(expectedFlagSet, interestEvents);
    }

    private GcnLogEvent mockGcnEvent(long tso){
        GcnLogEvent logEvent = Mockito.mock(GcnLogEvent.class);
        Mockito.when(logEvent.getGcn()).thenReturn(tso);
        LogHeader logHeader = new LogHeader(LogEvent.GCN_EVENT);
        Mockito.when(logEvent.getHeader()).thenReturn(logHeader);
        Mockito.when(logEvent.getFlag()).thenReturn(0x00000004);
        return logEvent;
    }

    private QueryLogEvent mockQueryLogEvent(String query){
        QueryLogEvent logEvent = Mockito.mock(QueryLogEvent.class);
        LogHeader logHeader = new LogHeader(LogEvent.QUERY_EVENT);
        Mockito.when(logEvent.getQuery()).thenReturn(query);
        Mockito.when(logEvent.getHeader()).thenReturn(logHeader);
        return logEvent;
    }

    @Test
    public void testStartReset(){
        AuthenticationInfo authenticationInfo = Mockito.mock(AuthenticationInfo.class);
        Mockito.when(authenticationInfo.getCharset()).thenReturn("utf-8");
        BinarySearchTsoEventHandle handle = new BinarySearchTsoEventHandle(101, "101", authenticationInfo);
        handle.setEndPosition(new BinlogPosition("test", 10000L, -1, -1));
        LogPosition position = new LogPosition("test", 4);
        QueryLogEvent startEvent = mockQueryLogEvent("XA START X'647264732d313937313531393430613434333030314063386636643665653762613038653433',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1");
        handle.handle(startEvent, position);
        handle.handle(mockGcnEvent(100L), position);
        QueryLogEvent commitEvent = mockQueryLogEvent("XA COMMIT X'647264732d313937313531393430613434333030314063386636643665653762613038653433',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1");
        handle.handle(commitEvent, position);
        Assert.assertEquals(100L, handle.getMinTSO());
        Assert.assertEquals(100L, handle.getMaxTSO());
        handle.onStart();
        Assert.assertEquals(-1, handle.getMinTSO());
        Assert.assertEquals(-1, handle.getMaxTSO());
    }

    @Test
    public void testStartBeforeFindReset(){
        AuthenticationInfo authenticationInfo = Mockito.mock(AuthenticationInfo.class);
        Mockito.when(authenticationInfo.getCharset()).thenReturn("utf-8");
        BinarySearchTsoEventHandle handle = new BinarySearchTsoEventHandle(101,"101", authenticationInfo);
        handle.setEndPosition(new BinlogPosition("test", 10000L, -1, -1));
        LogPosition position = new LogPosition("test", 4);
        QueryLogEvent startEvent = mockQueryLogEvent("XA START X'647264732d313937313531393430613434333030314063386636643665653762613038653433',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1");
        handle.handle(startEvent, position);
        handle.handle(mockGcnEvent(100L), position);
        QueryLogEvent commitEvent = mockQueryLogEvent("XA COMMIT X'647264732d313937313531393430613434333030314063386636643665653762613038653433',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1");
        handle.handle(commitEvent, position);
        Assert.assertEquals(100L, handle.getMinTSO());
        Assert.assertEquals(100L, handle.getMaxTSO());
        handle.onStart();
        handle.handle(mockGcnEvent(102), position);
        Assert.assertFalse(handle.needCheckLossStart());
        Assert.assertNull(handle.searchResult());
    }

    @Test
    public void testStartAfterFindReset(){
        AuthenticationInfo authenticationInfo = Mockito.mock(AuthenticationInfo.class);
        Mockito.when(authenticationInfo.getCharset()).thenReturn("utf-8");
        BinarySearchTsoEventHandle handle = new BinarySearchTsoEventHandle(101, "101",authenticationInfo);
        handle.setEndPosition(new BinlogPosition("test", 10000L, -1, -1));
        LogPosition position = new LogPosition("test", 4);
        QueryLogEvent startEvent = mockQueryLogEvent("XA START X'647264732d313937313531393430613434333030314063386636643665653762613038653433',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1");
        handle.handle(startEvent, position);
        handle.handle(mockGcnEvent(100L), position);
        QueryLogEvent commitEvent = mockQueryLogEvent("XA COMMIT X'647264732d313937313531393430613434333030314063386636643665653762613038653433',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1");
        handle.handle(commitEvent, position);
        Assert.assertEquals(100L, handle.getMinTSO());
        Assert.assertEquals(100L, handle.getMaxTSO());
        handle.handle(mockGcnEvent(102), position);
        handle.onStart();
        Assert.assertEquals(-1, handle.getMinTSO());
        Assert.assertEquals(-1, handle.getMaxTSO());
        Assert.assertFalse(handle.needCheckLossStart());
        Assert.assertNotNull(handle.searchResult());
    }

    @Test
    public void testTranInTwoFileFindReset(){
        AuthenticationInfo authenticationInfo = Mockito.mock(AuthenticationInfo.class);
        Mockito.when(authenticationInfo.getCharset()).thenReturn("utf-8");
        BinarySearchTsoEventHandle handle = new BinarySearchTsoEventHandle(101,"101", authenticationInfo);
        handle.setEndPosition(new BinlogPosition("test", 10000L, -1, -1));
        LogPosition position = new LogPosition("test", 4);
        handle.handle(mockGcnEvent(100L), position);
        QueryLogEvent commitEvent = mockQueryLogEvent("XA COMMIT X'647264732d313937313531393430613434333030314063386636643665653762613038653433',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1");
        handle.handle(commitEvent, position);
        Assert.assertEquals(100L, handle.getMinTSO());
        Assert.assertEquals(100L, handle.getMaxTSO());
        handle.handle(mockGcnEvent(102), position);
        handle.onStart();
        Assert.assertEquals(-1, handle.getMinTSO());
        Assert.assertEquals(-1, handle.getMaxTSO());
        Assert.assertTrue(handle.needCheckLossStart());
        Assert.assertNull(handle.searchResult());

        QueryLogEvent startEvent = mockQueryLogEvent("XA START X'647264732d313937313531393430613434333030314063386636643665653762613038653433',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1");
        handle.handle(startEvent, position);
        Assert.assertFalse(handle.needCheckLossStart());
        Assert.assertNotNull(handle.searchResult());
    }

    @Test
    public void testEndOfFile(){
        AuthenticationInfo authenticationInfo = Mockito.mock(AuthenticationInfo.class);
        BinarySearchTsoEventHandle handle = new BinarySearchTsoEventHandle(101, "101", authenticationInfo);
        handle.setEndPosition(new BinlogPosition("test", 10000L, -1, -1));
        LogPosition position = new LogPosition("test", 10000L);
        handle.handle(mockGcnEvent(100L), position);
        Assert.assertEquals(-1, handle.getMinTSO());
        Assert.assertEquals(-1, handle.getMaxTSO());
    }

    @Test
    public void testRegion(){
        AuthenticationInfo authenticationInfo = Mockito.mock(AuthenticationInfo.class);
        BinarySearchTsoEventHandle handle = new BinarySearchTsoEventHandle(101, "101", authenticationInfo);
        handle.setEndPosition(new BinlogPosition("test", 10000L, -1, -1));
        LogPosition position = new LogPosition("test", 4L);
        handle.handle(mockGcnEvent(102L), position);
        handle.handle(mockGcnEvent(100L), position);
        Assert.assertEquals(100, handle.getMinTSO());
        Assert.assertEquals(102, handle.getMaxTSO());
        Assert.assertEquals("[100 , 102]", handle.region());
        Assert.assertEquals("test", handle.getLastSearchFile());
    }
}
