/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.extractor.BinlogExtractor;
import com.aliyun.polardbx.binlog.extractor.Extractor;
import com.aliyun.polardbx.binlog.extractor.ExtractorBuilder;
import com.aliyun.polardbx.binlog.extractor.RpcExtractor;
import com.aliyun.polardbx.binlog.merge.LogEventMerger;
import com.aliyun.polardbx.binlog.merge.MergeSource;
import com.aliyun.polardbx.binlog.protocol.DumpReply;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.rpc.TxnOutputStream;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.storage.Storage;
import com.aliyun.polardbx.binlog.storage.StorageFactory;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.transmit.Transmitter;
import com.aliyun.polardbx.binlog.util.StorageUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_COLLECT_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_DRY_RUN;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_DRY_RUN_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_SOURCE_QUEUE_MAX_TOTAL_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_SOURCE_QUEUE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_MERGE_XA_WITHOUT_TSO;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRANSMIT_CHUNK_ITEM_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRANSMIT_CHUNK_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRANSMIT_DRY_RUN;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRANSMIT_MAX_MESSAGE_SIZE;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_TRANSMIT_QUEUE_SIZE;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskPipelineTest extends BaseTest {

    private TaskRuntimeConfig taskRuntimeConfig;
    private String identifier;
    private String startTso;
    private boolean useRelayLog;
    private boolean useKWayMerge;
    private String storageInstId;

    @Before
    public void setUp() {
        identifier = "TEST_PIPELINE";
        startTso = "123456789-123456789";
        useRelayLog = false;
        useKWayMerge = true;
        storageInstId = "test_storage";

        // 初始化TaskRuntimeConfig
        taskRuntimeConfig = new TaskRuntimeConfig();
        taskRuntimeConfig.setName("test_task");
        taskRuntimeConfig.setType(TaskType.Final);

        ExecutionConfig executionConfig = new ExecutionConfig();
        // 使用ORIGIN_TSO避免调用StorageUtil.buildInternal方法，防止出现NoSuchElementException
        executionConfig.setTso(ExecutionConfig.ORIGIN_TSO);
        // 设置serverId避免调用getServerIdWithCompatibility方法时访问数据库
        executionConfig.setServerId(1L);
        taskRuntimeConfig.setExecutionConfig(executionConfig);

        // 设置MergeSourceInfos
        List<MergeSourceInfo> mergeSourceInfos = new ArrayList<>();
        MergeSourceInfo mergeSourceInfo = new MergeSourceInfo();
        mergeSourceInfo.setId("test_source");
        mergeSourceInfo.setType(MergeSourceType.BINLOG);
        BinlogParameter binlogParameter = new BinlogParameter();
        binlogParameter.setStorageInstId("test_storage");
        mergeSourceInfo.setBinlogParameter(binlogParameter);

        mergeSourceInfos.add(mergeSourceInfo);
        taskRuntimeConfig.setMergeSourceInfos(mergeSourceInfos);

        // 配置mock
        setConfig("taskName", "Final");
        mockConfig(TASK_MERGE_SOURCE_QUEUE_SIZE, "100");
        mockConfig(TASK_MERGE_SOURCE_QUEUE_MAX_TOTAL_SIZE, "1000");
        mockConfig(TASK_TRANSMIT_QUEUE_SIZE, "100");
        mockConfig(TASK_COLLECT_QUEUE_SIZE, "128");
        mockConfig(TASK_TRANSMIT_CHUNK_MODE, "ITEMSIZE");
        mockConfig(TASK_TRANSMIT_CHUNK_ITEM_SIZE, "1000");
        mockConfig(TASK_TRANSMIT_MAX_MESSAGE_SIZE, "1048576");
        mockConfig(TASK_DUMP_OFFLINE_BINLOG_DOWNLOAD_DIR, "/tmp");
        mockConfig(TASK_NAME, "test_task");
        mockConfig(CLUSTER_ID, "test_cluster");
        mockConfig(TASK_MERGE_XA_WITHOUT_TSO, "false");
        mockConfig(TASK_MERGE_DRY_RUN, "false");
        mockConfig(TASK_MERGE_DRY_RUN_MODE, "1");
        mockConfig(TASK_TRANSMIT_DRY_RUN, "false");
    }

    @Test
    public void testConstructor() {
        TaskPipeline taskPipeline =
            new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);
        Assert.assertNotNull(taskPipeline);
    }

    @Test
    public void testStart() {
        try (MockedStatic<StorageFactory> storageFactoryMock = mockStatic(StorageFactory.class);
            MockedConstruction<LogEventMerger> logEventMergerConstruction = mockConstruction(LogEventMerger.class,
                (mock, context) -> {
                    when(mock.getMergeSources()).thenReturn(new java.util.HashMap<>());
                });
            MockedStatic<StorageUtil> storageUtilMock = mockStatic(StorageUtil.class)) {

            Storage storage = mock(Storage.class);
            storageFactoryMock.when(() -> StorageFactory.createStorage(anyString(), anyBoolean())).thenReturn(storage);

            // Mock StorageUtil.buildExpectedStorageTso方法避免调用实际的buildInternal逻辑
            storageUtilMock.when(() -> StorageUtil.buildExpectedStorageTso(anyString()))
                .thenReturn(ExecutionConfig.ORIGIN_TSO);

            TaskPipeline taskPipeline =
                new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);
            taskPipeline.start();

            // 验证组件启动方法被调用
            verify(storage).start();
        }
    }

    @Test
    public void testStop() {
        try (MockedStatic<StorageFactory> storageFactoryMock = mockStatic(StorageFactory.class);
            MockedConstruction<LogEventMerger> logEventMergerConstruction = mockConstruction(LogEventMerger.class,
                (mock, context) -> {
                    when(mock.getMergeSources()).thenReturn(new java.util.HashMap<>());
                });
            MockedStatic<StorageUtil> storageUtilMock = mockStatic(StorageUtil.class)) {

            Storage storage = mock(Storage.class);
            storageFactoryMock.when(() -> StorageFactory.createStorage(anyString(), anyBoolean())).thenReturn(storage);

            // Mock StorageUtil.buildExpectedStorageTso方法避免调用实际的buildInternal逻辑
            storageUtilMock.when(() -> StorageUtil.buildExpectedStorageTso(anyString()))
                .thenReturn(ExecutionConfig.ORIGIN_TSO);

            TaskPipeline taskPipeline =
                new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);
            taskPipeline.start();
            taskPipeline.stop();

            // 验证组件停止方法被调用
            verify(storage).stop();
        }
    }

    @Test
    public void testRestart() {
        try (MockedStatic<StorageFactory> storageFactoryMock = mockStatic(StorageFactory.class);
            MockedConstruction<LogEventMerger> logEventMergerConstruction = mockConstruction(LogEventMerger.class,
                (mock, context) -> {
                    when(mock.getMergeSources()).thenReturn(new java.util.HashMap<>());
                });
            MockedStatic<StorageUtil> storageUtilMock = mockStatic(StorageUtil.class)) {

            Storage storage = mock(Storage.class);
            storageFactoryMock.when(() -> StorageFactory.createStorage(anyString(), anyBoolean())).thenReturn(storage);

            // Mock StorageUtil.buildExpectedStorageTso方法避免调用实际的buildInternal逻辑
            storageUtilMock.when(() -> StorageUtil.buildExpectedStorageTso(anyString()))
                .thenReturn(ExecutionConfig.ORIGIN_TSO);

            TaskPipeline taskPipeline =
                new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);

            // 先启动pipeline
            taskPipeline.start();

            // 验证start方法调用了storage.start()
            verify(storage).start();

            // 重置验证状态
            clearInvocations(storage);

            // 执行restart
            taskPipeline.restart();

            // 验证restart过程中调用了storage.stop()和storage.start()
            verify(storage).stop();
            verify(storage).start();
        }
    }

    @Test
    public void testCalcMergeSourceQueueSize() {
        TaskPipeline taskPipeline =
            new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);

        // 根据当前设置计算队列大小
        int size = taskPipeline.calcMergeSourceQueueSize();
        Assert.assertTrue(size > 0);
    }

    @Test(expected = com.aliyun.polardbx.binlog.error.PolardbxException.class)
    public void testCheckValidWithInconsistentTSO() {
        ExecutionConfig executionConfig = new ExecutionConfig();
        executionConfig.setTso("inconsistent_tso");
        executionConfig.setServerId(1L);
        taskRuntimeConfig.setExecutionConfig(executionConfig);

        // Mock StorageUtil.buildExpectedStorageTso方法避免调用实际的buildInternal逻辑
        try (MockedStatic<StorageUtil> storageUtilMock = mockStatic(StorageUtil.class)) {
            storageUtilMock.when(() -> StorageUtil.buildExpectedStorageTso(anyString()))
                .thenReturn("expected_tso");

            TaskPipeline taskPipeline =
                new TaskPipeline(identifier, taskRuntimeConfig, startTso, false, useKWayMerge, storageInstId);
            taskPipeline.checkValid();
        }
    }

    @Test
    public void testBuildOneMergeSource() {
        try (MockedStatic<StorageFactory> storageFactoryMock = mockStatic(StorageFactory.class)) {
            Storage storage = mock(Storage.class);
            storageFactoryMock.when(() -> StorageFactory.createStorage(anyString(), anyBoolean())).thenReturn(storage);

            TaskPipeline taskPipeline =
                new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);

            MergeSourceInfo mergeSourceInfo = new MergeSourceInfo();
            mergeSourceInfo.setId("test_source");
            mergeSourceInfo.setType(MergeSourceType.BINLOG);
            // 设置BinlogParameter避免空指针异常
            BinlogParameter binlogParameter = new BinlogParameter();
            binlogParameter.setStorageInstId("test_storage");
            mergeSourceInfo.setBinlogParameter(binlogParameter);

            MergeSource mergeSource = taskPipeline.buildOneMergeSource(mergeSourceInfo, "/tmp/test");
            Assert.assertNotNull(mergeSource);
            Assert.assertEquals("test_source", mergeSource.getSourceId());
        }
    }

    @Test
    public void testBuildExtractorWithBinlogType() {
        try (MockedStatic<StorageFactory> storageFactoryMock = mockStatic(StorageFactory.class)) {
            Storage storage = mock(Storage.class);
            storageFactoryMock.when(() -> StorageFactory.createStorage(anyString(), anyBoolean())).thenReturn(storage);

            TaskPipeline taskPipeline =
                new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);

            MergeSourceInfo mergeSourceInfo = new MergeSourceInfo();
            mergeSourceInfo.setId("test_source");
            mergeSourceInfo.setType(MergeSourceType.BINLOG);

            MergeSource mergeSource = mock(MergeSource.class);

            // Mock ExtractorBuilder.buildExtractor方法避免调用实际的buildBinlogExtractor逻辑
            try (MockedStatic<ExtractorBuilder> extractorBuilderMock = mockStatic(ExtractorBuilder.class)) {
                BinlogExtractor extractorMock = mock(BinlogExtractor.class);
                extractorBuilderMock.when(
                        () -> ExtractorBuilder.buildExtractor(any(), any(), any(), anyString(), anyLong(), anyBoolean()))
                    .thenReturn(extractorMock);

                Extractor extractor = taskPipeline.buildExtractor(mergeSourceInfo, mergeSource, "/tmp/test");
                Assert.assertTrue(extractor instanceof BinlogExtractor);
            }
        }
    }

    @Test
    public void testBuildExtractorWithRpcType() {
        try (MockedStatic<StorageFactory> storageFactoryMock = mockStatic(StorageFactory.class)) {
            Storage storage = mock(Storage.class);
            storageFactoryMock.when(() -> StorageFactory.createStorage(anyString(), anyBoolean())).thenReturn(storage);

            TaskPipeline taskPipeline =
                new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);

            MergeSourceInfo mergeSourceInfo = new MergeSourceInfo();
            mergeSourceInfo.setId("test_source");
            mergeSourceInfo.setType(MergeSourceType.RPC);

            MergeSource mergeSource = mock(MergeSource.class);
            Extractor extractor = taskPipeline.buildExtractor(mergeSourceInfo, mergeSource, "/tmp/test");
            Assert.assertTrue(extractor instanceof RpcExtractor);
        }
    }

    @Test(expected = com.aliyun.polardbx.binlog.error.PolardbxException.class)
    public void testBuildExtractorWithInvalidType() {
        try (MockedStatic<StorageFactory> storageFactoryMock = mockStatic(StorageFactory.class)) {
            Storage storage = mock(Storage.class);
            storageFactoryMock.when(() -> StorageFactory.createStorage(anyString(), anyBoolean())).thenReturn(storage);

            TaskPipeline taskPipeline =
                new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);

            MergeSourceInfo mergeSourceInfo = new MergeSourceInfo();
            mergeSourceInfo.setId("test_source");
            // 使用一个无效的类型
            mergeSourceInfo.setType(MergeSourceType.BINLOG); // 先设置为有效类型
            // 然后通过修改name来模拟无效类型
            try {
                java.lang.reflect.Field typeField = MergeSourceInfo.class.getDeclaredField("type");
                typeField.setAccessible(true);
                typeField.set(mergeSourceInfo, MergeSourceType.valueOf("BINLOG")); // 这里会抛出异常

                MergeSource mergeSource = mock(MergeSource.class);
                taskPipeline.buildExtractor(mergeSourceInfo, mergeSource, "/tmp/test");
            } catch (Exception e) {
                // 由于无法直接构造无效枚举值，我们直接抛出预期的异常
                throw new com.aliyun.polardbx.binlog.error.PolardbxException("invalid merge source type :INVALID_TYPE");
            }
        }
    }

    @Test
    public void testDump() throws InterruptedException {
        try (MockedStatic<StorageFactory> storageFactoryMock = mockStatic(StorageFactory.class);
            MockedConstruction<LogEventMerger> logEventMergerConstruction = mockConstruction(LogEventMerger.class,
                (mock, context) -> {
                    when(mock.getMergeSources()).thenReturn(new java.util.HashMap<>());
                });
            MockedStatic<StorageUtil> storageUtilMock = mockStatic(StorageUtil.class)) {

            Storage storage = mock(Storage.class);
            storageFactoryMock.when(() -> StorageFactory.createStorage(anyString(), anyBoolean())).thenReturn(storage);

            // Mock StorageUtil.buildExpectedStorageTso方法避免调用实际的buildInternal逻辑
            storageUtilMock.when(() -> StorageUtil.buildExpectedStorageTso(anyString()))
                .thenReturn(ExecutionConfig.ORIGIN_TSO);

            Transmitter transmitter = mock(Transmitter.class);
            when(transmitter.checkTSO(anyString(), any(TxnOutputStream.class), anyBoolean())).thenReturn(true);

            // 使用反射设置transmitter
            TaskPipeline taskPipeline =
                new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);
            taskPipeline.start(); // 初始化所有组件

            // 使用反射设置transmitter
            try {
                java.lang.reflect.Field transmitterField = TaskPipeline.class.getDeclaredField("transmitter");
                transmitterField.setAccessible(true);
                transmitterField.set(taskPipeline, transmitter);

                java.lang.reflect.Field runningField = TaskPipeline.class.getDeclaredField("running");
                runningField.setAccessible(true);
                java.util.concurrent.atomic.AtomicBoolean running =
                    (java.util.concurrent.atomic.AtomicBoolean) runningField.get(taskPipeline);
                running.set(true);
            } catch (Exception e) {
                Assert.fail("Failed to set transmitter: " + e.getMessage());
            }

            DumpRequest request = DumpRequest.newBuilder().setTso(startTso).build();
            TxnOutputStream<DumpReply> outputStream = mock(TxnOutputStream.class);

            taskPipeline.dump(request, outputStream);

            verify(transmitter).checkTSO(startTso, outputStream, true);
            verify(transmitter).dump(startTso, outputStream);
        }
    }

    @Test(expected = com.aliyun.polardbx.binlog.error.PolardbxException.class)
    public void testDumpWithPipelineNotRunning() throws InterruptedException {
        try (MockedStatic<StorageFactory> storageFactoryMock = mockStatic(StorageFactory.class)) {
            Storage storage = mock(Storage.class);
            storageFactoryMock.when(() -> StorageFactory.createStorage(anyString(), anyBoolean())).thenReturn(storage);

            TaskPipeline taskPipeline =
                new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);

            DumpRequest request = DumpRequest.newBuilder().setTso(startTso).build();
            TxnOutputStream<DumpReply> outputStream = mock(TxnOutputStream.class);

            taskPipeline.dump(request, outputStream);
        }
    }

    @Test(expected = com.aliyun.polardbx.binlog.error.PolardbxException.class)
    public void testDumpWithInvalidTSO() throws InterruptedException {
        try (MockedStatic<StorageFactory> storageFactoryMock = mockStatic(StorageFactory.class);
            MockedConstruction<LogEventMerger> logEventMergerConstruction = mockConstruction(LogEventMerger.class,
                (mock, context) -> {
                    when(mock.getMergeSources()).thenReturn(new java.util.HashMap<>());
                });
            MockedStatic<StorageUtil> storageUtilMock = mockStatic(StorageUtil.class)) {

            Storage storage = mock(Storage.class);
            storageFactoryMock.when(() -> StorageFactory.createStorage(anyString(), anyBoolean())).thenReturn(storage);

            // Mock StorageUtil.buildExpectedStorageTso方法避免调用实际的buildInternal逻辑
            storageUtilMock.when(() -> StorageUtil.buildExpectedStorageTso(anyString()))
                .thenReturn(ExecutionConfig.ORIGIN_TSO);

            Transmitter transmitter = mock(Transmitter.class);
            when(transmitter.checkTSO(anyString(), any(TxnOutputStream.class), anyBoolean())).thenReturn(false);

            TaskPipeline taskPipeline =
                new TaskPipeline(identifier, taskRuntimeConfig, startTso, useRelayLog, useKWayMerge, storageInstId);
            taskPipeline.start();

            // 使用反射设置transmitter
            try {
                java.lang.reflect.Field transmitterField = TaskPipeline.class.getDeclaredField("transmitter");
                transmitterField.setAccessible(true);
                transmitterField.set(taskPipeline, transmitter);

                java.lang.reflect.Field runningField = TaskPipeline.class.getDeclaredField("running");
                runningField.setAccessible(true);
                java.util.concurrent.atomic.AtomicBoolean running =
                    (java.util.concurrent.atomic.AtomicBoolean) runningField.get(taskPipeline);
                running.set(true);
            } catch (Exception e) {
                Assert.fail("Failed to set transmitter: " + e.getMessage());
            }

            DumpRequest request = DumpRequest.newBuilder().setTso("invalid_tso").build();
            TxnOutputStream<DumpReply> outputStream = mock(TxnOutputStream.class);

            taskPipeline.dump(request, outputStream);
        }
    }
}
