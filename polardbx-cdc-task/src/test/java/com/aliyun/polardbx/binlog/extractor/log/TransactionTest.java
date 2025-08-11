/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.RuntimeContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.QueryLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.fetcher.FileLogFetcher;
import com.aliyun.polardbx.binlog.canal.core.ddl.ThreadRecorder;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.domain.po.CdcSyncPointMeta;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.extractor.DefaultOutputMergeSourceHandler;
import com.aliyun.polardbx.binlog.format.FormatDescriptionEvent;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.protocol.TxnFlag;
import com.aliyun.polardbx.binlog.protocol.TxnToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.service.CdcSyncPointMetaService;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.common.collect.Lists;
import com.google.protobuf.ByteString;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.mockito.Answers.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TransactionTest extends BaseTest {

    @Test
    public void testTraceIdOrder() throws Exception {
        testInternal();
    }

    private void testInternal() throws Exception {
        FileLogFetcher fetcher = new FileLogFetcher();
        fetcher.open(new File(TransactionTest.class.getClassLoader().getResource("binlog/mysql_bin.1").toURI()), 4);
        LogDecoder logDecoder = new LogDecoder(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("mysql_bin.1", 0));
        logContext.setServerCharactorSet(new ServerCharactorSet());

        LogEvent lastLogEvent = null;
        Transaction transaction = null;
        RuntimeContext runtimeContext = new RuntimeContext(new ThreadRecorder("pxc-xxx"));
        final List<String> traceIdList = new ArrayList<>();
        final List<String> compareList =
            Lists.newArrayList("00000000030000000000", "00000000030000000000", "00000000030000000000",
                "00000000030000000000", "00000000030000000000", "00000000030000000000", "00000000030000000000",
                "00000000030000000000");

        while (fetcher.fetch()) {
            LogEvent logEvent = logDecoder.decode(fetcher.buffer(), logContext);
            if (logEvent == null) {
                continue;
            }

            if (logEvent.getHeader().getType() == LogEvent.FORMAT_DESCRIPTION_EVENT) {
                lastLogEvent = logEvent;
                continue;
            }

            if (lastLogEvent != null && lastLogEvent.getHeader().getType() == LogEvent.FORMAT_DESCRIPTION_EVENT) {
                transaction = new Transaction(null, logEvent, runtimeContext) {
                    String lastTraceId;
                    int eventCount;

                    @Override
                    void buildBuffer() {
                    }

                    @Override
                    void addTxnBuffer(LogEvent logEvent) {
                        if (StringUtils.isNotBlank(lastTraceId) && logEvent.getTrace().compareTo(lastTraceId) < 0) {
                            throw new PolardbxException(
                                "detected disorderly traceId，current traceId is " + logEvent.getTrace()
                                    + ",last traceId is " + lastTraceId);
                        }
                        lastTraceId = logEvent.getTrace();
                        eventCount++;
                        traceIdList.add(logEvent.getTrace());
                    }

                    @Override
                    public int getEventCount() {
                        return eventCount;
                    }
                };
            } else {
                transaction.processEvent(logEvent, runtimeContext);
            }

            lastLogEvent = logEvent;
        }

        Assert.assertEquals(compareList, traceIdList);
    }

    @Test
    public void testSyncPoint() throws Exception {
        FileLogFetcher fetcher = new FileLogFetcher();
        fetcher.open(new File(TransactionTest.class.getClassLoader().getResource("binlog/mysql_bin.2").toURI()), 985);
        LogDecoder logDecoder = new LogDecoder(LogEvent.START_EVENT_V3, LogEvent.ENUM_END_EVENT);
        LogContext logContext = new LogContext();
        logContext.setLogPosition(new LogPosition("mysql_bin.2", 0));
        logContext.setServerCharactorSet(new ServerCharactorSet());
        // FORMAT_DESCRIPTION_EVENT
        fetcher.fetch();
        logDecoder.decode(fetcher.buffer(), logContext);
        // xa start
        fetcher.fetch();
        LogEvent xaStartEvent = logDecoder.decode(fetcher.buffer(), logContext);
        RuntimeContext runtimeContext = new RuntimeContext(new ThreadRecorder("pxc-xxx"));
        Transaction transaction = new Transaction(null, xaStartEvent, runtimeContext) {
            @Override
            void buildBuffer() {
            }
        };
        // table mapping
        LogEvent tableMappingEvent = logDecoder.decode(fetcher.buffer(), logContext);
        transaction.processEvent(tableMappingEvent, runtimeContext);
        // write rows
        LogEvent writeRowsEvent = logDecoder.decode(fetcher.buffer(), logContext);
        try (MockedStatic<SpringContextHolder> springContextHolderMockedStatic =
            Mockito.mockStatic(SpringContextHolder.class, CALLS_REAL_METHODS)) {
            CdcSyncPointMetaService service = mock(CdcSyncPointMetaService.class);
            when(getObject(CdcSyncPointMetaService.class)).thenReturn(service);
            CdcSyncPointMeta meta = new CdcSyncPointMeta();
            meta.setValid(1);
            meta.setId("test-id");
            Optional<CdcSyncPointMeta> record = Optional.of(meta);
            AtomicInteger atomicInteger = new AtomicInteger(0);
            when(service.selectById(Mockito.anyString())).then(
                invocation -> {
                    if (atomicInteger.getAndIncrement() <= 0) {
                        return Optional.empty();
                    }
                    return record;
                }
            );

            transaction.processEvent(writeRowsEvent, runtimeContext);
        }
        Assert.assertTrue(transaction.isSyncPoint());
        Map<String, String> extra = JSON.parseObject(
            transaction.getSyncPointExtra(),
            new TypeReference<Map<String, String>>() {
            }
        );
        Assert.assertTrue(extra.containsKey("tableId"));
        Assert.assertEquals("184", extra.get("tableId"));
        System.out.println(transaction.getSyncPointExtra());

        TxnToken.Builder txnTokenBuilder = TxnToken.newBuilder()
            .setPartitionId(transaction.getPartitionId())
            .setTso("1")
            .setTxnSize(1024)
            .setTxnId(0L)
            .setType(TxnType.DML)
            .setSchema("")
            .setTsoTransaction(true)
            .setXaTxn(true)
            .setTxnFlag(TxnFlag.NORMAL);

        MergeSource mergeSource = mock(MergeSource.class);
        Storage storage = mock(Storage.class);
        DefaultOutputMergeSourceHandler outputMergeSourceHandler =
            new DefaultOutputMergeSourceHandler(mergeSource, storage);
        outputMergeSourceHandler.process4SyncPoint(txnTokenBuilder, transaction);
        Assert.assertSame(TxnType.SYNC_POINT, txnTokenBuilder.getType());
        try (MockedStatic<ByteString> byteStringMockedStatic = Mockito.mockStatic(ByteString.class)) {
            byteStringMockedStatic.when(() -> ByteString.copyFrom(Mockito.any(byte[].class)))
                .thenThrow(new RuntimeException("test-xxx"));
            outputMergeSourceHandler.process4SyncPoint(txnTokenBuilder, transaction);
        }
    }

    @Test
    @SneakyThrows
    public void testProcessCdcInternalDDL() {
        FormatDescriptionLogEvent fdle = mock(FormatDescriptionLogEvent.class);
        FormatDescriptionEvent fde = mock(FormatDescriptionEvent.class);
        RuntimeContext rc = mock(RuntimeContext.class);
        TransEntity entity = mock(TransEntity.class);
        DDLExtInfo ddlExtInfo = mock(DDLExtInfo.class);
        when(ddlExtInfo.getGroupName()).thenReturn("2");

        when(rc.getStorageHashCode()).thenReturn("1");
        DDLRecord ddlRecord =
            new DDLRecord(1L, 1L, "FLUSH_LOGS", "test", "test", "CREATE TABLE test", "test", 1, ddlExtInfo);

        Transaction transaction = new Transaction(null, fdle, fde, rc);
        transaction.setEntity(entity);
        boolean res = transaction.processCdcInternalDDL(ddlRecord);
        Assert.assertTrue(res);
    }
}
