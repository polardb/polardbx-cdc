/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.TimelineEnvConfig;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.LogContext;
import com.aliyun.polardbx.binlog.canal.binlog.LogDecoder;
import com.aliyun.polardbx.binlog.canal.binlog.LogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.LogPosition;
import com.aliyun.polardbx.binlog.canal.binlog.event.FormatDescriptionLogEvent;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsQueryLogEvent;
import com.aliyun.polardbx.binlog.canal.core.model.ServerCharactorSet;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.protocol.TxnFlag;
import com.aliyun.polardbx.binlog.protocol.TxnMergedToken;
import com.aliyun.polardbx.binlog.protocol.TxnType;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;

@Slf4j
public class LogFileGeneratorTest1 extends BaseTest {
    private static final String rootPath = "binlog_test_write_config_change";
    private static final String binlogFileName = "binlog.000001";

    @Test
    public void testArchiveServerIdCheck() {
        LogFileGenerator generator = Mockito.mock(LogFileGenerator.class);
        TxnMergedToken currentToken =
                TxnMergedToken.newBuilder().setTxnFlag(TxnFlag.ARCHIVE).setType(TxnType.DML).build();
        Mockito.when(generator.serverIdCheckFailed(currentToken)).thenCallRealMethod();
        Assert.assertFalse(generator.serverIdCheckFailed(currentToken));
    }

    @Test
    public void testNormalServerIdCheckFailed() {
        LogFileGenerator generator = Mockito.mock(LogFileGenerator.class);
        TxnMergedToken currentToken =
                TxnMergedToken.newBuilder().setTxnFlag(TxnFlag.NORMAL).setType(TxnType.DML).build();
        Mockito.when(generator.serverIdCheckFailed(currentToken)).thenCallRealMethod();
        Assert.assertTrue(generator.serverIdCheckFailed(currentToken));
    }

    @Test
    public void testNeedServerIdCheckNormal() {
        LogFileGenerator generator = Mockito.mock(LogFileGenerator.class);
        TxnMergedToken currentToken =
                TxnMergedToken.newBuilder().setTxnFlag(TxnFlag.NORMAL).setType(TxnType.DML).build();
        Mockito.when(generator.needCheckServerId(currentToken)).thenCallRealMethod();
        Assert.assertTrue(generator.needCheckServerId(currentToken));
    }

    @Test
    public void testNeedServerIdCheckArchive() {
        LogFileGenerator generator = Mockito.mock(LogFileGenerator.class);
        TxnMergedToken currentToken =
                TxnMergedToken.newBuilder().setTxnFlag(TxnFlag.ARCHIVE).setType(TxnType.DML).build();
        Mockito.when(generator.needCheckServerId(currentToken)).thenCallRealMethod();
        Assert.assertFalse(generator.needCheckServerId(currentToken));
    }

    @Test
    public void testWriteConfigChange() throws IOException {
        // prepare

        LogFileManager manager = Mockito.mock(LogFileManager.class);
        TimelineEnvConfig timelineEnvConfig = Mockito.mock(TimelineEnvConfig.class);
        ExecutionConfig executionConfig = new ExecutionConfig();
        executionConfig.setSources(new ArrayList<>());
        executionConfig.setReservedMemMb(0);
        LogFileGenerator generator =
            new LogFileGenerator(manager, 4096, false, null, 1000, 1024, "Dumper-1", TaskType.Dumper, "group_test",
                "stream_test", executionConfig);

        File file = new File(rootPath + "/" + binlogFileName);
        if (file.exists()) {
            if (!file.delete()) {
                throw new IOException("delete file failed: " + rootPath + "/" + binlogFileName);
            }
        }

        if (!file.getParentFile().exists()) {
            if (file.getParentFile().mkdirs()) {
                System.out.println("create dir: " + rootPath);
            } else {
                throw new IOException("create file failed: " + rootPath + "/" + binlogFileName);
            }
        }

        if (!file.createNewFile()) {
            throw new IOException("create file failed: " + rootPath + "/" + binlogFileName);
        }

        generator.setCurrentServerId(231886L);
        generator.setBinlogFile(new BinlogFile(file, "rw", 1024, 1024, false, null));
        generator.setCurrentToken(TxnMergedToken.newBuilder().setTso("0").build());
        generator.setTimelineEnvConfig(timelineEnvConfig);

        // write config change event.
        generator.writeConfigChangeAsTxn();

        /*
          decode the binlog, the content should be:
          query_event: begin;
          rows_query_event: "# CONFIG CHANGE TXN"
          xid_event: commit;
          rows_query_event: "CTS::<tso>::ConfigChange"
         */
        try (RandomAccessFile raf = new RandomAccessFile(rootPath + "/" + binlogFileName, "r")) {
            byte[] data = new byte[2048];
            raf.read(data);
            LogContext context = new LogContext();
            FormatDescriptionLogEvent formatDescriptionLogEvent =
                new FormatDescriptionLogEvent(4, LogEvent.BINLOG_CHECKSUM_ALG_CRC32);
            context.setFormatDescription(formatDescriptionLogEvent);
            context.setServerCharactorSet(new ServerCharactorSet());
            context.setLogPosition(new LogPosition("binlog.000001", 0));
            LogBuffer buffer = new LogBuffer(data, 0, 2048);
            LogDecoder decoder = new LogDecoder(0, 165);
            LogEvent event = decoder.decode(buffer, context);
            Assert.assertNotNull(event);
            Assert.assertEquals(event.getHeader().getType(), LogEvent.QUERY_EVENT);
            event = decoder.decode(buffer, context);
            Assert.assertNotNull(event);
            Assert.assertEquals(event.getHeader().getType(), LogEvent.ROWS_QUERY_LOG_EVENT);
            RowsQueryLogEvent rowsQueryLogEvent = (RowsQueryLogEvent) event;
            Assert.assertEquals(rowsQueryLogEvent.getRowsQuery(), "# CONFIG CHANGE TXN");
            event = decoder.decode(buffer, context);
            Assert.assertNotNull(event);
            Assert.assertEquals(event.getHeader().getType(), LogEvent.XID_EVENT);
            event = decoder.decode(buffer, context);
            Assert.assertNotNull(event);
            Assert.assertEquals(event.getHeader().getType(), LogEvent.ROWS_QUERY_LOG_EVENT);
            rowsQueryLogEvent = (RowsQueryLogEvent) event;
            Assert.assertEquals(rowsQueryLogEvent.getRowsQuery(), "CTS::0::ConfigChange");
        }

    }

    @Test
    public void testExtractServerIdFromTraceId() {
        LogFileGenerator generator = Mockito.mock(LogFileGenerator.class);
        Mockito.when(generator.extractServerIdFromTraceId(Mockito.anyString())).thenCallRealMethod();
        String normalTrace = "/*DRDS /127.0.0.1/197122bf23800002-4/0/0/ */";
        long serverId = generator.extractServerIdFromTraceId(normalTrace);
        Assert.assertEquals(0, serverId);
        normalTrace = "/*DRDS /127.0.0.1/197122bf23800002-4/0/123/ */";
        serverId = generator.extractServerIdFromTraceId(normalTrace);
        Assert.assertEquals(123, serverId);
        normalTrace = "/*DRDS /127.0.0.1/197122bf23800002-4/0/456/3306/db1/tb1/ */";
        serverId = generator.extractServerIdFromTraceId(normalTrace);
        Assert.assertEquals(456, serverId);
    }
}
