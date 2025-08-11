/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.RuntimeMode;
import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.cdc.meta.CdcMetaManager;
import com.aliyun.polardbx.binlog.daemon.cluster.bootstrap.ClusterBootStrapFactory;
import com.aliyun.polardbx.binlog.daemon.cluster.bootstrap.ClusterBootstrapService;
import com.aliyun.polardbx.binlog.daemon.rest.RestServer;
import com.aliyun.polardbx.binlog.daemon.schedule.ColumnarNodeReporter;
import com.aliyun.polardbx.binlog.daemon.schedule.NodeReporter;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.monitor.MonitorManager;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class DaemonBootStrapTest {

    @Mock
    private SpringContextBootStrap appContextBootStrap;

    @Mock
    private RestServer restServer;

    @Mock
    private MonitorManager monitorManager;

    @Mock
    private ClusterBootstrapService clusterBootstrapService;

    @Test
    public void main_ColumnarClusterType_ColumnarNodeReporterStarted() throws Exception {
        ClusterBootstrapService clusterBootstrapService = mock(ClusterBootstrapService.class);

        try (
            MockedStatic<DynamicApplicationConfig> configMock = mockStatic(DynamicApplicationConfig.class);
            MockedStatic<ClusterBootStrapFactory> clusterBootStrapFactoryMock = mockStatic(
                ClusterBootStrapFactory.class)
        ) {
            configMock.when(() -> DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID))
                .thenReturn("testCluster");
            configMock.when(DynamicApplicationConfig::getClusterType)
                .thenReturn("COLUMNAR");
            configMock.when(() -> DynamicApplicationConfig.getString(ConfigKeys.RUNTIME_MODE))
                .thenReturn("LOCAL");
            configMock.when(() -> DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_HEARTBEAT_INTERVAL_MS))
                .thenReturn(1000);

            clusterBootStrapFactoryMock.when(() -> ClusterBootStrapFactory.getBootstrapService(any()))
                .thenReturn(clusterBootstrapService);

            MockedConstruction<ColumnarNodeReporter> nodeReporterMock = mockConstruction(ColumnarNodeReporter.class);

            DaemonBootStrap.main(new String[] {});

            assert nodeReporterMock.constructed().size() == 1;
            ColumnarNodeReporter mockedColumnarReporter = nodeReporterMock.constructed().get(0);
            verify(mockedColumnarReporter, times(1)).start();
        }
    }

    @Test
    public void main_LocalSingleRuntimeMode_TaskAndDumperBootStrapStarted() throws Exception {
        ClusterBootstrapService clusterBootstrapService = mock(ClusterBootstrapService.class);

        try (
            MockedStatic<DynamicApplicationConfig> configMock = mockStatic(DynamicApplicationConfig.class);
            MockedStatic<ClusterBootStrapFactory> clusterBootStrapFactoryMock = mockStatic(
                ClusterBootStrapFactory.class)
        ) {
            configMock.when(() -> DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID))
                .thenReturn("testCluster");
            configMock.when(DynamicApplicationConfig::getClusterType)
                .thenReturn("BINLOG");
            configMock.when(() -> DynamicApplicationConfig.getString(ConfigKeys.RUNTIME_MODE))
                .thenReturn("LOCAL");
            configMock.when(() -> DynamicApplicationConfig.getInt(ConfigKeys.DAEMON_HEARTBEAT_INTERVAL_MS))
                .thenReturn(1000);

            clusterBootStrapFactoryMock.when(() -> ClusterBootStrapFactory.getBootstrapService(any()))
                .thenReturn(clusterBootstrapService);

            MockedConstruction<CdcMetaManager> cdcMetaManagerMock = mockConstruction(CdcMetaManager.class);
            MockedConstruction<NodeReporter> nodeReporterMock = mockConstruction(NodeReporter.class);

            DaemonBootStrap.main(new String[] {});

            assert nodeReporterMock.constructed().size() == 1;
            NodeReporter mockedColumnarReporter = nodeReporterMock.constructed().get(0);
            verify(mockedColumnarReporter, times(1)).start();

            assert cdcMetaManagerMock.constructed().size() == 1;
            CdcMetaManager mockedCdcMetaManager = cdcMetaManagerMock.constructed().get(0);
            verify(mockedCdcMetaManager, times(1)).init();
        }
    }
}

