/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumBinlogChecksumAlg;
import com.aliyun.polardbx.binlog.filesys.CdcFileSystem;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.lock.LogFileLockManager;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.rpc.cdc.DumpStream;
import com.aliyun.polardbx.rpc.cdc.EventSplitMode;
import com.google.common.collect.ImmutableMap;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static com.aliyun.polardbx.binlog.CommonConstants.GROUP_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getLong;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

public class BinlogParallelSyncReaderTest {
    private final String path = BinlogFileTest.class.getClassLoader().getResource(".").getPath() + "binlog";

    @Test
    public void testParallelSyncBinlog() throws Exception {
        // test read binlog.000004 and binlog.000006
        {
            AtomicReference<DumpStream> stream = new AtomicReference<>(null);

            Thread thread = new Thread(() -> {
                try {
                    syncBinlog(stream, "binlog.000004", "2", "0", "test-0");
                } catch (Exception e) {
                    // ignore
                    e.printStackTrace();
                }
            }, "testParallelSyncBinlog-0");

            thread.start();

            if (null == stream.get()) {
                synchronized (stream) {
                    if (null == stream.get()) {
                        stream.wait(20 * 1000);
                    }
                }
            }

            thread.interrupt();

            File binlog0 = new File(path + File.separator + "binlog.000004");
            FileInputStream fis = new FileInputStream(binlog0);
            byte[] buf0 = new byte[(int) binlog0.length()];
            fis.read(buf0);
            fis.close();
            File binlog1 = new File(path + File.separator + "binlog.000006");
            fis = new FileInputStream(binlog1);
            byte[] buf1 = new byte[(int) binlog1.length()];
            fis.read(buf1);
            fis.close();

            // expected array
            byte[] expected = new byte[buf0.length + buf1.length - 8];
            System.arraycopy(buf0, 4, expected, 0, buf0.length - 4);
            System.arraycopy(buf1, 4, expected, buf0.length - 4, buf1.length - 4);

            Assert.assertNotNull(stream.get());
            Assert.assertArrayEquals(expected, stream.get().getPayload().toByteArray());
        }

        // test read binlog.000005 and binlog.000007
        {
            AtomicReference<DumpStream> stream = new AtomicReference<>(null);

            Thread thread = new Thread(() -> {
                try {
                    syncBinlog(stream, "binlog.000005", "2", "1", "test-1");
                } catch (Exception e) {
                    // ignore
                    e.printStackTrace();
                }
            }, "testParallelSyncBinlog-1");

            thread.start();

            if (null == stream.get()) {
                synchronized (stream) {
                    if (null == stream.get()) {
                        stream.wait(20 * 1000);
                    }
                }
            }

            thread.interrupt();

            File binlog0 = new File(path + File.separator + "binlog.000005");
            FileInputStream fis = new FileInputStream(binlog0);
            byte[] buf0 = new byte[(int) binlog0.length()];
            fis.read(buf0);
            fis.close();
            File binlog1 = new File(path + File.separator + "binlog.000007");
            fis = new FileInputStream(binlog1);
            byte[] buf1 = new byte[(int) binlog1.length()];
            fis.read(buf1);
            fis.close();

            // expected array
            byte[] expected = new byte[buf0.length + buf1.length - 8];
            System.arraycopy(buf0, 4, expected, 0, buf0.length - 4);
            System.arraycopy(buf1, 4, expected, buf0.length - 4, buf1.length - 4);

            Assert.assertNotNull(stream.get());
            Assert.assertArrayEquals(expected, stream.get().getPayload().toByteArray());
        }
    }

