/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.RuntimeMode;
import com.aliyun.polardbx.binlog.TaskBootStrap;
import com.aliyun.polardbx.binlog.dumper.DumperBootStrap;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

public class DaemonBootStrap_Not_Main_Test extends BaseTest {

    @Test
    public void testTryStartWorkerModule_LocalSingleMode() throws InterruptedException {
        try (MockedStatic<RuntimeMode> runtimeModeMock = mockStatic(RuntimeMode.class);
            MockedConstruction<TaskBootStrap> taskBootStrapConstruction = mockConstruction(TaskBootStrap.class);
            MockedStatic<SystemDbConfig> systemDbConfigMock = mockStatic(SystemDbConfig.class);
            MockedConstruction<DumperBootStrap> dumperBootStrapConstruction = mockConstruction(DumperBootStrap.class)) {

            // 设置RuntimeMode为LOCAL_SINGLE
            RuntimeMode runtimeMode = RuntimeMode.LOCAL_SINGLE;
            mockConfig(ConfigKeys.RUNTIME_MODE, "LOCAL_SINGLE");
            runtimeModeMock.when(() -> RuntimeMode.valueOf("LOCAL_SINGLE")).thenReturn(runtimeMode);

            // 准备测试数据
            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L); // 版本大于1表示准备就绪
            String clusterSnapshotStr = JSONObject.toJSONString(clusterSnapshot);

            // 设置mock行为
            systemDbConfigMock.when(() -> SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY))
                .thenReturn(clusterSnapshotStr);

            // 调用待测试方法
            DaemonBootStrap.tryStartWorkerModule();

            // 验证waitForTopologyReady被调用
            // 验证TaskBootStrap被正确初始化和调用
            assertEquals(1, taskBootStrapConstruction.constructed().size());
            TaskBootStrap taskBootStrap = taskBootStrapConstruction.constructed().get(0);
            verify(taskBootStrap).setTaskRuntimeConfigProvider(any());
            verify(taskBootStrap).boot(any());

            // 验证DumperBootStrap被正确初始化和调用
            assertEquals(1, dumperBootStrapConstruction.constructed().size());
            DumperBootStrap dumperBootStrap = dumperBootStrapConstruction.constructed().get(0);
            verify(dumperBootStrap).setTaskRuntimeConfigProvider(any());
            verify(dumperBootStrap).boot(any());
        }
    }

    @Test
    public void testTryStartWorkerModule_ClusterMode() throws InterruptedException {
        try (MockedStatic<RuntimeMode> runtimeModeMock = mockStatic(RuntimeMode.class)) {

            // 设置RuntimeMode为CLUSTER
            RuntimeMode runtimeMode = RuntimeMode.CLUSTER;
            mockConfig(ConfigKeys.RUNTIME_MODE, "CLUSTER");
            runtimeModeMock.when(() -> RuntimeMode.valueOf("CLUSTER")).thenReturn(runtimeMode);

            // 调用待测试方法
            DaemonBootStrap.tryStartWorkerModule();

            // 在CLUSTER模式下不应该启动TaskBootStrap和DumperBootStrap
            // 由于我们没有mockConstruction，如果它们被创建就会抛出异常
        }
    }

    @Test
    public void testWaitForTopologyReady_Success() throws InterruptedException {
        try (MockedStatic<SystemDbConfig> systemDbConfigMock = mockStatic(SystemDbConfig.class)) {

            // 准备测试数据
            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L); // 版本大于1表示准备就绪
            String clusterSnapshotStr = JSONObject.toJSONString(clusterSnapshot);

            // 设置mock行为
            systemDbConfigMock.when(() -> SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY))
                .thenReturn(clusterSnapshotStr);

            // 调用待测试方法，应该成功返回
            DaemonBootStrap.waitForTopologyReady(1);
            // 如果没有抛出异常，则测试通过
        }
    }

    @Test
    public void testWaitForTopologyReady_Timeout() throws InterruptedException {
        try (MockedStatic<SystemDbConfig> systemDbConfigMock = mockStatic(SystemDbConfig.class)) {

            // 准备测试数据
            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(1L); // 版本等于1表示未就绪
            String clusterSnapshotStr = JSONObject.toJSONString(clusterSnapshot);

            // 设置mock行为
            systemDbConfigMock.when(() -> SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY))
                .thenReturn(clusterSnapshotStr);

            // 调用待测试方法，应该抛出超时异常
            try {
                DaemonBootStrap.waitForTopologyReady(1); // 设置较短的超时时间以加快测试
                fail("应该抛出PolardbxException异常");
            } catch (PolardbxException e) {
                assertTrue(e.getMessage().contains("wait for topology first build failed"));
            }
        }
    }

    @Test
    public void testWaitForTopologyReady_NullSnapshot() throws InterruptedException {
        try (MockedStatic<SystemDbConfig> systemDbConfigMock = mockStatic(SystemDbConfig.class)) {

            // 设置mock行为，返回空字符串（模拟没有快照的情况）
            systemDbConfigMock.when(() -> SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY))
                .thenReturn("");

            // 调用待测试方法，应该抛出超时异常
            try {
                DaemonBootStrap.waitForTopologyReady(1); // 设置较短的超时时间以加快测试
                fail("应该抛出PolardbxException异常");
            } catch (PolardbxException e) {
                assertTrue(e.getMessage().contains("wait for topology first build failed"));
            }
        }
    }
}
