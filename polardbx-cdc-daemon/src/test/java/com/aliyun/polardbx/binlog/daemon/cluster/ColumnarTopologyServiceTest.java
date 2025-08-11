/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.daemon.cluster.topology.ColumnarTopologyService;
import com.aliyun.polardbx.binlog.dao.ColumnarInfoMapper;
import com.aliyun.polardbx.binlog.scheduler.ColumnarResourceManager;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.util.HashSet;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.COLUMNAR_NO_ALARM_WITHOUT_CCI;
import static com.aliyun.polardbx.binlog.ConfigKeys.COLUMNAR_PROCESS_RESTART_THRESHOLD;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class ColumnarTopologyServiceTest {
    @Test
    public void testCheckColumnarContainers() {
        ColumnarInfoMapper columnarInfoMapper = mock(ColumnarInfoMapper.class);
        ColumnarTopologyService columnarTopologyService = mock(ColumnarTopologyService.class);

        try (MockedStatic<DynamicApplicationConfig> config = mockStatic(DynamicApplicationConfig.class)) {
            config.when(() -> DynamicApplicationConfig.getInt(COLUMNAR_NO_ALARM_WITHOUT_CCI)).thenReturn(600000);
            config.when(() -> DynamicApplicationConfig.getInt(COLUMNAR_PROCESS_RESTART_THRESHOLD)).thenReturn(-1);
            config.when(() -> DynamicApplicationConfig.getBoolean(anyString())).thenReturn(true);

            ColumnarResourceManager resourceManager = mock(ColumnarResourceManager.class);
            Set<String> offlineContainers = new HashSet<>(1);
            offlineContainers.add("test");
            when(columnarTopologyService.getColumnarInfoMapper()).thenReturn(columnarInfoMapper);
            when(resourceManager.allOfflineContainers()).thenReturn(offlineContainers);
            when(columnarInfoMapper.getColumnarIndexExist()).thenReturn(true);

            doCallRealMethod().when(columnarTopologyService).checkColumnarContainers(resourceManager);
            columnarTopologyService.checkColumnarContainers(resourceManager);

            config.when(() -> DynamicApplicationConfig.getInt(COLUMNAR_PROCESS_RESTART_THRESHOLD)).thenReturn(10);
            columnarTopologyService.checkColumnarContainers(resourceManager);
        }
    }
}