    private void syncBinlog(AtomicReference<DumpStream> stream, String fileName, String parallelism,
                            String parallelismId,
                            String trace) throws Exception {
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = Mockito.mockStatic(
            RuntimeLeaderElector.class);
            MockedStatic<DynamicApplicationConfig> dynamicApplicationConfigMock = Mockito.mockStatic(
                DynamicApplicationConfig.class);
            MockedStatic<SpringContextHolder> springContextHolderMockedStatic = Mockito.mockStatic(
                SpringContextHolder.class);
        ) {
            // static method mock
            runtimeLeaderElectorMock.when(
                    () -> RuntimeLeaderElector.isDumperMasterOrX(Mockito.anyLong(), Mockito.any(), Mockito.any()))
                .thenReturn(false);
            springContextHolderMockedStatic.when(() -> SpringContextHolder.getObject(Mockito.any(Class.class)))
                .then(invocation -> {
                    Class clazz = invocation.getArgument(0);
                    if (clazz == BinlogOssRecordMapperExtend.class) {
                        BinlogOssRecordMapperExtend mapper = Mockito.mock(BinlogOssRecordMapperExtend.class);
                        return mapper;
                    }
                    return null;
                });

            // config map
            final Map<String, String> stringConfigMap = ImmutableMap.of(
                ConfigKeys.BINLOG_DUMP_DOWNLOAD_PATH, path,
                ConfigKeys.BINLOG_DUMP_M_EVENT_CHECKSUM_ALG, "NONE"
            );
            final Map<String, Integer> intConfigMap = ImmutableMap.<String, Integer>builder()
                .put(ConfigKeys.BINLOG_DUMP_DOWNLOAD_WINDOW_SIZE, 3)
                .put(ConfigKeys.BINLOG_DUMP_DOWNLOAD_PARALLELISM_PER_FILE, 64)
                .put(ConfigKeys.BINLOG_SYNC_PACKET_SIZE, 1 * 1024 * 1024)
                .put(ConfigKeys.BINLOG_SYNC_READ_BUFFER_SIZE, 1 * 1024 * 1024)
                .put(ConfigKeys.BINLOG_BACKUP_UPLOAD_PART_SIZE, 1 * 1024 * 1024)
                .put(ConfigKeys.BINLOG_SYNC_CHECK_FILE_STATUS_INTERVAL_SECOND, 10000000)
                .build();
            final Map<String, Long> longConfigMap = ImmutableMap.of(
                ConfigKeys.BINLOG_DUMP_MASTER_HEARTBEAT_PERIOD, 1000000000000L,
                ConfigKeys.BINLOG_DUMP_DOWNLOAD_PART_SIZE, 10485760L
            );
            dynamicApplicationConfigMock.when(() -> getString(Mockito.anyString()))
                .then(invocation -> stringConfigMap.get(invocation.getArgument(0)));
            dynamicApplicationConfigMock.when(() -> getInt(Mockito.anyString()))
                .then(invocation -> intConfigMap.get(invocation.getArgument(0)));
            dynamicApplicationConfigMock.when(() -> getLong(Mockito.anyString()))
                .then(invocation -> longConfigMap.get(invocation.getArgument(0)));

            // run test here
            LogFileManager logFileManager = buildLogFileManager();

            // mock output stream
            TxnOutputStream<DumpStream> outputStream = Mockito.mock(TxnOutputStream.class);
            Mockito.when(outputStream.tryWait()).thenReturn(true);
            // store output stream in stream
            Mockito.doAnswer((Answer<Void>) invocation -> {
                DumpStream dumpStream = (DumpStream) invocation.getArguments()[0];
                stream.set(dumpStream);
                synchronized (stream) {
                    stream.notify();
                }
                return null;
            }).when(outputStream).onNext(Mockito.any());

            LogFileReader logFileReader = new LogFileReader(logFileManager);
            Map<String, String> ext = ImmutableMap.of(
                "parallelism", parallelism,
                "parallelism_id", parallelismId,
                "trace", trace
            );

            logFileReader.binlogSync(fileName, 4, EventSplitMode.CLIENT, null,
                ext, outputStream);
        }
    }

    @Test
    public void testParallelSyncDownloader() {
        try (
            MockedStatic<DynamicApplicationConfig> dynamicApplicationConfigMock = Mockito.mockStatic(
                DynamicApplicationConfig.class);
            MockedStatic<SpringContextHolder> springContextHolderMockedStatic = Mockito.mockStatic(
                SpringContextHolder.class);
        ) {
            // static method mock
            springContextHolderMockedStatic.when(() -> SpringContextHolder.getObject(Mockito.any(Class.class)))
                .then(invocation -> {
                    Class clazz = invocation.getArgument(0);
                    if (clazz == BinlogOssRecordMapperExtend.class) {
                        final BinlogOssRecordMapperExtend mapper = Mockito.mock(BinlogOssRecordMapperExtend.class);
                        List<BinlogOssRecord> records = new ArrayList<>();
                        for (int i = 1; i <= 7; i++) {
                            BinlogOssRecord record = new BinlogOssRecord();
                            record.setBinlogFile("binlog.00000" + i);
                            records.add(record);
                        }
                        Mockito.when(mapper.getRecordsForBinlogDump(Mockito.any(), Mockito.any(),
                            Mockito.any(), Mockito.any())).thenReturn(records);
                        return mapper;
                    }
                    return null;
                });

            // config map
            final Map<String, Integer> intConfigMap = ImmutableMap.of(
                ConfigKeys.BINLOG_BACKUP_UPLOAD_PART_SIZE, 1 * 1024 * 1024
            );
            dynamicApplicationConfigMock.when(() -> getInt(Mockito.anyString()))
                .then(invocation -> intConfigMap.get(invocation.getArgument(0)));

            // should download binlog.000001, binlog.000003, binlog.000005, ...
            BinlogParallelSyncDownloader binlogParallelSyncDownloader = new BinlogParallelSyncDownloader(
                buildLogFileManager(), path, 1024, "binlog.000001", 1000,
                null, null, 5, 10485760, 2, "trace-0"
            );

            binlogParallelSyncDownloader.getDownloadFileList("binlog.000001");
            // expected files: binlog.000001, binlog.000003, binlog.000005, binlog.000007
            // local files: binlog.000005, binlog.000007
            // to-be-downloaded files: binlog.000001, binlog.000003
            Assert.assertEquals(2, binlogParallelSyncDownloader.downloadQueue.size());
            Assert.assertEquals(1, (int) binlogParallelSyncDownloader.downloadQueue.poll());
            Assert.assertEquals(3, (int) binlogParallelSyncDownloader.downloadQueue.poll());

            // test invalid binlog file
            try {
                binlogParallelSyncDownloader.getDownloadFileList("binlog.000002");
            } catch (Exception e) {
                Assert.assertTrue(e.getMessage().contains(
                    "Get unexpected download file request, currentStartFile is binlog.000002, current seq is 2, initialStartFileSeq is 1, parallelism is 2"));
            }
        }
    }

    @Test
    public void testParallelSyncReader() throws IOException {
        try (MockedStatic<DynamicApplicationConfig> dynamicApplicationConfigMock = Mockito.mockStatic(
            DynamicApplicationConfig.class);
            MockedStatic<SpringContextHolder> springContextHolderMockedStatic = Mockito.mockStatic(
                SpringContextHolder.class)
        ) {
            springContextHolderMockedStatic.when(() -> SpringContextHolder.getObject(Mockito.any(Class.class)))
                .then(invocation -> null);

            BinlogParallelSyncReader reader = new BinlogParallelSyncReader(
                buildLogFileManager(), "binlog.000001", 4, EventSplitMode.CLIENT, 1024, 1024,
                EnumBinlogChecksumAlg.BINLOG_CHECKSUM_ALG_OFF, 2, 0, "test-0", null);

            Assert.assertTrue(reader.hasNext());
        }
    }

    private LogFileManager buildLogFileManager() {
        LogFileManager logFileManager = new LogFileManager();
        logFileManager.setTaskName("mock-task");
        logFileManager.setTaskType(TaskType.Dumper);
        logFileManager.setGroupName(GROUP_NAME_GLOBAL);
        logFileManager.setExecutionConfig(new ExecutionConfig());
        logFileManager.setBinlogRootPath(path);
        logFileManager.setBinlogFileSize(1024 * 1024);
        logFileManager.setDryRun(false);
        logFileManager.setFlushPolicy(FlushPolicy.FlushPerTxn);
        logFileManager.setFlushInterval(1000);
        logFileManager.setWriteBufferSize(1024 * 1024);
        logFileManager.setStreamName(STREAM_NAME_GLOBAL);
        logFileManager.setLogFileLockManager(Mockito.mock(LogFileLockManager.class));
        logFileManager.setLatestFileCursor(new BinlogCursor("binlog.000007", 13991L, 7));
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = Mockito.mockStatic(RemoteBinlogProxy.class)) {
            RemoteBinlogProxy remoteBinlogProxy = Mockito.mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            CdcFileSystem cdcFileSystem = new CdcFileSystem(path, GROUP_NAME_GLOBAL, STREAM_NAME_GLOBAL);
            logFileManager.setCdcFileSystem(cdcFileSystem);
        }
        return logFileManager;
    }
}
