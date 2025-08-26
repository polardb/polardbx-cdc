/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter;

import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.event.GcnLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import org.junit.Test;
import org.mockito.Mockito;

public class TransactionBufferEventFilterTest extends BaseTest {


    private QueryLogEvent mockQueryLog(String query, long pos){
        QueryLogEvent event = Mockito.mock(QueryLogEvent.class);
        Mockito.when(event.getQuery()).thenReturn(query);
        LogHeader header = Mockito.mock(LogHeader.class);
        Mockito.when(header.getLogPos()).thenReturn(pos);
        Mockito.when(event.getHeader()).thenReturn(header);
        return event;
    }
    @Test
    public void testLogLossCommitXAEvent() throws Exception {
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter = new TransactionBufferEventFilter(storage, "");
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        filter.onStart(context);
        QueryLogEvent event = mockQueryLog("XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1", 128L);
        filter.processCommit(event, context);
        Mockito.verify(event.getHeader(), Mockito.times(1)).getLogPos();
    }

    @Test(expected = PolardbxException.class)
    public void testLogLossCommitXAEventWith8Exception() throws Exception {
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter = new TransactionBufferEventFilter(storage, CommonUtils.generateTSO(100L, "0000", null));
        GcnLogEvent gcnLogEvent = Mockito.mock(GcnLogEvent.class);
        Mockito.when(gcnLogEvent.getGcn()).thenReturn(100L);
        Mockito.when(gcnLogEvent.getFlag()).thenReturn(0x00000004);
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        filter.onStart(context);
        QueryLogEvent event = mockQueryLog("XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1", 128L);
        filter.processGcn(gcnLogEvent, context);
        filter.processCommit(event, context);
        Mockito.verify(event.getHeader(), Mockito.times(1)).getLogPos();
    }

    @Test()
    public void testLogLossCommitXAEventWith8NoException() throws Exception {
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter = new TransactionBufferEventFilter(storage, CommonUtils.generateTSO(99L, "0000", null));
        GcnLogEvent gcnLogEvent = Mockito.mock(GcnLogEvent.class);
        Mockito.when(gcnLogEvent.getGcn()).thenReturn(98L);
        Mockito.when(gcnLogEvent.getFlag()).thenReturn(0x00000004);
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        filter.onStart(context);
        QueryLogEvent event = mockQueryLog("XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1", 128L);
        filter.processGcn(gcnLogEvent, context);
        filter.processCommit(event, context);
        Mockito.verify(event.getHeader(), Mockito.times(1)).getLogPos();
    }

    @Test(expected = PolardbxException.class)
    public void testLogLossCommitXAEventWithException() throws Exception {
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter = new TransactionBufferEventFilter(storage, CommonUtils.generateTSO(99L, "0000", null));
        SequenceLogEvent sequenceLogEvent = Mockito.mock(SequenceLogEvent.class);
        Mockito.when(sequenceLogEvent.getSequenceNum()).thenReturn(100L);
        Mockito.when(sequenceLogEvent.isCommitSequence()).thenReturn(true);
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        filter.onStart(context);
        QueryLogEvent event = mockQueryLog("XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1", 128L);
        filter.processSequence(sequenceLogEvent, context);
        filter.processCommit(event, context);
        Mockito.verify(event.getHeader(), Mockito.times(1)).getLogPos();
    }

    @Test()
    public void testLogLossCommitXAEventWithNoException() throws Exception {
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter = new TransactionBufferEventFilter(storage, CommonUtils.generateTSO(99L, "0000", null));
        SequenceLogEvent sequenceLogEvent = Mockito.mock(SequenceLogEvent.class);
        Mockito.when(sequenceLogEvent.getSequenceNum()).thenReturn(98L);
        Mockito.when(sequenceLogEvent.isCommitSequence()).thenReturn(true);
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        filter.onStart(context);
        QueryLogEvent event = mockQueryLog("XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1", 128L);
        filter.processSequence(sequenceLogEvent, context);
        filter.processCommit(event, context);
        Mockito.verify(event.getHeader(), Mockito.times(1)).getLogPos();
    }

    @Test
    public void testLogLossRollbackXAEvent(){
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter = new TransactionBufferEventFilter(storage, "");
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        filter.onStart(context);
        QueryLogEvent event = mockQueryLog("XA ROLLBACK X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1", 128L);
        filter.processRollback(event, context);
        Mockito.verify(event.getHeader(), Mockito.times(1)).getLogPos();
    }


    @Test
    public void testCommitXAEvent() throws Exception {
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter = new TransactionBufferEventFilter(storage, "");
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        RuntimeContext runtimeContext = new RuntimeContext(new ThreadRecorder(""));
        runtimeContext.setAuthenticationInfo(new AuthenticationInfo());
        runtimeContext.getAuthenticationInfo().setStorageInstId("test");
        context.setRuntimeContext(runtimeContext);
        filter.onStart(context);
        QueryLogEvent beginEvent = mockQueryLog("XA START X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1", 128L);
        filter.processStart(beginEvent, context);
        QueryLogEvent commitEvent = mockQueryLog("XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1", 928L);
        filter.processCommit(commitEvent, context);
        Mockito.verify(commitEvent.getHeader(), Mockito.times(0)).getLogPos();
    }
}
