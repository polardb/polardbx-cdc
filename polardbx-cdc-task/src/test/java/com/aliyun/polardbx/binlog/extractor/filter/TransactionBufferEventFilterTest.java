/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.HandlerContext;
import com.aliyun.polardbx.binlog.canal.LogEventFilter;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.GcnLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.LogHeader;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.SequenceLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.XaPrepareLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.TransactionStorage;
import com.aliyun.polardbx.binlog.extractor.log.Transaction;
import com.aliyun.polardbx.binlog.extractor.log.TransactionGroup;
import com.aliyun.polardbx.binlog.storage.IteratorBuffer;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.TxnItemRef;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.CommonUtils;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.List;

@Slf4j
public class TransactionBufferEventFilterTest extends BaseTest {

    private QueryLogEvent mockQueryLog(String query, long pos) {
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
        QueryLogEvent event = mockQueryLog(
            "XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1",
            128L);
        filter.processCommit(event, context);
        Mockito.verify(event.getHeader(), Mockito.times(1)).getLogPos();
    }

    @Test(expected = PolardbxException.class)
    public void testLogLossCommitXAEventWith8Exception() throws Exception {
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter =
            new TransactionBufferEventFilter(storage, CommonUtils.generateTSO(100L, "0000", null));
        GcnLogEvent gcnLogEvent = Mockito.mock(GcnLogEvent.class);
        Mockito.when(gcnLogEvent.getGcn()).thenReturn(100L);
        Mockito.when(gcnLogEvent.getFlag()).thenReturn(0x00000004);
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        filter.onStart(context);
        QueryLogEvent event = mockQueryLog(
            "XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1",
            128L);
        filter.processGcn(gcnLogEvent, context);
        filter.processCommit(event, context);
        Mockito.verify(event.getHeader(), Mockito.times(1)).getLogPos();
    }

    @Test()
    public void testLogLossCommitXAEventWith8NoException() throws Exception {
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter =
            new TransactionBufferEventFilter(storage, CommonUtils.generateTSO(99L, "0000", null));
        GcnLogEvent gcnLogEvent = Mockito.mock(GcnLogEvent.class);
        Mockito.when(gcnLogEvent.getGcn()).thenReturn(98L);
        Mockito.when(gcnLogEvent.getFlag()).thenReturn(0x00000004);
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        filter.onStart(context);
        QueryLogEvent event = mockQueryLog(
            "XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1",
            128L);
        filter.processGcn(gcnLogEvent, context);
        filter.processCommit(event, context);
        Mockito.verify(event.getHeader(), Mockito.times(1)).getLogPos();
    }

    @Test(expected = PolardbxException.class)
    public void testLogLossCommitXAEventWithException() throws Exception {
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter =
            new TransactionBufferEventFilter(storage, CommonUtils.generateTSO(99L, "0000", null));
        SequenceLogEvent sequenceLogEvent = Mockito.mock(SequenceLogEvent.class);
        Mockito.when(sequenceLogEvent.getSequenceNum()).thenReturn(100L);
        Mockito.when(sequenceLogEvent.isCommitSequence()).thenReturn(true);
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        filter.onStart(context);
        QueryLogEvent event = mockQueryLog(
            "XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1",
            128L);
        filter.processSequence(sequenceLogEvent, context);
        filter.processCommit(event, context);
        Mockito.verify(event.getHeader(), Mockito.times(1)).getLogPos();
    }

    @Test()
    public void testLogLossCommitXAEventWithNoException() throws Exception {
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter =
            new TransactionBufferEventFilter(storage, CommonUtils.generateTSO(99L, "0000", null));
        SequenceLogEvent sequenceLogEvent = Mockito.mock(SequenceLogEvent.class);
        Mockito.when(sequenceLogEvent.getSequenceNum()).thenReturn(98L);
        Mockito.when(sequenceLogEvent.isCommitSequence()).thenReturn(true);
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        filter.onStart(context);
        QueryLogEvent event = mockQueryLog(
            "XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1",
            128L);
        filter.processSequence(sequenceLogEvent, context);
        filter.processCommit(event, context);
        Mockito.verify(event.getHeader(), Mockito.times(1)).getLogPos();
    }

