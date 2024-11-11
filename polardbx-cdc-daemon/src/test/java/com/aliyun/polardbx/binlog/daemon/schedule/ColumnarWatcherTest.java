/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.ColumnarTopologyBuilder;
import com.aliyun.polardbx.binlog.daemon.pipeline.CommandPipeline;
import com.aliyun.polardbx.binlog.daemon.vo.CommandResult;
import com.aliyun.polardbx.binlog.dao.ColumnarInfoMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ColumnarWatcherTest {

    @Test
    public void testUpdateTimeAlarmNoTimeout() {
        ColumnarInfoMapper columnarInfoMapper = mock(ColumnarInfoMapper.class);
        ColumnarWatcher columnarWatcher = mock(ColumnarWatcher.class);

        try (MockedStatic<DynamicApplicationConfig> config = mockStatic(DynamicApplicationConfig.class)) {
            config.when(() -> DynamicApplicationConfig.getInt(anyString())).thenReturn(600000);

            when(columnarWatcher.getColumnarInfoMapper()).thenReturn(columnarInfoMapper);
            when(columnarInfoMapper.getUpdateTimeInterval()).thenReturn(500L);
            when(columnarInfoMapper.getColumnarIndexExist()).thenReturn(true);

            doCallRealMethod().when(columnarWatcher).updateTimeAlarm();
            columnarWatcher.updateTimeAlarm();
        }
    }

    @Test
    public void testUpdateTimeAlarmTimeout() {
        ColumnarInfoMapper columnarInfoMapper = mock(ColumnarInfoMapper.class);
        ColumnarWatcher columnarWatcher = mock(ColumnarWatcher.class);

        try (MockedStatic<DynamicApplicationConfig> config = mockStatic(DynamicApplicationConfig.class)) {
            config.when(() -> DynamicApplicationConfig.getInt(anyString())).thenReturn(600000);

            when(columnarWatcher.getColumnarInfoMapper()).thenReturn(columnarInfoMapper);
            when(columnarInfoMapper.getUpdateTimeInterval()).thenReturn(600001L);
            when(columnarInfoMapper.getColumnarIndexExist()).thenReturn(true);

            doCallRealMethod().when(columnarWatcher).updateTimeAlarm();
            columnarWatcher.updateTimeAlarm();
        }
    }

    @Test
    public void watchMemoryTest() throws Exception {
        ColumnarInfoMapper columnarInfoMapper = mock(ColumnarInfoMapper.class);
        ColumnarWatcher columnarWatcher = mock(ColumnarWatcher.class);
        when(columnarWatcher.getColumnarInfoMapper()).thenReturn(columnarInfoMapper);

        CommandPipeline commandPipeline = mock(CommandPipeline.class);
        CommandResult commandResult = mock(CommandResult.class);

        when(columnarWatcher.getCommander()).thenReturn(commandPipeline);
        when(commandPipeline.execCommand(any(), anyLong())).thenReturn(commandResult);
        when(commandResult.getCode()).thenReturn(0);
        when(commandResult.getMsg()).thenReturn("1234");

        try (
            MockedStatic<ColumnarTopologyBuilder> columnarTopologyBuilder = mockStatic(ColumnarTopologyBuilder.class)) {
            columnarTopologyBuilder.when(() -> ColumnarTopologyBuilder.calculateHeapMemory(anyLong())).thenReturn(10);

            doCallRealMethod().when(columnarWatcher).watchMemory();
            columnarWatcher.watchMemory();

            columnarTopologyBuilder.when(() -> ColumnarTopologyBuilder.calculateHeapMemory(anyLong())).thenReturn(1);
            columnarWatcher.watchMemory();
        }

    }
}
