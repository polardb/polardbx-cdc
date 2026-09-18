/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.extractor.DnHealthCheckerManager;
import com.aliyun.polardbx.binlog.metrics.MetricsManager;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.rpc.TxnStreamRpcServer;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.springframework.dao.DuplicateKeyException;

import java.io.IOException;
import java.util.Optional;
import java.util.function.Consumer;

import static com.aliyun.polardbx.binlog.ConfigKeys.INST_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.INST_IP;
import static com.aliyun.polardbx.binlog.ConfigKeys.POLARX_INST_ID;
import static com.aliyun.polardbx.binlog.ConfigKeys.RUNTIME_MODE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskControllerTest extends BaseTest {

    private String cluster;
    private TaskRuntimeConfig taskRuntimeConfig;
    private TaskRuntimeConfigProvider taskRuntimeConfigProvider;

    @Before
    public void setUp() {
        cluster = "test_cluster";
        taskRuntimeConfigProvider = mock(TaskRuntimeConfigProvider.class);

        // 初始化TaskRuntimeConfig
        taskRuntimeConfig = new TaskRuntimeConfig();
        taskRuntimeConfig.setName("test_task");
        taskRuntimeConfig.setType(TaskType.Final);
        taskRuntimeConfig.setServerPort(8080);

        BinlogTaskConfig binlogTaskConfig = new BinlogTaskConfig();
        binlogTaskConfig.setVersion(1L);
        binlogTaskConfig.setSubVersion(1L);
        taskRuntimeConfig.setBinlogTaskConfig(binlogTaskConfig);

        // 配置mock
        mockConfig(INST_IP, "127.0.0.1");
        mockConfig(INST_ID, "test_container");
        mockConfig(POLARX_INST_ID, "test_polarx");
        mockConfig(RUNTIME_MODE, "LOCAL");
    }

    @Test
    public void testConstructor() {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class);
            MockedStatic<RuntimeMode> runtimeModeMock = mockStatic(RuntimeMode.class)) {

            BinlogTaskInfoMapper taskInfoMapper = mock(BinlogTaskInfoMapper.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject(BinlogTaskInfoMapper.class))
                .thenReturn(taskInfoMapper);

            when(taskInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());
            when(taskInfoMapper.insert(any(BinlogTaskInfo.class))).thenReturn(1);

            runtimeModeMock.when(() -> RuntimeMode.valueOf(anyString())).thenReturn(RuntimeMode.LOCAL);
            runtimeModeMock.when(() -> RuntimeMode.isLocalMode(any())).thenReturn(true);

            TaskController taskController = new TaskController(cluster, taskRuntimeConfig, taskRuntimeConfigProvider);
        }
    }

    @Test
    public void testStart() throws IOException {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class);
            MockedConstruction<TaskEngine> taskEngineConstruction = mockConstruction(TaskEngine.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedConstruction<TxnStreamRpcServer> rpcServerConstruction = mockConstruction(TxnStreamRpcServer.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedStatic<MonitorManager> monitorManagerMock = mockStatic(MonitorManager.class);
            MockedConstruction<MetricsManager> metricsManagerConstruction = mockConstruction(MetricsManager.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedStatic<RuntimeMode> runtimeModeMock = mockStatic(RuntimeMode.class)) {

            BinlogTaskInfoMapper taskInfoMapper = mock(BinlogTaskInfoMapper.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject(BinlogTaskInfoMapper.class))
                .thenReturn(taskInfoMapper);
            when(taskInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());
            when(taskInfoMapper.insert(any(BinlogTaskInfo.class))).thenReturn(1);

            DnHealthCheckerManager checker = mock(DnHealthCheckerManager.class);
            doNothing().when(checker).start();
            doNothing().when(checker).stop();
            springContextHolderMock.when(() -> SpringContextHolder.getObject(DnHealthCheckerManager.class))
                .thenReturn(checker);

            MonitorManager monitorManager = mock(MonitorManager.class);
            monitorManagerMock.when(MonitorManager::getInstance).thenReturn(monitorManager);
            doNothing().when(monitorManager).startup();
            doNothing().when(monitorManager).shutdown();

            runtimeModeMock.when(() -> RuntimeMode.valueOf(anyString())).thenReturn(RuntimeMode.LOCAL);
            runtimeModeMock.when(() -> RuntimeMode.isLocalMode(any())).thenReturn(true);

            TaskController taskController = new TaskController(cluster, taskRuntimeConfig, taskRuntimeConfigProvider);
            taskController.start();

            // 验证组件启动方法被调用
            verify(taskEngineConstruction.constructed().get(0), times(1)).start();
            verify(rpcServerConstruction.constructed().get(0), times(1)).start();
            verify(checker, times(1)).start();
            verify(metricsManagerConstruction.constructed().get(0), times(1)).start();
            verify(monitorManager, times(1)).startup();
        }
    }

    @Test
    public void testStop() throws IOException, InterruptedException {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class);
            MockedConstruction<TaskEngine> taskEngineConstruction = mockConstruction(TaskEngine.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedConstruction<TxnStreamRpcServer> rpcServerConstruction = mockConstruction(TxnStreamRpcServer.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedStatic<MonitorManager> monitorManagerMock = mockStatic(MonitorManager.class);
            MockedConstruction<MetricsManager> metricsManagerConstruction = mockConstruction(MetricsManager.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedStatic<RuntimeMode> runtimeModeMock = mockStatic(RuntimeMode.class)) {

            BinlogTaskInfoMapper taskInfoMapper = mock(BinlogTaskInfoMapper.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject(BinlogTaskInfoMapper.class))
                .thenReturn(taskInfoMapper);
            when(taskInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());
            when(taskInfoMapper.insert(any(BinlogTaskInfo.class))).thenReturn(1);

            DnHealthCheckerManager checker = mock(DnHealthCheckerManager.class);
            doNothing().when(checker).start();
            doNothing().when(checker).stop();
            springContextHolderMock.when(() -> SpringContextHolder.getObject(DnHealthCheckerManager.class))
                .thenReturn(checker);

            MonitorManager monitorManager = mock(MonitorManager.class);
            monitorManagerMock.when(MonitorManager::getInstance).thenReturn(monitorManager);
            doNothing().when(monitorManager).startup();
            doNothing().when(monitorManager).shutdown();

            runtimeModeMock.when(() -> RuntimeMode.valueOf(anyString())).thenReturn(RuntimeMode.LOCAL);
            runtimeModeMock.when(() -> RuntimeMode.isLocalMode(any())).thenReturn(true);

            TaskController taskController = new TaskController(cluster, taskRuntimeConfig, taskRuntimeConfigProvider);

            // 先启动
            taskController.start();

            // 重置验证状态
            clearInvocations(taskEngineConstruction.constructed().get(0));
            clearInvocations(rpcServerConstruction.constructed().get(0));
            clearInvocations(checker);
            clearInvocations(metricsManagerConstruction.constructed().get(0));
            clearInvocations(monitorManager);

            // 执行停止
            taskController.stop();

            // 验证组件停止方法被调用
            verify(taskEngineConstruction.constructed().get(0), times(1)).stop();
            verify(rpcServerConstruction.constructed().get(0), times(1)).stop();
            verify(checker, times(1)).stop();
            verify(metricsManagerConstruction.constructed().get(0), times(1)).stop();
            verify(monitorManager, times(1)).shutdown();
        }
    }

    @Test
    public void testReload() throws IOException {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class);
            MockedConstruction<TaskEngine> taskEngineConstruction = mockConstruction(TaskEngine.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                    doNothing().when(mock).setTaskRuntimeConfig(any());
                });
            MockedConstruction<TxnStreamRpcServer> rpcServerConstruction = mockConstruction(TxnStreamRpcServer.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                    doNothing().when(mock).setSubVersion(anyLong());
                });
            MockedStatic<MonitorManager> monitorManagerMock = mockStatic(MonitorManager.class);
            MockedConstruction<MetricsManager> metricsManagerConstruction = mockConstruction(MetricsManager.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedStatic<RuntimeMode> runtimeModeMock = mockStatic(RuntimeMode.class)) {

            BinlogTaskInfoMapper taskInfoMapper = mock(BinlogTaskInfoMapper.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject(BinlogTaskInfoMapper.class))
                .thenReturn(taskInfoMapper);
            when(taskInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());
            when(taskInfoMapper.insert(any(BinlogTaskInfo.class))).thenReturn(1);

            DnHealthCheckerManager checker = mock(DnHealthCheckerManager.class);
            doNothing().when(checker).start();
            doNothing().when(checker).stop();
            springContextHolderMock.when(() -> SpringContextHolder.getObject(DnHealthCheckerManager.class))
                .thenReturn(checker);

            MonitorManager monitorManager = mock(MonitorManager.class);
            monitorManagerMock.when(MonitorManager::getInstance).thenReturn(monitorManager);
            doNothing().when(monitorManager).startup();
            doNothing().when(monitorManager).shutdown();

            runtimeModeMock.when(() -> RuntimeMode.valueOf(anyString())).thenReturn(RuntimeMode.LOCAL);
            runtimeModeMock.when(() -> RuntimeMode.isLocalMode(any())).thenReturn(true);

            TaskController taskController = new TaskController(cluster, taskRuntimeConfig, taskRuntimeConfigProvider);
            taskController.start();

            // 创建新的配置
            TaskRuntimeConfig newConfig = new TaskRuntimeConfig();
            newConfig.setName("test_task");
            newConfig.setType(TaskType.Final);
            newConfig.setServerPort(8080);

            BinlogTaskConfig newBinlogTaskConfig = new BinlogTaskConfig();
            newBinlogTaskConfig.setVersion(2L);
            newBinlogTaskConfig.setSubVersion(2L);
            newConfig.setBinlogTaskConfig(newBinlogTaskConfig);

            // 执行重新加载
            taskController.reload(newConfig);

            // 验证方法被调用
            verify(taskEngineConstruction.constructed().get(0), atLeastOnce()).setTaskRuntimeConfig(newConfig);
            verify(rpcServerConstruction.constructed().get(0), atLeastOnce()).setSubVersion(2L);
        }
    }

    @Test
    public void testStartWhenAlreadyRunning() throws IOException {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class);
            MockedConstruction<TaskEngine> taskEngineConstruction = mockConstruction(TaskEngine.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedConstruction<TxnStreamRpcServer> rpcServerConstruction = mockConstruction(TxnStreamRpcServer.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedStatic<MonitorManager> monitorManagerMock = mockStatic(MonitorManager.class);
            MockedConstruction<MetricsManager> metricsManagerConstruction = mockConstruction(MetricsManager.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedStatic<RuntimeMode> runtimeModeMock = mockStatic(RuntimeMode.class)) {

            BinlogTaskInfoMapper taskInfoMapper = mock(BinlogTaskInfoMapper.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject(BinlogTaskInfoMapper.class))
                .thenReturn(taskInfoMapper);
            when(taskInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());
            when(taskInfoMapper.insert(any(BinlogTaskInfo.class))).thenReturn(1);

            DnHealthCheckerManager checker = mock(DnHealthCheckerManager.class);
            doNothing().when(checker).start();
            doNothing().when(checker).stop();
            springContextHolderMock.when(() -> SpringContextHolder.getObject(DnHealthCheckerManager.class))
                .thenReturn(checker);

            MonitorManager monitorManager = mock(MonitorManager.class);
            monitorManagerMock.when(MonitorManager::getInstance).thenReturn(monitorManager);
            doNothing().when(monitorManager).startup();
            doNothing().when(monitorManager).shutdown();

            runtimeModeMock.when(() -> RuntimeMode.valueOf(anyString())).thenReturn(RuntimeMode.LOCAL);
            runtimeModeMock.when(() -> RuntimeMode.isLocalMode(any())).thenReturn(true);

            TaskController taskController = new TaskController(cluster, taskRuntimeConfig, taskRuntimeConfigProvider);
            taskController.start();

            // 重置验证状态
            clearInvocations(taskEngineConstruction.constructed().get(0));
            clearInvocations(rpcServerConstruction.constructed().get(0));
            clearInvocations(checker);
            clearInvocations(metricsManagerConstruction.constructed().get(0));
            clearInvocations(monitorManager);

            // 再次启动，应该不会重复调用组件的启动方法
            taskController.start();

            // 验证组件启动方法没有被再次调用
            verify(taskEngineConstruction.constructed().get(0), times(0)).start();
            verify(rpcServerConstruction.constructed().get(0), times(0)).start();
        }
    }

    @Test
    public void testStopWhenNotRunning() {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class);
            MockedStatic<RuntimeMode> runtimeModeMock = mockStatic(RuntimeMode.class)) {

            BinlogTaskInfoMapper taskInfoMapper = mock(BinlogTaskInfoMapper.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject(BinlogTaskInfoMapper.class))
                .thenReturn(taskInfoMapper);
            when(taskInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());
            when(taskInfoMapper.insert(any(BinlogTaskInfo.class))).thenReturn(1);

            runtimeModeMock.when(() -> RuntimeMode.valueOf(anyString())).thenReturn(RuntimeMode.LOCAL);
            runtimeModeMock.when(() -> RuntimeMode.isLocalMode(any())).thenReturn(true);

            TaskController taskController = new TaskController(cluster, taskRuntimeConfig, taskRuntimeConfigProvider);

            // 停止一个未运行的控制器，不应该抛出异常
            taskController.stop();
        }
    }

    @Test
    public void testBuildWithExistingTaskInfo() {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class);
            MockedStatic<RuntimeMode> runtimeModeMock = mockStatic(RuntimeMode.class)) {

            BinlogTaskInfoMapper taskInfoMapper = mock(BinlogTaskInfoMapper.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject(BinlogTaskInfoMapper.class))
                .thenReturn(taskInfoMapper);

            BinlogTaskInfo existingTaskInfo = new BinlogTaskInfo();
            existingTaskInfo.setId(1L);
            existingTaskInfo.setVersion(0L);
            when(taskInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.of(existingTaskInfo));
            when(taskInfoMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

            runtimeModeMock.when(() -> RuntimeMode.valueOf(anyString())).thenReturn(RuntimeMode.LOCAL);
            runtimeModeMock.when(() -> RuntimeMode.isLocalMode(any())).thenReturn(true);

            TaskController taskController = new TaskController(cluster, taskRuntimeConfig, taskRuntimeConfigProvider);

            verify(taskInfoMapper, times(1)).updateByPrimaryKeySelective(any());
        }
    }

    @Test
    public void testBuildWithDuplicateKeyException() {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class);
            MockedStatic<RuntimeMode> runtimeModeMock = mockStatic(RuntimeMode.class)) {

            BinlogTaskInfoMapper taskInfoMapper = mock(BinlogTaskInfoMapper.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject(BinlogTaskInfoMapper.class))
                .thenReturn(taskInfoMapper);

            when(taskInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());
            when(taskInfoMapper.insert(any(BinlogTaskInfo.class))).thenThrow(
                new DuplicateKeyException("Duplicate key"));

            runtimeModeMock.when(() -> RuntimeMode.valueOf(anyString())).thenReturn(RuntimeMode.LOCAL);
            runtimeModeMock.when(() -> RuntimeMode.isLocalMode(any())).thenReturn(true);

            Consumer processExitCallback = mock(Consumer.class);
            // 由于会调用Runtime.getRuntime().halt(1)，我们只验证是否能正常构造对象
            TaskController taskController =
                new TaskController(cluster, taskRuntimeConfig, taskRuntimeConfigProvider, processExitCallback);
            verify(processExitCallback, times(1)).accept(null);
        }
    }

    @Test
    public void testDoStopWithInterruptedException() throws IOException {
        try (MockedStatic<SpringContextHolder> springContextHolderMock = mockStatic(SpringContextHolder.class);
            MockedConstruction<TaskEngine> taskEngineConstruction = mockConstruction(TaskEngine.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedConstruction<TxnStreamRpcServer> rpcServerConstruction = mockConstruction(TxnStreamRpcServer.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doThrow(new InterruptedException()).when(mock).stop();
                });
            MockedStatic<MonitorManager> monitorManagerMock = mockStatic(MonitorManager.class);
            MockedConstruction<MetricsManager> metricsManagerConstruction = mockConstruction(MetricsManager.class,
                (mock, context) -> {
                    doNothing().when(mock).start();
                    doNothing().when(mock).stop();
                });
            MockedStatic<RuntimeMode> runtimeModeMock = mockStatic(RuntimeMode.class)) {

            BinlogTaskInfoMapper taskInfoMapper = mock(BinlogTaskInfoMapper.class);
            springContextHolderMock.when(() -> SpringContextHolder.getObject(BinlogTaskInfoMapper.class))
                .thenReturn(taskInfoMapper);
            when(taskInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());
            when(taskInfoMapper.insert(any(BinlogTaskInfo.class))).thenReturn(1);

            DnHealthCheckerManager checker = mock(DnHealthCheckerManager.class);
            doNothing().when(checker).start();
            doNothing().when(checker).stop();
            springContextHolderMock.when(() -> SpringContextHolder.getObject(DnHealthCheckerManager.class))
                .thenReturn(checker);

            MonitorManager monitorManager = mock(MonitorManager.class);
            monitorManagerMock.when(MonitorManager::getInstance).thenReturn(monitorManager);
            doNothing().when(monitorManager).startup();
            doNothing().when(monitorManager).shutdown();

            runtimeModeMock.when(() -> RuntimeMode.valueOf(anyString())).thenReturn(RuntimeMode.LOCAL);
            runtimeModeMock.when(() -> RuntimeMode.isLocalMode(any())).thenReturn(true);

            TaskController taskController = new TaskController(cluster, taskRuntimeConfig, taskRuntimeConfigProvider);
            taskController.start();

            // 停止时应该能正常处理InterruptedException
            taskController.stop();
        }
    }
}
