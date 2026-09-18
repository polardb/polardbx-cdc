/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.relay.HashLevel;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskSubVersionChangeCallbackTest extends BaseTest {

    @Mock
    private TaskController taskController;
    @Mock
    private TaskRuntimeConfig taskRuntimeConfig;

    private TaskSubVersionChangeCallback callback;
    @Mock
    private Consumer<Void> exitCallback;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        callback = new TaskSubVersionChangeCallback(taskController, exitCallback);
    }

    @Test
    public void testOnSubVersionChangeWithDispatcherAndNonDataNode() {
        // 测试条件: TaskType为Dispatcher且HashLevel不是DATANODE
        try (MockedStatic<HashLevel> mockedHashLevel = mockStatic(HashLevel.class)) {
            when(taskRuntimeConfig.getType()).thenReturn(TaskType.Dispatcher);
            mockedHashLevel.when(HashLevel::getCurrentHashLevel).thenReturn(HashLevel.TABLE);

            // 调用被测试方法
            callback.onSubVersionChange(1L, 2L, taskRuntimeConfig);

            // 验证reload方法未被调用
            verify(taskController, never()).reload(any());
            verify(exitCallback, times(1)).accept(null);
        }
    }

    @Test
    public void testOnSubVersionChangeWithDispatcherAndDataNode() {
        // 测试条件: TaskType为Dispatcher且HashLevel是DATANODE
        try (MockedStatic<HashLevel> mockedHashLevel = mockStatic(HashLevel.class)) {
            when(taskRuntimeConfig.getType()).thenReturn(TaskType.Dispatcher);
            mockedHashLevel.when(HashLevel::getCurrentHashLevel).thenReturn(HashLevel.DATANODE);

            // 调用被测试方法
            callback.onSubVersionChange(1L, 2L, taskRuntimeConfig);

            // 验证reload方法被调用
            verify(taskController).reload(taskRuntimeConfig);
        }
    }

    @Test
    public void testOnSubVersionChangeWithNonDispatcher() {
        // 测试条件: TaskType不是Dispatcher
        when(taskRuntimeConfig.getType()).thenReturn(TaskType.Relay); // 使用Relay作为非Dispatcher示例

        // 调用被测试方法
        callback.onSubVersionChange(1L, 2L, taskRuntimeConfig);

        // 验证reload方法被调用
        verify(taskController).reload(taskRuntimeConfig);
    }
}