    @Test
    public void testLogLossRollbackXAEvent() {
        Storage storage = Mockito.mock(Storage.class);
        TransactionBufferEventFilter filter = new TransactionBufferEventFilter(storage, "");
        HandlerContext context = new HandlerContext(new RtRecordFilter());
        context.setRuntimeContext(new RuntimeContext(new ThreadRecorder("")));
        filter.onStart(context);
        QueryLogEvent event = mockQueryLog(
            "XA ROLLBACK X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1",
            128L);
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
        QueryLogEvent beginEvent = mockQueryLog(
            "XA START X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1",
            128L);
        filter.processStart(beginEvent, context);
        QueryLogEvent commitEvent = mockQueryLog(
            "XA COMMIT X'647264732d313937313531393334613034343030304035366632346566393366636438633565',X'494e56454e544f52595f43454e5445525f5030303030335f47524f5550',1",
            928L);
        filter.processCommit(commitEvent, context);
        Mockito.verify(commitEvent.getHeader(), Mockito.times(0)).getLogPos();

        // A normal transaction leaves TransactionStorage in the thread-local commit listener. CDC single-group
        // transactions are not added to TransactionStorage at XA START, so a following one-phase XA PREPARE must
        // remain ignored instead of sending an orphan Commit through that listener.
        QueryLogEvent cdcSingleBeginEvent = mockQueryLog(
            "XA START X'647264732d316330643230326237333830333030314031',X'5f5f4344435f5f5f53494e474c455f47524f55504030303031',1",
            1024L);
        filter.processStart(cdcSingleBeginEvent, context);

        XaPrepareLogEvent prepareEvent = Mockito.mock(XaPrepareLogEvent.class);
        LogHeader prepareHeader = Mockito.mock(LogHeader.class);
        Mockito.when(prepareHeader.getType()).thenReturn(LogEvent.XA_PREPARE_LOG_EVENT);
        Mockito.when(prepareHeader.getLogPos()).thenReturn(2048L);
        Mockito.when(prepareEvent.getHeader()).thenReturn(prepareHeader);
        Mockito.when(prepareEvent.getLogPos()).thenReturn(2048L);
        Mockito.when(prepareEvent.isOnePhase()).thenReturn(true);
        filter.handle(prepareEvent, context);
    }

    @Test
    @Ignore
    @SneakyThrows
    public void testLocalDumpForReturning() {
        String fileName = "mysql-bin.011190";
        String path = "/Users/zm/Downloads/tmp/";
        FileLogFetcher fetcher = new FileLogFetcher();
        fetcher.open(path + fileName, 0);
        AuthenticationInfo authenticationInfo = new AuthenticationInfo();
        authenticationInfo.setCharset("utf-8");
        authenticationInfo.setStorageInstId("pxc-xdb-s-ffrjgdmnlv787ned50");
        LogDecoder decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
        LogContext lc = new LogContext(new FormatDescriptionLogEvent(7));
        lc.setServerCharactorSet(new ServerCharactorSet());
        lc.setLogPosition(new LogPosition(fileName, 0));
        Storage storage = Mockito.mock(Storage.class);
        ThreadRecorder recorder = new ThreadRecorder("pxc-xdb-s-ffrjgdmnlv787ned50");
        TransactionStorage transactionStorage = new TransactionStorage(recorder);
        TransactionBufferEventFilter txnBufferEventFilter = new TransactionBufferEventFilter(storage, "");
        HandlerContext context = new HandlerContext(txnBufferEventFilter);
        context.setNext(new HandlerContext(new LogTestTailFilter()));
        RuntimeContext runtimeContext = new RuntimeContext(recorder);
        runtimeContext.setAuthenticationInfo(authenticationInfo);
        context.setRuntimeContext(runtimeContext);
        txnBufferEventFilter.setTransactionStorage(transactionStorage);
        mockConfig(ConfigKeys.TASK_EXTRACT_LOSS_COMMIT_CHECK, "false");

        while (fetcher.fetch()) {
            LogEvent event = decoder.decode(fetcher, lc);
            txnBufferEventFilter.handle(event, context);
        }
    }

    static class LogTestTailFilter implements LogEventFilter<TransactionGroup> {
        private final LogDecoder decoder;
        private final LogContext lc;

        public LogTestTailFilter() {
            decoder = new LogDecoder(LogEvent.UNKNOWN_EVENT, LogEvent.ENUM_END_EVENT);
            lc = new LogContext(new FormatDescriptionLogEvent(7));
            lc.setServerCharactorSet(new ServerCharactorSet());
            lc.setLogPosition(new LogPosition("binlog.000001", 0));
        }

        @Override
        public void handle(TransactionGroup event, HandlerContext context) throws Exception {
            List<Transaction> transactionList = event.getTransactionList();
            long startEventLogPos = 0L;
            for (Transaction transaction : transactionList) {
                // start event 的logPos
                long pos = transaction.getStartLogPos();
                if (pos == startEventLogPos) {
                    IteratorBuffer iter = transaction.iterator();
                    while (iter.hasNext()) {
                        TxnItemRef ref = iter.next();
                        byte[] payload = ref.getEventData().getPayload().toByteArray();
                        LogEvent logEvent = decoder.decode(new LogBuffer(payload, 0, payload.length), lc);
                        if (logEvent instanceof RowsLogEvent) {
                            RowsLogEvent rowsLogEvent = (RowsLogEvent) logEvent;
                            log.warn(rowsLogEvent.printRowValues());
                        }
                    }
                }
            }
        }

        @Override
        public void onStart(HandlerContext context) {
        }

        @Override
        public void onStop() {
        }

        @Override
        public void onStartConsume(HandlerContext context) {
        }
    }

}
