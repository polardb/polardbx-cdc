/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.task;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.TaskRuntimeConfigProvider;
import com.aliyun.polardbx.binlog.dao.BinlogDumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.TaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.XStreamMapper;
import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mybatis.dynamic.sql.update.UpdateDSLCompleter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.aliyun.polardbx.binlog.CommonConstants.STREAM_NAME_GLOBAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.INST_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_WAIT_SUB_VERSION_CALLBACK_TIMEOUT_MS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskHeartbeatTest extends BaseTest {

    private static final String CLUSTER_ID_VALUE = "test_cluster";
    private static final String CLUSTER_TYPE_VALUE = "test_cluster_type";
    private static final String TASK_NAME = "test_task";
    private static final int INTERVAL = 1000;

    @Mock
    private TaskRuntimeConfigProvider taskRuntimeConfigProvider;

    @Mock
    private ISubVersionChangeCallback subVersionChangeCallback;

    @Mock
    private BinlogDumperInfoMapper binlogDumperInfoMapper;

    @Mock
    private DumperInfoMapper dumperInfoMapper;

    @Mock
    private NodeInfoMapper nodeInfoMapper;

    @Mock
    private XStreamMapper xStreamMapper;

    @Mock
    private TaskInfoMapper taskInfoMapper;

    @Mock
    private TaskRuntimeConfig taskRuntimeConfig;

    @Mock
    private BinlogTaskConfig binlogTaskConfig;

    @Mock
    private ExecutionConfig executionConfig;

    @Mock
    private Supplier<Map<String, IDumperStatisticProvider>> dumperStatisticSupplier;

    @Mock
    private Consumer<Void> processExitCallback;

    private TaskHeartbeat taskHeartbeat;
    private TaskHeartbeat taskHeartbeatForDumper;
    private TaskHeartbeat taskHeartbeatForTask;
    private TaskHeartbeat taskHeartbeatForDumperX;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        mockConfig(CLUSTER_ID, CLUSTER_ID_VALUE);
        mockConfig(INST_ID, "test_inst_id");
        mockConfig(TOPOLOGY_WAIT_SUB_VERSION_CALLBACK_TIMEOUT_MS, "5000");

        // Setup common mocks
        when(taskRuntimeConfig.getBinlogTaskConfig()).thenReturn(binlogTaskConfig);
        when(taskRuntimeConfig.getExecutionConfig()).thenReturn(executionConfig);
        when(binlogTaskConfig.getVersion()).thenReturn(1L);
        when(binlogTaskConfig.getSubVersion()).thenReturn(1L);
        when(binlogTaskConfig.getRole()).thenReturn("Dumper");

        // Register mocks in Spring context
        registerSpringObject("binlogDumperInfoMapper", binlogDumperInfoMapper);
        registerSpringObject("dumperInfoMapper", dumperInfoMapper);
        registerSpringObject("nodeInfoMapper", nodeInfoMapper);
        registerSpringObject("XStreamMapper", xStreamMapper);
        registerSpringObject("taskInfoMapper", taskInfoMapper);

        taskHeartbeatForDumper = new TaskHeartbeat(
            CLUSTER_ID_VALUE,
            CLUSTER_TYPE_VALUE,
            TASK_NAME,
            INTERVAL,
            taskRuntimeConfig,
            taskRuntimeConfigProvider,
            subVersionChangeCallback
        );
        taskHeartbeatForDumper.setProcessExitCallback(processExitCallback);

        // Create task with Task role
        when(binlogTaskConfig.getRole()).thenReturn("Final");
        taskHeartbeatForTask = new TaskHeartbeat(
            CLUSTER_ID_VALUE,
            CLUSTER_TYPE_VALUE,
            TASK_NAME,
            INTERVAL,
            taskRuntimeConfig,
            taskRuntimeConfigProvider,
            subVersionChangeCallback
        );
        taskHeartbeatForTask.setProcessExitCallback(processExitCallback);

        // Create task with DumperX role
        when(binlogTaskConfig.getRole()).thenReturn("DumperX");
        taskHeartbeatForDumperX = new TaskHeartbeat(
            CLUSTER_ID_VALUE,
            CLUSTER_TYPE_VALUE,
            TASK_NAME,
            INTERVAL,
            taskRuntimeConfig,
            taskRuntimeConfigProvider,
            subVersionChangeCallback
        );
        taskHeartbeatForDumperX.setProcessExitCallback(processExitCallback);

        // Reset to Dumper role for main test instance
        when(binlogTaskConfig.getRole()).thenReturn("Dumper");
        taskHeartbeat = taskHeartbeatForDumper;
    }

    @Test
    public void testConstructor() {
        Assert.assertNotNull(taskHeartbeat);
        // Test that fields are properly initialized
    }

    @Test
    public void testUpdateHeartbeat_Dumper() {
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = Mockito.mockStatic(
            RuntimeLeaderElector.class)) {
            runtimeLeaderElectorMock.when(() -> RuntimeLeaderElector.isDumperMaster(anyLong(), anyString()))
                .thenReturn(true);

            Map<String, IDumperStatisticProvider> statisticProviderMap = new HashMap<>();
            IDumperStatisticProvider statisticProvider = Mockito.mock(IDumperStatisticProvider.class);
            BinlogCursor cursor = new BinlogCursor("binlog.000001", 100L);
            when(statisticProvider.getLatestFileCursor()).thenReturn(cursor);
            when(statisticProvider.getDumperDelay()).thenReturn(50L);
            statisticProviderMap.put(STREAM_NAME_GLOBAL, statisticProvider);

            when(dumperStatisticSupplier.get()).thenReturn(statisticProviderMap);
            taskHeartbeat.setDumperStatisticSupplier(dumperStatisticSupplier);

            when(binlogDumperInfoMapper.updateDumperHeartbeatWithDelay(anyString(), anyString(), anyString(), anyLong(),
                anyLong(), anyBoolean()))
                .thenReturn(1);

            try (MockedStatic<SystemDbConfig> systemDbConfigMock = Mockito.mockStatic(SystemDbConfig.class)) {
                systemDbConfigMock.when(() -> SystemDbConfig.updateSystemDbConfig(anyString(), anyString()))
                    .thenAnswer(invocation -> null);
                taskHeartbeat.updateDumperHeartbeat();
            }

            verify(binlogDumperInfoMapper, times(1))
                .updateDumperHeartbeatWithDelay(eq(TASK_NAME), anyString(), eq(CLUSTER_ID_VALUE), anyLong(), anyLong(),
                    anyBoolean());
            verify(dumperInfoMapper, times(1)).update(any(UpdateDSLCompleter.class));
            verify(nodeInfoMapper, times(1)).update(any(UpdateDSLCompleter.class));
        }
    }

    @Test
    public void testUpdateHeartbeat_Dumper_NoCursor() {
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = Mockito.mockStatic(
            RuntimeLeaderElector.class)) {
            runtimeLeaderElectorMock.when(() -> RuntimeLeaderElector.isDumperMaster(anyLong(), anyString()))
                .thenReturn(false);

            Map<String, IDumperStatisticProvider> statisticProviderMap = new HashMap<>();
            IDumperStatisticProvider statisticProvider = Mockito.mock(IDumperStatisticProvider.class);
            when(statisticProvider.getLatestFileCursor()).thenReturn(null);
            when(statisticProvider.getDumperDelay()).thenReturn(0L);
            statisticProviderMap.put(STREAM_NAME_GLOBAL, statisticProvider);

            when(dumperStatisticSupplier.get()).thenReturn(statisticProviderMap);
            taskHeartbeat.setDumperStatisticSupplier(dumperStatisticSupplier);

            when(binlogDumperInfoMapper.updateDumperHeartbeatWithDelay(anyString(), anyString(), anyString(), anyLong(),
                anyLong(), anyBoolean()))
                .thenReturn(1);

            taskHeartbeat.updateDumperHeartbeat();

            verify(binlogDumperInfoMapper, times(1))
                .updateDumperHeartbeatWithDelay(eq(TASK_NAME), anyString(), eq(CLUSTER_ID_VALUE), anyLong(), anyLong(),
                    anyBoolean());
            verify(nodeInfoMapper, times(0)).update(any(UpdateDSLCompleter.class));
        }
    }

    @Test
    public void testUpdateHeartbeat_Dumper_ExitOnZeroResult() {
        try (MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = Mockito.mockStatic(
            RuntimeLeaderElector.class)) {
            runtimeLeaderElectorMock.when(() -> RuntimeLeaderElector.isDumperMaster(anyLong(), anyString()))
                .thenReturn(false);

            Map<String, IDumperStatisticProvider> statisticProviderMap = new HashMap<>();
            IDumperStatisticProvider statisticProvider = Mockito.mock(IDumperStatisticProvider.class);
            when(statisticProvider.getLatestFileCursor()).thenReturn(null);
            when(statisticProvider.getDumperDelay()).thenReturn(0L);
            statisticProviderMap.put(STREAM_NAME_GLOBAL, statisticProvider);

            when(dumperStatisticSupplier.get()).thenReturn(statisticProviderMap);
            taskHeartbeat.setDumperStatisticSupplier(dumperStatisticSupplier);

            when(binlogDumperInfoMapper.updateDumperHeartbeatWithDelay(anyString(), anyString(), anyString(), anyLong(),
                anyLong(), anyBoolean()))
                .thenReturn(0); // This should cause system exit

            // We expect the method to call Runtime.getRuntime().halt(1)
            // Since we can't easily test System.exit or Runtime.getRuntime().halt, we just verify the method was called
            taskHeartbeat.updateDumperHeartbeat();

            verify(binlogDumperInfoMapper, times(1))
                .updateDumperHeartbeatWithDelay(eq(TASK_NAME), anyString(), eq(CLUSTER_ID_VALUE), anyLong(), anyLong(),
                    anyBoolean());
            verify(processExitCallback, times(1)).accept(null);
        }
    }

    @Test
    public void testUpdateHeartbeat_Task() {
        when(taskInfoMapper.updateTaskHeartbeat(anyString(), anyString(), anyLong(), anyBoolean()))
            .thenReturn(1);

        taskHeartbeatForTask.updateTaskHeartbeat();

        verify(taskInfoMapper, times(1))
            .updateTaskHeartbeat(eq(TASK_NAME), eq(CLUSTER_ID_VALUE), anyLong(), anyBoolean());
    }

    @Test
    public void testUpdateHeartbeat_Task_ExitOnZeroResult() {
        when(taskInfoMapper.updateTaskHeartbeat(anyString(), anyString(), anyLong(), anyBoolean()))
            .thenReturn(0); // This should cause system exit

        taskHeartbeatForTask.updateTaskHeartbeat();

        verify(taskInfoMapper, times(1))
            .updateTaskHeartbeat(eq(TASK_NAME), eq(CLUSTER_ID_VALUE), anyLong(), anyBoolean());
        verify(processExitCallback, times(1)).accept(null);
    }

    @Test
    public void testUpdateHeartbeat_DumperX() {
        when(binlogDumperInfoMapper.updateDumperHeartbeatBasic(anyString(), anyString(), anyString(), anyLong(),
            anyBoolean()))
            .thenReturn(1);

        Set<String> streamNames = new HashSet<>();
        streamNames.add("stream1");
        streamNames.add("stream2");
        when(executionConfig.getStreamNameSet()).thenReturn(streamNames);

        Map<String, IDumperStatisticProvider> statisticProviderMap = new HashMap<>();
        IDumperStatisticProvider statisticProvider1 = Mockito.mock(IDumperStatisticProvider.class);
        IDumperStatisticProvider statisticProvider2 = Mockito.mock(IDumperStatisticProvider.class);
        BinlogCursor cursor1 = new BinlogCursor("binlog.000001", 100L);
        BinlogCursor cursor2 = new BinlogCursor("binlog.000002", 200L);
        when(statisticProvider1.getLatestFileCursor()).thenReturn(cursor1);
        when(statisticProvider2.getLatestFileCursor()).thenReturn(cursor2);
        statisticProviderMap.put("stream1", statisticProvider1);
        statisticProviderMap.put("stream2", statisticProvider2);

        when(dumperStatisticSupplier.get()).thenReturn(statisticProviderMap);
        taskHeartbeatForDumperX.setDumperStatisticSupplier(dumperStatisticSupplier);

        taskHeartbeatForDumperX.updateDumperXHeartbeat();

        verify(binlogDumperInfoMapper, times(1))
            .updateDumperHeartbeatBasic(eq(TASK_NAME), anyString(), eq(CLUSTER_ID_VALUE), anyLong(), anyBoolean());
        verify(xStreamMapper, times(2)).update(any(UpdateDSLCompleter.class));
    }

    @Test
    public void testUpdateHeartbeat_DumperX_NoCursor() {
        when(binlogDumperInfoMapper.updateDumperHeartbeatBasic(anyString(), anyString(), anyString(), anyLong(),
            anyBoolean()))
            .thenReturn(1);

        Set<String> streamNames = new HashSet<>();
        streamNames.add("stream1");
        when(executionConfig.getStreamNameSet()).thenReturn(streamNames);

        Map<String, IDumperStatisticProvider> statisticProviderMap = new HashMap<>();
        IDumperStatisticProvider statisticProvider = Mockito.mock(IDumperStatisticProvider.class);
        when(statisticProvider.getLatestFileCursor()).thenReturn(null); // No cursor
        statisticProviderMap.put("stream1", statisticProvider);

        when(dumperStatisticSupplier.get()).thenReturn(statisticProviderMap);
        taskHeartbeatForDumperX.setDumperStatisticSupplier(dumperStatisticSupplier);

        taskHeartbeatForDumperX.updateDumperXHeartbeat();

        verify(binlogDumperInfoMapper, times(1))
            .updateDumperHeartbeatBasic(eq(TASK_NAME), anyString(), eq(CLUSTER_ID_VALUE), anyLong(), anyBoolean());
        verify(xStreamMapper, times(0)).update(any(UpdateDSLCompleter.class));
    }

    @Test
    public void testUpdateHeartbeat_DumperX_ExitOnZeroResult() {
        when(binlogDumperInfoMapper.updateDumperHeartbeatBasic(anyString(), anyString(), anyString(), anyLong(),
            anyBoolean()))
            .thenReturn(0); // This should cause system exit

        taskHeartbeatForDumperX.updateDumperXHeartbeat();

        verify(binlogDumperInfoMapper, times(1))
            .updateDumperHeartbeatBasic(eq(TASK_NAME), anyString(), eq(CLUSTER_ID_VALUE), anyLong(), anyBoolean());
        verify(processExitCallback, times(1)).accept(null);
    }

    @Test
    public void testUpdateHeartbeat_DumperX_NullStatisticMap() {
        when(binlogDumperInfoMapper.updateDumperHeartbeatBasic(anyString(), anyString(), anyString(), anyLong(),
            anyBoolean()))
            .thenReturn(1);

        Set<String> streamNames = new HashSet<>();
        streamNames.add("stream1");
        when(executionConfig.getStreamNameSet()).thenReturn(streamNames);

        // Mock dumperStatisticSupplier to return null
        Map<String, IDumperStatisticProvider> statisticProviderMap = new HashMap<>();
        when(dumperStatisticSupplier.get()).thenReturn(statisticProviderMap);
        taskHeartbeatForDumperX.setDumperStatisticSupplier(dumperStatisticSupplier);

        // This should not throw an exception
        taskHeartbeatForDumperX.updateDumperXHeartbeat();

        verify(binlogDumperInfoMapper, times(1))
            .updateDumperHeartbeatBasic(eq(TASK_NAME), anyString(), eq(CLUSTER_ID_VALUE), anyLong(), anyBoolean());
        verify(xStreamMapper, times(0)).update(any(UpdateDSLCompleter.class));
    }

    @Test
    public void testCheckMainVersion_NoSnapshot() {
        taskHeartbeat.checkMainVersion(null);
        // Should not throw exception
    }

    @Test
    public void testCheckMainVersion_VersionNotExpired() {
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        clusterSnapshot.setVersion(1L);

        when(binlogTaskConfig.getVersion()).thenReturn(1L);
        taskHeartbeat.checkMainVersion(clusterSnapshot);
        // Should not throw exception or exit
    }

    @Test
    public void testCheckMainVersion_VersionExpired() {
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        clusterSnapshot.setVersion(2L); // newer version

        when(binlogTaskConfig.getVersion()).thenReturn(1L); // older version
        taskHeartbeat.checkMainVersion(clusterSnapshot);
        verify(processExitCallback, times(1)).accept(null);
        // Should cause system exit
    }

    @Test
    public void testCheckSubVersion_NoSnapshot() {
        taskHeartbeat.checkSubVersion(null);
        // Should not throw exception
    }

    @Test
    public void testCheckSubVersion_SubVersionNotExpired() {
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        clusterSnapshot.setSubVersion(1L);

        taskHeartbeat.checkSubVersion(clusterSnapshot);
        // Should not call onSubVersionChange
        // We can't easily verify this without additional mocking
    }

    @Test
    public void testCheckSubVersion_SubVersionExpired() {
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        clusterSnapshot.setSubVersion(2L); // newer sub version

        when(taskRuntimeConfigProvider.getTaskRuntimeConfig()).thenReturn(taskRuntimeConfig);

        taskHeartbeat.checkSubVersion(clusterSnapshot);
        // Should call onSubVersionChange
        // We can't easily verify this without additional mocking
    }

    @Test
    public void testQueryClusterSnapshot_NullConfig() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMock = Mockito.mockStatic(SystemDbConfig.class)) {
            systemDbConfigMock.when(() -> SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY))
                .thenReturn(null);
            ClusterSnapshot result = taskHeartbeat.queryClusterSnapshot();
            Assert.assertNull(result);
        }
    }

    @Test
    public void testQueryClusterSnapshot_ValidConfig() {
        ClusterSnapshot snapshot = new ClusterSnapshot();
        snapshot.setVersion(1L);
        snapshot.setSubVersion(1L);
        String jsonString = JSONObject.toJSONString(snapshot);

        try (MockedStatic<SystemDbConfig> systemDbConfigMock = Mockito.mockStatic(SystemDbConfig.class)) {
            systemDbConfigMock.when(() -> SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY))
                .thenReturn(jsonString);
            ClusterSnapshot result = taskHeartbeat.queryClusterSnapshot();
            Assert.assertNotNull(result);
            Assert.assertEquals(1L, result.getVersion());
            Assert.assertEquals(1L, result.getSubVersion().longValue());
        }
    }

    @Test
    public void testOnSubVersionChange_VersionMismatch() {
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        clusterSnapshot.setVersion(2L);
        clusterSnapshot.setSubVersion(2L);

        when(taskRuntimeConfigProvider.getTaskRuntimeConfig()).thenReturn(taskRuntimeConfig);
        when(binlogTaskConfig.getVersion()).thenReturn(1L); // mismatch version

        taskHeartbeat.onSubVersionChange(clusterSnapshot);
        // Should not call notifySubVersionChange
        verify(subVersionChangeCallback, times(0)).onSubVersionChange(anyLong(), anyLong(),
            any(TaskRuntimeConfig.class));
    }

    @Test
    public void testOnSubVersionChange_SubVersionMismatch() {
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        clusterSnapshot.setVersion(1L);
        clusterSnapshot.setSubVersion(2L);

        when(taskRuntimeConfigProvider.getTaskRuntimeConfig()).thenReturn(taskRuntimeConfig);
        when(binlogTaskConfig.getVersion()).thenReturn(1L);
        when(binlogTaskConfig.getSubVersion()).thenReturn(1L); // mismatch sub version

        taskHeartbeat.onSubVersionChange(clusterSnapshot);
        // Should not call notifySubVersionChange
        verify(subVersionChangeCallback, times(0)).onSubVersionChange(anyLong(), anyLong(),
            any(TaskRuntimeConfig.class));
    }

    @Test
    public void testOnSubVersionChange_AllMatch() {
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        clusterSnapshot.setVersion(1L);
        clusterSnapshot.setSubVersion(2L);

        when(taskRuntimeConfigProvider.getTaskRuntimeConfig()).thenReturn(taskRuntimeConfig);
        when(binlogTaskConfig.getVersion()).thenReturn(1L);
        when(binlogTaskConfig.getSubVersion()).thenReturn(2L); // match sub version

        taskHeartbeat.onSubVersionChange(clusterSnapshot);
        // Should call notifySubVersionChange
        // We can't easily verify this without additional mocking
        verify(subVersionChangeCallback, times(1)).onSubVersionChange(anyLong(), anyLong(),
            any(TaskRuntimeConfig.class));
    }

    @Test
    public void testNotifySubVersionChange() {
        doNothing().when(subVersionChangeCallback)
            .onSubVersionChange(anyLong(), anyLong(), any(TaskRuntimeConfig.class));

        TaskRuntimeConfig runtimeConfig = Mockito.mock(TaskRuntimeConfig.class);
        taskHeartbeat.notifySubVersionChange(1L, 2L, runtimeConfig);

        // Verification would be difficult due to threading, but we can at least ensure it doesn't throw exceptions
    }

    @Test
    public void testNotifySubVersionChange_WithExceptionInCallback() {
        doThrow(new RuntimeException("Test exception")).when(subVersionChangeCallback)
            .onSubVersionChange(anyLong(), anyLong(), any(TaskRuntimeConfig.class));

        TaskRuntimeConfig runtimeConfig = Mockito.mock(TaskRuntimeConfig.class);
        taskHeartbeat.notifySubVersionChange(1L, 2L, runtimeConfig);
        // Should handle exceptions properly
        verify(processExitCallback, times(1)).accept(null);
    }

    @Test
    public void testUpdateHeartbeat() {
        TaskHeartbeat taskHeartbeat = spy(taskHeartbeatForDumper);
        doNothing().when(taskHeartbeat).updateDumperHeartbeat();
        taskHeartbeat.updateHeartbeat();
        verify(taskHeartbeat, times(1)).updateDumperHeartbeat();
        verify(taskHeartbeat, never()).updateTaskHeartbeat();
        verify(taskHeartbeat, never()).updateDumperXHeartbeat();

        taskHeartbeat = spy(taskHeartbeatForTask);
        doNothing().when(taskHeartbeat).updateTaskHeartbeat();
        taskHeartbeat.updateHeartbeat();
        verify(taskHeartbeat, never()).updateDumperHeartbeat();
        verify(taskHeartbeat, times(1)).updateTaskHeartbeat();
        verify(taskHeartbeat, never()).updateDumperXHeartbeat();

        taskHeartbeat = spy(taskHeartbeatForDumperX);
        doNothing().when(taskHeartbeat).updateDumperXHeartbeat();
        taskHeartbeat.updateHeartbeat();
        verify(taskHeartbeat, never()).updateDumperHeartbeat();
        verify(taskHeartbeat, never()).updateTaskHeartbeat();
        verify(taskHeartbeat, times(1)).updateDumperXHeartbeat();
    }
}
