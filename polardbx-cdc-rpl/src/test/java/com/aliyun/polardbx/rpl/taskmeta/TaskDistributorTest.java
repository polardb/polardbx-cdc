/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.monitor.MonitorType;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.Resource;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class TaskDistributorTest extends BaseTest {

    @Before
    public void setUp() {
        setConfig("cluster_id", "test_cluster");
        setConfig(ConfigKeys.RPL_RESOURCE_USE_RATIO, "0.95");
        setConfig("rpl_support_running_check", "true");
        setConfig("rpl_task_keep_alive_interval_seconds", "300");
        setConfig(ConfigKeys.INST_IP, "1.1.1.1");
        setConfig(ConfigKeys.TASK_NAME, "123");
    }

    @Test
    public void distributeTasks_NeedsRebalance_RebalancesTasks_And_RunningTasks() {

        // 模拟资源管理器
        ResourceManager resourceManager = mock(ResourceManager.class);
        List<Container> containers = new ArrayList<>();
        Container container1 = Container.builder().hostString("worker1")
            .capability(Resource.builder().memory_mb(1024).build()).build();
        Container container2 = Container.builder().hostString("worker2")
            .capability(Resource.builder().memory_mb(2048).build()).build();
        Container container3 = Container.builder().hostString("worker3")
            .capability(Resource.builder().memory_mb(10240).build()).build();
        containers.add(container1);
        containers.add(container2);
        containers.add(container3);
        when(resourceManager.availableContainers()).thenReturn(containers);

        List<RplTask> runningTasks = new ArrayList<>();
        RplTask task1 = new RplTask();
        task1.setId(1L);
        task1.setWorker("worker1");
        task1.setType("FULL_COPY");
        task1.setStatus(TaskStatus.RUNNING.name());
        runningTasks.add(task1);

        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManager = mockStatic(DbTaskMetaManager.class)) {
            dbTaskMetaManager.when(() -> DbTaskMetaManager.listClusterTask(TaskStatus.RUNNING, "test_cluster"))
                .thenReturn(runningTasks);
            dbTaskMetaManager.when(() -> DbTaskMetaManager.getTaskMemory(task1.getId())).thenReturn(512);
            TaskDistributor.RESOURCE_MANAGER = resourceManager;
            TaskDistributor.containerMemory = new HashMap<>();
            // rebalance for running tasks
            // worker1 -> 余量最多的worker3
            TaskDistributor.distributeTasks();
            dbTaskMetaManager.verify(() -> DbTaskMetaManager.updateTaskWorker(eq(1L), eq("worker3")), times(1));

            // direct fixed worker for running tasks without distribution
            // worker1 -> worker1
            TaskDistributor.distributeTasks();
            dbTaskMetaManager.verify(() -> DbTaskMetaManager.updateTaskWorker(eq(1L), eq("worker1")), times(0));

            // rebalance when insufficient resources
            // worker1 -> 余量最多的worker3
            RplTask task2 = new RplTask();
            task2.setId(2L);
            task2.setWorker("worker2");
            task2.setType("FULL_COPY");
            task2.setStatus(TaskStatus.RUNNING.name());
            runningTasks.add(task2);
            dbTaskMetaManager.when(() -> DbTaskMetaManager.getTaskMemory(task2.getId())).thenReturn(5120);
            TaskDistributor.distributeTasks();
            dbTaskMetaManager.verify(() -> DbTaskMetaManager.updateTaskWorker(eq(2L), eq("worker3")), times(1));
        }
    }

    @Test
    public void distributeTasks_InsufficientResources_TriggersAlarm() {

        // 模拟资源管理器
        ResourceManager resourceManager = mock(ResourceManager.class);
        List<Container> containers = new ArrayList<>();
        Container container1 = Container.builder().hostString("worker1")
            .capability(Resource.builder().memory_mb(1024).build()).build();
        Container container2 = Container.builder().hostString("worker2")
            .capability(Resource.builder().memory_mb(2048).build()).build();
        containers.add(container1);
        containers.add(container2);
        when(resourceManager.availableContainers()).thenReturn(containers);

        List<RplTask> runningTasks = new ArrayList<>();
        RplTask task1 = new RplTask();
        task1.setId(1L);
        task1.setWorker("worker1");
        task1.setType("FULL_COPY");
        task1.setStatus(TaskStatus.RUNNING.name());
        runningTasks.add(task1);

        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManager = mockStatic(DbTaskMetaManager.class);
            MockedStatic<MonitorManager> monitorManager = mockStatic(MonitorManager.class)) {
            dbTaskMetaManager.when(() -> DbTaskMetaManager.listClusterTask(TaskStatus.RUNNING, "test_cluster"))
                .thenReturn(runningTasks);
            MonitorManager monitorManager1 = mock(MonitorManager.class);
            monitorManager.when(MonitorManager::getInstance).thenReturn(monitorManager1);
            dbTaskMetaManager.when(() -> DbTaskMetaManager.getTaskMemory(task1.getId())).thenReturn(30000);
            TaskDistributor.RESOURCE_MANAGER = resourceManager;
            TaskDistributor.distributeTasks();
            verify(monitorManager1).triggerAlarm(eq(MonitorType.RPL_RESOURCE_NOT_ENOUGH_ERROR), eq(1L));
        }
    }

    @Test
    public void distributeTasks_Ready_And_Restart_Tasks_DistributesSuccessfully() {

        // 模拟资源管理器
        ResourceManager resourceManager = mock(ResourceManager.class);
        List<Container> containers = new ArrayList<>();
        Container container1 = Container.builder().hostString("worker1")
            .capability(Resource.builder().memory_mb(1024).build()).build();
        Container container2 = Container.builder().hostString("worker2")
            .capability(Resource.builder().memory_mb(2048).build()).build();
        Container container3 = Container.builder().hostString("worker3")
            .capability(Resource.builder().memory_mb(10240).build()).build();
        containers.add(container1);
        containers.add(container2);
        containers.add(container3);
        when(resourceManager.availableContainers()).thenReturn(containers);

        List<RplTask> readyTasks = new ArrayList<>();
        List<RplTask> restartTasks = new ArrayList<>();
        RplTask task1 = new RplTask();
        task1.setId(1L);
        task1.setWorker("worker1");
        task1.setType("FULL_COPY");
        task1.setStatus(TaskStatus.READY.name());
        readyTasks.add(task1);

        RplTask task2 = new RplTask();
        task2.setId(2L);
        task2.setWorker("worker4");
        task2.setType("FULL_COPY");
        task2.setStatus(TaskStatus.RESTART.name());
        restartTasks.add(task2);

        RplTask task3 = new RplTask();
        task3.setId(3L);
        task3.setWorker("worker3");
        task3.setType("FULL_COPY");
        task3.setStatus(TaskStatus.RESTART.name());
        restartTasks.add(task3);

        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManager = mockStatic(DbTaskMetaManager.class)) {
            dbTaskMetaManager.when(() -> DbTaskMetaManager.listClusterTask(TaskStatus.READY, "test_cluster"))
                .thenReturn(readyTasks);
            dbTaskMetaManager.when(() -> DbTaskMetaManager.listClusterTask(TaskStatus.RESTART, "test_cluster"))
                .thenReturn(restartTasks);
            double ratio = DynamicApplicationConfig.getDouble(ConfigKeys.RPL_RESOURCE_USE_RATIO);
            dbTaskMetaManager.when(() -> DbTaskMetaManager.getTaskMemory(task1.getId()))
                .thenReturn((int) (10240 * ratio) - 100);
            dbTaskMetaManager.when(() -> DbTaskMetaManager.getTaskMemory(task2.getId())).thenReturn(1500);
            dbTaskMetaManager.when(() -> DbTaskMetaManager.getTaskMemory(task3.getId())).thenReturn(512);
            TaskDistributor.RESOURCE_MANAGER = resourceManager;
            // rebalance for ready tasks
            TaskDistributor.distributeTasks();
            dbTaskMetaManager.verify(() -> DbTaskMetaManager.updateTaskWorker(eq(1L), eq("worker3")), times(1));
            dbTaskMetaManager.verify(() -> DbTaskMetaManager.updateTaskWorker(eq(2L), eq("worker2")), times(1));
        }
    }
}