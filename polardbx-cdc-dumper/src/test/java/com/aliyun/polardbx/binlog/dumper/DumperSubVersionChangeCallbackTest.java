/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper;

import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DumperSubVersionChangeCallbackTest extends BaseTest {

    private DumperController dumperController;
    private DumperSubVersionChangeCallback callback;
    private TaskRuntimeConfig taskRuntimeConfig;

    @Before
    public void setUp() {
        dumperController = Mockito.mock(DumperController.class);
        callback = new DumperSubVersionChangeCallback(dumperController);
        taskRuntimeConfig = new TaskRuntimeConfig();
    }

    @Test
    public void testOnSubVersionChangeWhenControllerIsRunningForDumperX() {
        // 准备
        long oldSubVersion = 1L;
        long newSubVersion = 2L;
        taskRuntimeConfig.setType(TaskType.DumperX);

        // 模拟控制器正在运行
        when(dumperController.isRunning()).thenReturn(true);
        when(dumperController.getTaskRuntimeConfig()).thenReturn(taskRuntimeConfig);

        // 执行
        callback.onSubVersionChange(oldSubVersion, newSubVersion, taskRuntimeConfig);

        // 验证
        verify(dumperController, times(1)).isRunning();
        verify(dumperController, times(1)).getTaskRuntimeConfig();
        verify(dumperController, times(1)).reloadForMultiStream(taskRuntimeConfig);
        verify(dumperController, never()).reloadForSingleStream(any());
    }

    @Test
    public void testOnSubVersionChangeWhenControllerIsRunningForNonDumperX() {
        // 准备
        long oldSubVersion = 1L;
        long newSubVersion = 2L;
        taskRuntimeConfig.setType(TaskType.Dumper);

        // 模拟控制器正在运行
        when(dumperController.isRunning()).thenReturn(true);
        when(dumperController.getTaskRuntimeConfig()).thenReturn(taskRuntimeConfig);

        // 执行
        callback.onSubVersionChange(oldSubVersion, newSubVersion, taskRuntimeConfig);

        // 验证
        verify(dumperController, times(1)).isRunning();
        verify(dumperController, times(1)).getTaskRuntimeConfig();
        verify(dumperController, times(1)).reloadForSingleStream(taskRuntimeConfig);
        verify(dumperController, never()).reloadForMultiStream(any());
    }

    @Test
    public void testOnSubVersionChangeWhenControllerIsNotRunningInitially() {
        // 准备
        long oldSubVersion = 1L;
        long newSubVersion = 2L;
        taskRuntimeConfig.setType(TaskType.DumperX);

        // 模拟控制器最初未运行，然后运行
        when(dumperController.isRunning()).thenReturn(false, false, false, true);
        when(dumperController.getTaskRuntimeConfig()).thenReturn(taskRuntimeConfig);

        // 执行
        callback.onSubVersionChange(oldSubVersion, newSubVersion, taskRuntimeConfig);

        // 验证
        verify(dumperController, times(4)).isRunning();
        verify(dumperController, times(1)).getTaskRuntimeConfig();
        verify(dumperController, times(1)).reloadForMultiStream(taskRuntimeConfig);
    }
}
