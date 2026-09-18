/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoExt;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.dumper.metrics.StreamMetrics;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.rpc.TxnMessageReceiver;
import com.aliyun.polardbx.binlog.rpc.TxnStreamRpcClient;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.service.XStreamService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UpstreamBinlogFetcherTest {

    private static final String TASK_NAME = "testTask";
    private static final String STREAM_NAME = "testStream";
    private static final String START_TSO = "123456789";

    @Mock
    private TxnMessageReceiver receiver;

    @Mock
    private ExecutionConfig executionConfig;

    @Mock
    private StreamMetrics metrics;

    @Mock
    private TxnStreamRpcClient rpcClient;

    @Mock
    private BinlogKWayMerger kWayMerger;

    private MockedStatic<XStreamService> xStreamServiceMockedStatic;
    private MockedStatic<HashLevel> hashLevelMockedStatic;
    private MockedStatic<SpringContextHolder> springContextHolderMockedStatic;
    private MockedStatic<DynamicApplicationConfig> dynamicApplicationConfigMockedStatic;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        xStreamServiceMockedStatic = mockStatic(XStreamService.class);
        hashLevelMockedStatic = mockStatic(HashLevel.class);
        springContextHolderMockedStatic = mockStatic(SpringContextHolder.class);
        dynamicApplicationConfigMockedStatic = mockStatic(DynamicApplicationConfig.class);

        // Mock DynamicApplicationConfig to return a default value for BINLOGX_KWAY_SOURCE_QUEUE_SIZE
        dynamicApplicationConfigMockedStatic.when(() -> DynamicApplicationConfig.getInt(any(String.class)))
            .thenReturn(100);
    }

    @After
    public void tearDown() {
        if (xStreamServiceMockedStatic != null) {
            xStreamServiceMockedStatic.close();
        }
        if (hashLevelMockedStatic != null) {
            hashLevelMockedStatic.close();
        }
        if (springContextHolderMockedStatic != null) {
            springContextHolderMockedStatic.close();
        }
        if (dynamicApplicationConfigMockedStatic != null) {
            dynamicApplicationConfigMockedStatic.close();
        }
    }

    @Test
    public void testConstructorWithDumperTask() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        UpstreamBinlogFetcher fetcher = new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.Dumper, STREAM_NAME, executionConfig, receiver, true, 100, 1024);

        assertNotNull(fetcher);
    }

    @Test
    public void testConstructorWithDumperXTaskOneStreamPerDn() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        hashLevelMockedStatic.when(HashLevel::getCurrentHashLevel).thenReturn(HashLevel.DATANODE);
        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        UpstreamBinlogFetcher fetcher = new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.DumperX, STREAM_NAME, executionConfig, receiver, true, 100, 1024);

        assertNotNull(fetcher);
    }

    @Test
    public void testConstructorWithDumperXTaskMultiStream() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        hashLevelMockedStatic.when(HashLevel::getCurrentHashLevel).thenReturn(HashLevel.TABLE);
        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        UpstreamBinlogFetcher fetcher = new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.DumperX, STREAM_NAME, executionConfig, receiver, true, 100, 1024);

        assertNotNull(fetcher);
    }

    @Test
    public void testConnectWithRpcClient() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        UpstreamBinlogFetcher fetcher = spy(new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.Dumper, STREAM_NAME, executionConfig, receiver, true, 100, 1024));

        // Inject mock rpcClient using reflection
        setPrivateField(fetcher, "rpcClient", rpcClient);

        fetcher.connect();

        verify(rpcClient).connect();
    }

    @Test
    public void testConnectWithKWayMerger() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        hashLevelMockedStatic.when(HashLevel::getCurrentHashLevel).thenReturn(HashLevel.TABLE);
        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        UpstreamBinlogFetcher fetcher = spy(new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.DumperX, STREAM_NAME, executionConfig, receiver, true, 100, 1024));

        // Inject mock kWayMerger using reflection
        setPrivateField(fetcher, "kWayMerger", kWayMerger);

        fetcher.connect();

        verify(kWayMerger).connect();
    }

    @Test
    public void testDisconnectWithRpcClient() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        UpstreamBinlogFetcher fetcher = spy(new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.Dumper, STREAM_NAME, executionConfig, receiver, true, 100, 1024));

        // Inject mock rpcClient using reflection
        setPrivateField(fetcher, "rpcClient", rpcClient);

        // First connect
        fetcher.connect();
        // Then disconnect
        fetcher.disconnect();

        verify(rpcClient).disconnect();
    }

    @Test
    public void testDisconnectWithKWayMerger() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        hashLevelMockedStatic.when(HashLevel::getCurrentHashLevel).thenReturn(HashLevel.TABLE);
        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        UpstreamBinlogFetcher fetcher = spy(new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.DumperX, STREAM_NAME, executionConfig, receiver, true, 100, 1024));

        // Inject mock kWayMerger using reflection
        setPrivateField(fetcher, "kWayMerger", kWayMerger);

        // First connect
        fetcher.connect();
        // Then disconnect
        fetcher.disconnect();

        verify(kWayMerger).disconnect();
    }

    @Test
    public void testSetMetricsWithRpcClient() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        UpstreamBinlogFetcher fetcher = spy(new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.Dumper, STREAM_NAME, executionConfig, receiver, true, 100, 1024));

        // Inject mock rpcClient using reflection
        setPrivateField(fetcher, "rpcClient", rpcClient);

        fetcher.setMetrics(metrics);

        verify(rpcClient).setMetricsConsumer(any());
    }

    @Test
    public void testSetMetricsWithKWayMerger() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        hashLevelMockedStatic.when(HashLevel::getCurrentHashLevel).thenReturn(HashLevel.TABLE);
        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        UpstreamBinlogFetcher fetcher = spy(new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.DumperX, STREAM_NAME, executionConfig, receiver, true, 100, 1024));

        // Inject mock kWayMerger using reflection
        setPrivateField(fetcher, "kWayMerger", kWayMerger);

        fetcher.setMetrics(metrics);

        verify(kWayMerger).setMetrics(metrics);
    }

    @Test
    public void testDumpWithRpcClient() throws InterruptedException {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        when(executionConfig.getRuntimeVersion()).thenReturn(1L);
        when(executionConfig.getSubRuntimeVersion()).thenReturn(0L);

        UpstreamBinlogFetcher fetcher = spy(new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.Dumper, STREAM_NAME, executionConfig, receiver, true, 100, 1024));

        // Inject mock rpcClient using reflection
        setPrivateField(fetcher, "rpcClient", rpcClient);

        fetcher.dump(START_TSO);

        verify(rpcClient).dump(any());
    }

    @Test
    public void testDumpWithKWayMerger() throws InterruptedException {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        hashLevelMockedStatic.when(HashLevel::getCurrentHashLevel).thenReturn(HashLevel.TABLE);
        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        UpstreamBinlogFetcher fetcher = spy(new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.DumperX, STREAM_NAME, executionConfig, receiver, true, 100, 1024));

        // Inject mock kWayMerger using reflection
        setPrivateField(fetcher, "kWayMerger", kWayMerger);

        fetcher.dump(START_TSO);

        verify(kWayMerger).dump(eq(START_TSO));
    }

    @Test
    public void testSuspendWhenStreamInPendingState() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        hashLevelMockedStatic.when(HashLevel::getCurrentHashLevel).thenReturn(HashLevel.DATANODE);

        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        // Mock XStreamService.isStreamInPendingState to return true
        xStreamServiceMockedStatic.when(() -> XStreamService.isStreamInPendingState(STREAM_NAME)).thenReturn(true);

        UpstreamBinlogFetcher fetcher = spy(new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.DumperX, STREAM_NAME, executionConfig, receiver, true, 100, 1024));

        // Mock connected state using reflection
        try {
            java.lang.reflect.Field connectedField = UpstreamBinlogFetcher.class.getDeclaredField("connected");
            connectedField.setAccessible(true);
            AtomicBoolean connected = (AtomicBoolean) connectedField.get(fetcher);
            connected.set(true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set connected field", e);
        }

        // Since we cannot mock Thread.interrupted(), we will test the suspend method differently
        // We'll directly call the suspend method and check its behavior
        // To avoid infinite loop, we'll interrupt the current thread before calling suspend
        Thread.currentThread().interrupt();
        boolean result = fetcher.suspend();
        // Clear the interrupt status
        Thread.interrupted();

        assertTrue(result);
    }

    @Test
    public void testSuspendWhenStreamNotInPendingState() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        hashLevelMockedStatic.when(HashLevel::getCurrentHashLevel).thenReturn(HashLevel.DATANODE);

        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        // Mock XStreamService.isStreamInPendingState to return false
        xStreamServiceMockedStatic.when(() -> XStreamService.isStreamInPendingState(STREAM_NAME)).thenReturn(false);

        UpstreamBinlogFetcher fetcher = spy(new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.DumperX, STREAM_NAME, executionConfig, receiver, true, 100, 1024));

        boolean result = fetcher.suspend();

        assertFalse(result);
    }

    @Test
    public void testGetStreamId() {
        List<BinlogTaskInfo> taskInfoList = createMockTaskInfoList();
        mockTaskInfoMapper(taskInfoList);

        // Mock executionConfig to return expected tasks
        List<String> expectedTasks = new ArrayList<>();
        expectedTasks.add("upstreamTask");
        when(executionConfig.getSources()).thenReturn(expectedTasks);

        XStream xStream = mock(XStream.class);
        when(xStream.getId()).thenReturn(1L);
        xStreamServiceMockedStatic.when(() -> XStreamService.getXStreamByName(STREAM_NAME)).thenReturn(xStream);

        UpstreamBinlogFetcher fetcher = new UpstreamBinlogFetcher(
            TASK_NAME, TaskType.Dumper, STREAM_NAME, executionConfig, receiver, true, 100, 1024);

        int streamId = fetcher.getStreamId(STREAM_NAME);

        assertEquals(1, streamId);
    }

    @Test
    public void testCheckTableIdEnabled() {
        UpstreamBinlogFetcher fetcher = Mockito.mock(UpstreamBinlogFetcher.class, CALLS_REAL_METHODS);
        BinlogTaskInfo binlogTaskInfo = new BinlogTaskInfo();
        BinlogTaskInfoExt binlogTaskInfoExt = new BinlogTaskInfoExt();
        binlogTaskInfo.setExt(JSON.toJSONString(binlogTaskInfoExt));
        Assert.assertTrue(fetcher.checkTableIdEnabled(binlogTaskInfo));
        binlogTaskInfoExt.setTableIdEnabled(false);
        binlogTaskInfo.setExt(JSON.toJSONString(binlogTaskInfoExt));
        Assert.assertFalse(fetcher.checkTableIdEnabled(binlogTaskInfo));
    }

    private List<BinlogTaskInfo> createMockTaskInfoList() {
        List<BinlogTaskInfo> taskInfoList = new ArrayList<>();
        BinlogTaskInfo taskInfo = new BinlogTaskInfo();
        taskInfo.setTaskName("upstreamTask");
        taskInfo.setIp("127.0.0.1");
        taskInfo.setPort(8080);
        taskInfo.setRole("Final");
        taskInfoList.add(taskInfo);
        return taskInfoList;
    }

    private void mockTaskInfoMapper(List<BinlogTaskInfo> taskInfoList) {
        // Create a mock BinlogTaskInfoMapper
        com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper taskInfoMapper =
            mock(com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper.class);
        when(taskInfoMapper.select(any())).thenReturn(taskInfoList);

        // Mock SpringContextHolder to return our mock mapper
        springContextHolderMockedStatic.when(
                () -> SpringContextHolder.getObject(com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper.class))
            .thenReturn(taskInfoMapper);
    }

    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set private field: " + fieldName, e);
        }
    }
}