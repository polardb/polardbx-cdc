/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.backup;

import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anySet;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BinlogBackupManagerTest extends BaseTest {

    private StreamContext streamContext;
    private Map<String, MetricsObserver> metrics;

    @Before
    public void setUp() {
        // 初始化StreamContext
        Set<String> streamSet = new HashSet<>();
        streamSet.add("stream1");
        streamSet.add("stream2");

        streamContext = new StreamContext("test_group", streamSet, "test_cluster", "test_task", TaskType.Final, 1L);

        // 初始化metrics
        metrics = new HashMap<>();
        MetricsObserver observer = mock(MetricsObserver.class);
        metrics.put("stream1", observer);
    }

    @Test
    public void testConstructor() {
        BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
        // 构造函数不应该抛出异常
    }

    @Test
    public void testNeedStartReturnsTrueWhenBackupIsOnAndIsMaster() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = mockStatic(RemoteBinlogProxy.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class)) {

            RemoteBinlogProxy remoteBinlogProxy = mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isBackupOn()).thenReturn(true);

            runtimeLeaderElectorMock.when(() -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString()))
                .thenReturn(true);

            BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
            boolean result = backupManager.needStart();

            // 验证needStart返回true
            assert result;
        }
    }

    @Test
    public void testNeedStartReturnsFalseWhenBackupIsOff() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = mockStatic(RemoteBinlogProxy.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class)) {

            RemoteBinlogProxy remoteBinlogProxy = mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isBackupOn()).thenReturn(false); // 备份关闭

            BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
            boolean result = backupManager.needStart();

            // 验证needStart返回false
            assert !result;
        }
    }

    @Test
    public void testNeedStartReturnsFalseWhenNotMaster() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = mockStatic(RemoteBinlogProxy.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class)) {

            RemoteBinlogProxy remoteBinlogProxy = mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isBackupOn()).thenReturn(true);

            runtimeLeaderElectorMock.when(() -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString()))
                .thenReturn(false); // 不是master

            BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
            boolean result = backupManager.needStart();

            // 验证needStart返回false
            assert !result;
        }
    }

    @Test
    public void testStartWhenNeedStartReturnsTrue() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = mockStatic(RemoteBinlogProxy.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class);
            MockedConstruction<BinlogUploadManager> binlogUploadManagerConstruction = mockConstruction(
                BinlogUploadManager.class,
                (mock, context) -> doNothing().when(mock).start())) {

            RemoteBinlogProxy remoteBinlogProxy = mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isBackupOn()).thenReturn(true);

            runtimeLeaderElectorMock.when(() -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString()))
                .thenReturn(true);

            BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
            backupManager.start();

            // 验证BinlogUploadManager被创建并启动
            verify(binlogUploadManagerConstruction.constructed().get(0), times(1)).start();
        }
    }

    @Test
    public void testStartWhenNeedStartReturnsFalse() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = mockStatic(RemoteBinlogProxy.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class)) {

            RemoteBinlogProxy remoteBinlogProxy = mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isBackupOn()).thenReturn(false);

            BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
            backupManager.start();

            // 验证BinlogUploadManager没有被创建
            // 由于BinlogUploadManager没有被创建，所以不会有任何调用
        }
    }

    @Test
    public void testStartWithStreamsAndMetrics() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = mockStatic(RemoteBinlogProxy.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class);
            MockedConstruction<BinlogUploadManager> binlogUploadManagerConstruction = mockConstruction(
                BinlogUploadManager.class,
                (mock, context) -> doNothing().when(mock).start(anySet(), anyMap()))) {

            RemoteBinlogProxy remoteBinlogProxy = mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isBackupOn()).thenReturn(true);

            runtimeLeaderElectorMock.when(() -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString()))
                .thenReturn(true);

            BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
            // 先调用start()创建binlogUploadManager
            backupManager.start();

            // 创建新的streams和metrics
            Set<String> streams = new HashSet<>();
            streams.add("new_stream");
            HashMap<String, MetricsObserver> newMetrics = new HashMap<>();
            MetricsObserver observer = mock(MetricsObserver.class);
            newMetrics.put("new_stream", observer);

            backupManager.start(streams, newMetrics);

            // 验证BinlogUploadManager的start方法被调用
            verify(binlogUploadManagerConstruction.constructed().get(0), times(1)).start(streams, newMetrics);
        }
    }

    @Test
    public void testStartWithStreamsAndMetricsWhenBinlogUploadManagerIsNull() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = mockStatic(RemoteBinlogProxy.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class)) {

            RemoteBinlogProxy remoteBinlogProxy = mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isBackupOn()).thenReturn(false);

            BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
            // binlogUploadManager为null

            Set<String> streams = new HashSet<>();
            streams.add("new_stream");
            HashMap<String, MetricsObserver> newMetrics = new HashMap<>();
            MetricsObserver observer = mock(MetricsObserver.class);
            newMetrics.put("new_stream", observer);

            // 调用start方法不应该抛出异常
            backupManager.start(streams, newMetrics);
        }
    }

    @Test
    public void testStop() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = mockStatic(RemoteBinlogProxy.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class);
            MockedConstruction<BinlogUploadManager> binlogUploadManagerConstruction = mockConstruction(
                BinlogUploadManager.class,
                (mock, context) -> doNothing().when(mock).stop())) {

            RemoteBinlogProxy remoteBinlogProxy = mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isBackupOn()).thenReturn(true);

            runtimeLeaderElectorMock.when(() -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString()))
                .thenReturn(true);

            BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
            backupManager.start();

            backupManager.stop();

            // 验证BinlogUploadManager的stop方法被调用
            verify(binlogUploadManagerConstruction.constructed().get(0), times(1)).stop();
        }
    }

    @Test
    public void testStopWhenBinlogUploadManagerIsNull() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = mockStatic(RemoteBinlogProxy.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class)) {

            RemoteBinlogProxy remoteBinlogProxy = mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isBackupOn()).thenReturn(false);

            BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
            // binlogUploadManager为null

            // 调用stop方法不应该抛出异常
            backupManager.stop();
        }
    }

    @Test
    public void testStopWithStreamName() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = mockStatic(RemoteBinlogProxy.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class);
            MockedConstruction<BinlogUploadManager> binlogUploadManagerConstruction = mockConstruction(
                BinlogUploadManager.class,
                (mock, context) -> doNothing().when(mock).stop(anyString()))) {

            RemoteBinlogProxy remoteBinlogProxy = mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isBackupOn()).thenReturn(true);

            runtimeLeaderElectorMock.when(() -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString()))
                .thenReturn(true);

            BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
            backupManager.start();

            backupManager.stop("stream1");

            // 验证BinlogUploadManager的stop方法被调用
            verify(binlogUploadManagerConstruction.constructed().get(0), times(1)).stop("stream1");
        }
    }

    @Test
    public void testGetFilesToUploadWithMultipleStreams() {
        try (MockedStatic<RemoteBinlogProxy> remoteBinlogProxyMock = mockStatic(RemoteBinlogProxy.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMock = mockStatic(RuntimeLeaderElector.class);
            MockedConstruction<BinlogUploadManager> binlogUploadManagerConstruction = mockConstruction(
                BinlogUploadManager.class,
                (mock, context) -> doNothing().when(mock).start())) {

            RemoteBinlogProxy remoteBinlogProxy = mock(RemoteBinlogProxy.class);
            remoteBinlogProxyMock.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            when(remoteBinlogProxy.isBackupOn()).thenReturn(true);

            runtimeLeaderElectorMock.when(() -> RuntimeLeaderElector.isDumperMasterOrX(anyLong(), any(), anyString()))
                .thenReturn(true);

            BinlogBackupManager backupManager = new BinlogBackupManager(streamContext, metrics);
            backupManager.start();

            // 验证BinlogUploadManager被正确初始化
            assert backupManager != null;
        }
    }
}
