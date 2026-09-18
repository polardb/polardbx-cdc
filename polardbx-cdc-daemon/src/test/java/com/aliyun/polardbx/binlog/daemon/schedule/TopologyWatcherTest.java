/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.BinlogXTopologyService;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.ColumnarTopologyService;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.GlobalBinlogTopologyService;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyService;
import com.aliyun.polardbx.binlog.dao.SystemConfigInfoMapper;
import com.aliyun.polardbx.binlog.enums.ClusterType;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class TopologyWatcherTest extends BaseTest {

    @Mock
    private SystemConfigInfoMapper systemConfigInfoMapper;

    @Mock
    private TopologyService topologyService;

    private MockedStatic<RuntimeLeaderElector> mockedRuntimeLeaderElector;
    private MockedStatic<SpringContextHolder> mockedSpringContextHolder;
    private MockedStatic<MonitorManager> mockedMonitorManager;

    private TopologyWatcher topologyWatcher;

    @Before
    public void setUp() {
        mockedRuntimeLeaderElector = mockStatic(RuntimeLeaderElector.class);
        mockedSpringContextHolder = mockStatic(SpringContextHolder.class);
        mockedMonitorManager = mockStatic(MonitorManager.class);

        mockedSpringContextHolder.when(() -> SpringContextHolder.getObject(SystemConfigInfoMapper.class))
            .thenReturn(systemConfigInfoMapper);

        // 创建TopologyWatcher实例，但需要mock掉getTopologyService方法
        topologyWatcher = new TestTopologyWatcher("test_cluster_id", ClusterType.BINLOG.name(), "test_name", 1000);
    }

    @After
    public void tearDown() {
        mockedRuntimeLeaderElector.close();
        mockedSpringContextHolder.close();
        mockedMonitorManager.close();
    }

    @Test
    public void testExec_NotLeader() throws Throwable {
        mockedRuntimeLeaderElector.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(false);

        topologyWatcher.exec();

        // 验证没有执行初始化和构建操作
        verify(systemConfigInfoMapper, never()).insertSelective(any());
        verify(topologyService, never()).tryBuild();
    }

    @Test
    public void testExec_Leader_InitSuccess() throws Throwable {
        mockedRuntimeLeaderElector.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(true);
        doNothing().when(topologyService).tryBuild();

        topologyWatcher.exec();

        // 验证执行了初始化和构建操作
        verify(systemConfigInfoMapper, times(1)).insertSelective(any());
        verify(topologyService, times(1)).tryBuild();
    }

    @Test
    public void testExec_Leader_InitAlreadyExists() throws Throwable {
        mockedRuntimeLeaderElector.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(true);
        doThrow(new DataIntegrityViolationException("Duplicate entry"))
            .when(systemConfigInfoMapper).insertSelective(any());
        doNothing().when(topologyService).tryBuild();

        topologyWatcher.exec();

        // 验证即使初始化失败也执行了构建操作
        verify(topologyService, times(1)).tryBuild();
    }

    @Test(expected = PolardbxException.class)
    public void testExec_Leader_BuildFailure() throws Throwable {
        mockedRuntimeLeaderElector.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(true);
        doThrow(new RuntimeException("Build failed")).when(topologyService).tryBuild();
        MonitorManager mockMonitorManager = mock(MonitorManager.class);
        mockedMonitorManager.when(MonitorManager::getInstance).thenReturn(mockMonitorManager);

        topologyWatcher.exec();
    }

    @Test
    public void testGetTopologyService_Binlog() {
        AtomicReference<GlobalBinlogTopologyService> globalBinlogTopologyService = new AtomicReference<>();
        try (MockedConstruction<GlobalBinlogTopologyService> mockedConstruction = mockConstruction(
            GlobalBinlogTopologyService.class,
            (mock, context) -> globalBinlogTopologyService.set(mock))) {

            TopologyWatcher watcher =
                new TopologyWatcher("test_cluster_id", ClusterType.BINLOG.name(), "test_name", 1000);
            TopologyService result = watcher.getTopologyService();
            assertSame(result, globalBinlogTopologyService.get());
        }
    }

    @Test
    public void testGetTopologyService_BinlogX() {
        AtomicReference<BinlogXTopologyService> binlogXTopologyService = new AtomicReference<>();
        try (MockedConstruction<BinlogXTopologyService> mockedConstruction = mockConstruction(
            BinlogXTopologyService.class,
            (mock, context) -> binlogXTopologyService.set(mock))) {
            TopologyWatcher watcher =
                new TopologyWatcher("test_cluster_id", ClusterType.BINLOG_X.name(), "test_name", 1000);
            TopologyService result = watcher.getTopologyService();
            assertSame(result, binlogXTopologyService.get());
        }
    }

    @Test
    public void testGetTopologyService_Columnar() {
        AtomicReference<ColumnarTopologyService> columnarTopologyService = new AtomicReference<>();
        try (MockedConstruction<ColumnarTopologyService> mockedConstruction = mockConstruction(
            ColumnarTopologyService.class,
            (mock, context) -> columnarTopologyService.set(mock))) {
            TopologyWatcher watcher =
                new TopologyWatcher("test_cluster_id", ClusterType.COLUMNAR.name(), "test_name", 1000);
            TopologyService result = watcher.getTopologyService();
            assertSame(result, columnarTopologyService.get());
        }
    }

    // 创建一个测试子类来mock getTopologyService 方法
    private class TestTopologyWatcher extends TopologyWatcher {
        public TestTopologyWatcher(String clusterId, String clusterType, String name, int interval) {
            super(clusterId, clusterType, name, interval);
        }

        @Override
        TopologyService getTopologyService() {
            return topologyService;
        }
    }
}
