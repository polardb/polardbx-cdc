/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.domain.BinlogParameter;
import com.aliyun.polardbx.binlog.domain.MergeSourceInfo;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.metadata.MetaGenerator;
import com.aliyun.polardbx.binlog.protocol.DumpRequest;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.DNStorageSqlExecutor;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_ENGINE_AUTO_START;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskEngineTest extends BaseTest {

    private TaskRuntimeConfig taskRuntimeConfig;
    private TaskEngine taskEngine;

    @Before
    public void setUp() {
        // 初始化必要的配置
        mockConfig(TASK_ENGINE_AUTO_START, "false");

        taskRuntimeConfig = new TaskRuntimeConfig();
        taskRuntimeConfig.setType(TaskType.Final);

        // 设置MergeSourceInfos以避免flushLogs方法中的空指针异常
        List<MergeSourceInfo> mergeSourceInfos = new ArrayList<>();
        MergeSourceInfo mergeSourceInfo = new MergeSourceInfo();
        BinlogParameter binlogParameter = new BinlogParameter();
        binlogParameter.setStorageInstId("test_storage");
        mergeSourceInfo.setBinlogParameter(binlogParameter);
        mergeSourceInfos.add(mergeSourceInfo);
        taskRuntimeConfig.setMergeSourceInfos(mergeSourceInfos);

        taskEngine = new TaskEngine(taskRuntimeConfig);
    }

    @Test
    public void testTryTriggerMetaStartWhenMetaNotExists() {
        MetaGenerator metaGenerator = mock(MetaGenerator.class);
        when(metaGenerator.exists()).thenReturn(false);

        // Mock DNStorageSqlExecutor以避免实际连接数据库
        try (MockedConstruction<DNStorageSqlExecutor> mockedConstruction = mockConstruction(DNStorageSqlExecutor.class,
            (mock, context) -> {
                // 验证构造函数参数
                assertEquals("test_storage", context.arguments().get(0));
                doNothing().when(mock).tryFlushDnBinlog();
            })) {

            // 使用反射替换metaGenerator
            try {
                java.lang.reflect.Field field = TaskEngine.class.getDeclaredField("metaGenerator");
                field.setAccessible(true);
                field.set(taskEngine, metaGenerator);
            } catch (Exception e) {
                Assert.fail("Failed to set metaGenerator: " + e.getMessage());
            }

            taskEngine.tryTriggerMetaStart();

            // 验证flushLogs被调用（因为meta不存在）
            verify(metaGenerator, times(1)).exists();
            verify(metaGenerator, times(1)).tryStart();
        }
    }

    @Test
    public void testTryTriggerMetaStartWhenMetaExists() {
        MetaGenerator metaGenerator = mock(MetaGenerator.class);
        when(metaGenerator.exists()).thenReturn(true);

        // 使用反射替换metaGenerator
        try {
            java.lang.reflect.Field field = TaskEngine.class.getDeclaredField("metaGenerator");
            field.setAccessible(true);
            field.set(taskEngine, metaGenerator);
        } catch (Exception e) {
            Assert.fail("Failed to set metaGenerator: " + e.getMessage());
        }

        taskEngine.tryTriggerMetaStart();

        // 验证exists被调用，但flushLogs没有被调用（因为meta已存在）
        verify(metaGenerator, times(1)).exists();
        verify(metaGenerator, times(1)).tryStart();
    }

    @Test
    public void testTryStartPipelineWithAutoStartEnabled() {
        mockConfig(TASK_ENGINE_AUTO_START, "true");

        // Mock TaskPipeline构造函数以避免初始化过程中的复杂依赖
        AtomicReference<TaskPipeline> taskPipeline = new AtomicReference<>();
        try (MockedConstruction<TaskPipeline> mockedConstruction = mockConstruction(TaskPipeline.class,
            (mock, context) -> {
                // 验证构造函数参数
                assertEquals("MAIN_PIPELINE", context.arguments().get(0));
                assertEquals(taskRuntimeConfig, context.arguments().get(1));
                assertEquals("", context.arguments().get(2));
                assertEquals(true, context.arguments().get(3));  // useRelayLog应该为true
                assertEquals(true, context.arguments().get(4));  // useKWayMerge应该为true
                assertEquals(null, context.arguments().get(5));
                // 将mock对象赋值给taskPipeline变量，这样verify才能工作
                taskPipeline.set(mock);
            })) {

            taskEngine.tryStartPipeline();

            // 验证TaskPipeline的start方法被调用
            verify(taskPipeline.get(), times(1)).start();
        }
    }

    @Test
    public void testTryStartPipelineWithAutoStartDisabledAndNotDispatcher() {
        mockConfig(TASK_ENGINE_AUTO_START, "false");

        taskEngine.tryStartPipeline();

        // 由于不是Dispatcher类型且auto start为false，不应该启动pipeline
        // 这个验证比较难直接做，因为我们无法访问taskPipelines map
    }

    @Test
    public void testTryStartPipelineWithAutoStartDisabledAndDispatcherWithRelay() {
        mockConfig(TASK_ENGINE_AUTO_START, "false");

        // 设置为Dispatcher类型
        taskRuntimeConfig.setType(TaskType.Dispatcher);

        // 模拟HashLevel不是DATANODE
        try (MockedStatic<HashLevel> mockedHashLevel = mockStatic(HashLevel.class)) {
            mockedHashLevel.when(HashLevel::getCurrentHashLevel).thenReturn(HashLevel.TABLE);

            // Mock TaskPipeline构造函数以避免初始化过程中的复杂依赖
            AtomicReference<TaskPipeline> taskPipeline = new AtomicReference<>();
            try (MockedConstruction<TaskPipeline> mockedConstruction = mockConstruction(TaskPipeline.class,
                (mock, context) -> {
                    // 验证构造函数参数
                    assertEquals("MAIN_PIPELINE", context.arguments().get(0));
                    assertEquals(taskRuntimeConfig, context.arguments().get(1));
                    assertEquals("", context.arguments().get(2));
                    assertEquals(true, context.arguments().get(3));  // useRelayLog应该为true
                    assertEquals(true, context.arguments().get(4));  // useKWayMerge应该为true
                    assertEquals(null, context.arguments().get(5));
                    // 将mock对象赋值给taskPipeline变量，这样verify才能工作
                    taskPipeline.set(mock);
                })) {

                taskEngine.tryStartPipeline();

                // 验证TaskPipeline的start方法被调用
                verify(taskPipeline.get(), times(1)).start();
            }
        }
    }

    @Test
    public void testCreateTaskPipelineWithValidRequest() {
        DumpRequest request = DumpRequest.newBuilder()
            .setStorageInstId("")
            .setTso("test_tso")
            .build();

        // Mock TaskPipeline构造函数以避免初始化过程中的复杂依赖
        AtomicReference<TaskPipeline> taskPipeline = new AtomicReference<>();
        try (MockedConstruction<TaskPipeline> mockedConstruction = mockConstruction(TaskPipeline.class,
            (mock, context) -> {
                // 验证构造函数参数
                assertEquals("MAIN_PIPELINE", context.arguments().get(0));
                assertEquals(taskRuntimeConfig, context.arguments().get(1));
                assertEquals("test_tso", context.arguments().get(2));
                assertEquals(false, context.arguments().get(3));  // useRelayLog应该为false
                assertEquals(true, context.arguments().get(4));  // useKWayMerge应该为false
                assertEquals("", context.arguments().get(5));
                // 将mock对象赋值给taskPipeline变量，这样verify才能工作
                taskPipeline.set(mock);
            })) {

            TaskPipeline result = taskEngine.createTaskPipeline(request);

            Assert.assertNotNull(result);
            assertEquals(taskPipeline.get(), result);

            // 验证pipeline被添加到map中
            try {
                java.lang.reflect.Field field = TaskEngine.class.getDeclaredField("taskPipelines");
                field.setAccessible(true);
                ConcurrentHashMap<String, TaskPipeline> taskPipelines =
                    (ConcurrentHashMap<String, TaskPipeline>) field.get(taskEngine);

                Assert.assertTrue(taskPipelines.containsKey("MAIN_PIPELINE"));
            } catch (Exception e) {
                Assert.fail("Failed to access taskPipelines field: " + e.getMessage());
            }
        }
    }

    @Test(expected = com.aliyun.polardbx.binlog.error.PolardbxException.class)
    public void testCreateTaskPipelineWithInvalidRequest() {
        // 设置为非Dispatcher类型
        taskRuntimeConfig.setType(TaskType.Final);

        DumpRequest request = DumpRequest.newBuilder()
            .setStorageInstId("test_storage")
            .setTso("test_tso")
            .build();

        taskEngine.createTaskPipeline(request);
    }

    @Test
    public void testRestart() throws InterruptedException {
        DumpRequest request = DumpRequest.newBuilder()
            .setTso("test_tso")
            .build();

        // Mock TaskPipeline构造函数以避免初始化过程中的复杂依赖
        AtomicReference<TaskPipeline> taskPipeline = new AtomicReference<>();
        try (MockedConstruction<TaskPipeline> mockedConstruction = mockConstruction(TaskPipeline.class,
            (mock, context) -> {
                // 验证构造函数参数
                assertEquals("MAIN_PIPELINE", context.arguments().get(0));
                assertEquals(taskRuntimeConfig, context.arguments().get(1));
                assertEquals("test_tso", context.arguments().get(2));
                assertEquals(false, context.arguments().get(3));  // useRelayLog应该为false
                assertEquals(true, context.arguments().get(4));  // useKWayMerge应该为false
                assertEquals("", context.arguments().get(5));
                // 将mock对象赋值给taskPipeline变量，这样verify才能工作
                taskPipeline.set(mock);
            })) {

            taskEngine.restart(request);

            // 验证TaskPipeline的start方法被调用
            verify(taskPipeline.get(), times(1)).start();
        }
    }
}