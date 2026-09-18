/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.dao.ColumnarInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ColumnarResourceManager;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.COLUMNAR_NO_ALARM_WITHOUT_CCI;
import static com.aliyun.polardbx.binlog.ConfigKeys.COLUMNAR_PROCESS_RESTART_THRESHOLD;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ColumnarTopologyServiceTest extends BaseTest {

    private ColumnarTopologyService columnarTopologyService;

    @Before
    public void setUp() {
        columnarTopologyService = new ColumnarTopologyService("test-cluster-id", "test-cluster-type");
    }

    @Test
    public void testCheckColumnarContainers() {
        ColumnarInfoMapper columnarInfoMapper = mock(ColumnarInfoMapper.class);
        ColumnarTopologyService columnarTopologyService = mock(ColumnarTopologyService.class);

        mockConfig(COLUMNAR_NO_ALARM_WITHOUT_CCI, "600000");
        mockConfig(COLUMNAR_PROCESS_RESTART_THRESHOLD, "-1");
        mockedAppConfig.when(() -> DynamicApplicationConfig.getBoolean(anyString())).thenReturn("true");

        ColumnarResourceManager resourceManager = mock(ColumnarResourceManager.class);
        Set<String> offlineContainers = new HashSet<>(1);
        offlineContainers.add("test");
        when(columnarTopologyService.getColumnarInfoMapper()).thenReturn(columnarInfoMapper);
        when(resourceManager.allOfflineContainers()).thenReturn(offlineContainers);
        when(columnarInfoMapper.getColumnarIndexExist()).thenReturn(true);

        doCallRealMethod().when(columnarTopologyService).checkColumnarContainers(resourceManager);
        columnarTopologyService.checkColumnarContainers(resourceManager);

        mockConfig(COLUMNAR_PROCESS_RESTART_THRESHOLD, "10");
        columnarTopologyService.checkColumnarContainers(resourceManager);

    }

    @Test
    public void testBuildPostClusterSnapshot() throws Exception {
        // 准备测试数据
        List<Container> containers = new ArrayList<>();
        Container container1 = Container.builder().containerId("container-1").build();
        containers.add(container1);

        Container container2 = Container.builder().containerId("container-2").build();
        containers.add(container2);

        List<StorageInfo> storageInfos = new ArrayList<>();
        StorageInfo storageInfo1 = new StorageInfo();
        storageInfo1.setStorageInstId("storage-1");
        storageInfos.add(storageInfo1);

        StorageInfo storageInfo2 = new StorageInfo();
        storageInfo2.setStorageInstId("storage-2");
        storageInfos.add(storageInfo2);

        StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
        storageHistoryInfo.setTso("test-tso");

        ClusterSnapshot result =
            columnarTopologyService.buildPostClusterSnapshot(containers, storageInfos, 2L, storageHistoryInfo);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.getContainers().contains("container-1"));
        assertTrue(result.getContainers().contains("container-2"));
        assertEquals(2, result.getContainers().size());
        assertTrue(result.getStorages().contains("storage-1"));
        assertTrue(result.getStorages().contains("storage-2"));
        assertEquals(2, result.getStorages().size());
        assertEquals("test-tso", result.getStorageHistoryTso());
        assertEquals(2L, result.getVersion());
        assertEquals(1L, result.getSubVersion().longValue());
        assertNotNull(result.getTimestamp());
    }

}
