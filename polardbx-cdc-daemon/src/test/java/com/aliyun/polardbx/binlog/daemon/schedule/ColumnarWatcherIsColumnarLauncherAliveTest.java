/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.daemon.pipeline.CommandPipeline;
import com.aliyun.polardbx.binlog.daemon.vo.CommandResult;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;

@RunWith(MockitoJUnitRunner.class)
public class ColumnarWatcherIsColumnarLauncherAliveTest {

    @Mock
    private CommandPipeline mockCommandPipeline;

    @Mock
    private CommandResult mockCommandResult;

    @Mock
    private ColumnarWatcher columnarWatcherUnderTest;
    /**
     * TC01: 第一次调用成功，返回 count > 0
     */
    @Test
    public void testIsColumnarLauncherAlive_Success_CountGreaterThanZero() throws Exception {
        Mockito.when(columnarWatcherUnderTest.getCommander()).thenReturn(mockCommandPipeline);
        Mockito.when(mockCommandPipeline.execCommand(Mockito.any(), Mockito.anyLong()))
                .thenReturn(mockCommandResult);
        Mockito.when(mockCommandResult.getCode()).thenReturn(0);
        Mockito.when(mockCommandResult.getMsg()).thenReturn("1");

        Mockito.doCallRealMethod().when(columnarWatcherUnderTest).isColumnarLauncherAlive();
        boolean result = columnarWatcherUnderTest.isColumnarLauncherAlive();
        Assert.assertTrue(result);
    }

    /**
     * TC02: 第一次调用成功，返回 count == 0
     */
    @Test
    public void testIsColumnarLauncherAlive_Success_CountZero() throws Exception {
        Mockito.when(columnarWatcherUnderTest.getCommander()).thenReturn(mockCommandPipeline);
        Mockito.when(mockCommandPipeline.execCommand(Mockito.any(), Mockito.anyLong()))
                .thenReturn(mockCommandResult);
        Mockito.when(mockCommandResult.getCode()).thenReturn(0);
        Mockito.when(mockCommandResult.getMsg()).thenReturn("0");

        Mockito.doCallRealMethod().when(columnarWatcherUnderTest).isColumnarLauncherAlive();
        boolean result = columnarWatcherUnderTest.isColumnarLauncherAlive();
        Assert.assertFalse(result);
    }

    /**
     * TC03: 所有调用 execCommand 都抛出异常
     */
    @Test
    public void testIsColumnarLauncherAlive_AllAttemptsFailWithException() throws Exception {
        Mockito.when(columnarWatcherUnderTest.getCommander()).thenReturn(mockCommandPipeline);
        Mockito.when(mockCommandPipeline.execCommand(Mockito.any(), Mockito.anyLong()))
                .thenThrow(new RuntimeException("Command failed"));

        Mockito.doCallRealMethod().when(columnarWatcherUnderTest).isColumnarLauncherAlive();
        boolean result = columnarWatcherUnderTest.isColumnarLauncherAlive();
        Assert.assertFalse(result);
    }

    /**
     * TC04: 所有调用 execCommand 返回 code != 0
     */
    @Test
    public void testIsColumnarLauncherAlive_CommandFailed_ReturnCodeNotZero() throws Exception {
        Mockito.when(columnarWatcherUnderTest.getCommander()).thenReturn(mockCommandPipeline);
        Mockito.when(mockCommandPipeline.execCommand(Mockito.any(), Mockito.anyLong()))
                .thenReturn(mockCommandResult);
        Mockito.when(mockCommandResult.getCode()).thenReturn(1); // 非零错误码

        Mockito.doCallRealMethod().when(columnarWatcherUnderTest).isColumnarLauncherAlive();
        boolean result = columnarWatcherUnderTest.isColumnarLauncherAlive();
        Assert.assertFalse(result);
    }

    /**
     * TC05: 返回的 msg 不是纯数字
     */
    @Test
    public void testIsColumnarLauncherAlive_MsgNotNumeric() throws Exception {
        Mockito.when(columnarWatcherUnderTest.getCommander()).thenReturn(mockCommandPipeline);
        Mockito.when(mockCommandPipeline.execCommand(Mockito.any(), Mockito.anyLong()))
                .thenReturn(mockCommandResult);
        Mockito.when(mockCommandResult.getCode()).thenReturn(0);
        Mockito.when(mockCommandResult.getMsg()).thenReturn("abc"); // 非数字字符串

        Mockito.doCallRealMethod().when(columnarWatcherUnderTest).isColumnarLauncherAlive();
        boolean result = columnarWatcherUnderTest.isColumnarLauncherAlive();
        Assert.assertFalse(result);
    }
}
