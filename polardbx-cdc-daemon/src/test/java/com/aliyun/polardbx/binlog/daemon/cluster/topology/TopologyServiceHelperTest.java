/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.daemon.constant.ClusterRebalanceInstruction;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ExecutionSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_SUPPORT_REFRESH_TOPOLOGY_ONLY_DAEMON_DOWN;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class TopologyServiceHelperTest extends BaseTest {

    private AutoCloseable closeable;
    @Mock
    private ResourceManager resourceManager;

    @Mock
    private ExecutionSnapshot executionSnapshot;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        TopologyServiceHelper.setLastForceRefreshTime(0);
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    public void testShouldRefreshTopology_WhenRebalanceInstructionSet_ShouldReturnTrue() {
        try (MockedStatic<SystemDbConfig> mockedStatic = mockStatic(SystemDbConfig.class)) {
            // Given
            mockedStatic.when(() -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.SET_REBALANCE_INSTRUCTION);

            // When
            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, new ClusterSnapshot(), new ArrayList<>(), executionSnapshot, new StorageHistoryInfo());

            // Then
            assertTrue(result.isNeedRebalance());
            assertTrue(result.isFullRebalance());
            assertFalse(result.isForceIntervalRebalance());
        }
    }

    @Test
    public void testShouldRefreshTopology_WhenClusterSnapshotIsOrigin_ShouldReturnTrue() {
        try (MockedStatic<SystemDbConfig> mockedStatic = mockStatic(SystemDbConfig.class)) {
            // Given
            mockedStatic.when(() -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(1L);

            // When
            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, clusterSnapshot, new ArrayList<>(), executionSnapshot, new StorageHistoryInfo());

            // Then
            assertTrue(result.isNeedRebalance());
            assertTrue(result.isFullRebalance());
            assertFalse(result.isForceIntervalRebalance());
        }
    }

    @Test
    public void testShouldRefreshTopology_WhenServerIdMismatch_ShouldReturnTrue() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class)) {
            // Given
            systemDbConfigMockedStatic.when(
                    () -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L);
            clusterSnapshot.setServerId(1234L);

            serverConfigUtilMockedStatic.when(
                    () -> com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect(anyString()))
                .thenReturn(5678L);

            // When
            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, clusterSnapshot, new ArrayList<>(), executionSnapshot, new StorageHistoryInfo());

            // Then
            assertTrue(result.isNeedRebalance());
            assertFalse(result.isFullRebalance());
            assertFalse(result.isForceIntervalRebalance());
        }
    }

    @Test
    public void testShouldRefreshTopology_WhenStorageHistoryTsoMismatch_ShouldThrowException() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class)) {
            // Given
            systemDbConfigMockedStatic.when(
                    () -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L);
            clusterSnapshot.setServerId(1234L);
            clusterSnapshot.setStorageHistoryTso("tso2");

            serverConfigUtilMockedStatic.when(
                    () -> com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect(anyString()))
                .thenReturn(1234L);

            StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
            storageHistoryInfo.setTso("tso1");

            // When & Then
            try {
                TopologyServiceHelper.shouldRefreshTopology(resourceManager, clusterSnapshot, new ArrayList<>(),
                    executionSnapshot, storageHistoryInfo);
                // If we reach this line, no exception was thrown, which is incorrect
                org.junit.Assert.fail("Expected PolardbxException to be thrown");
            } catch (com.aliyun.polardbx.binlog.error.PolardbxException e) {
                org.junit.Assert.assertTrue(e.getMessage()
                    .contains("latest storage history tso can`t be less than previous storage history tso"));
            }
        }
    }

    @Test
    public void testShouldRefreshTopology_WhenForceRefreshIntervalTriggered_ShouldReturnTrue() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class)) {
            // Given
            systemDbConfigMockedStatic.when(
                    () -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L);
            clusterSnapshot.setServerId(1234L);
            clusterSnapshot.setStorageHistoryTso(ExecutionConfig.ORIGIN_TSO);

            serverConfigUtilMockedStatic.when(
                    () -> com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect(anyString()))
                .thenReturn(1234L);

            mockConfig(DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL, "1");

            // Mock lastForceRefreshTime to make sure interval has passed
            // Note: We can't easily test this without modifying the source to allow setting lastForceRefreshTime

            // For now, just verify it doesn't throw an exception
            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, clusterSnapshot, new ArrayList<>(), executionSnapshot, null);

            // Then
            // Depending on random, either fullRebalance is true or false
            assertTrue(result.isNeedRebalance());
            assertTrue(result.isForceIntervalRebalance());
        }
    }

    @Test
    public void testShouldRefreshTopology_WhenStorageChanged_ShouldReturnTrue() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class)) {
            // Given
            systemDbConfigMockedStatic.when(
                    () -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L);
            clusterSnapshot.setServerId(1234L);
            clusterSnapshot.setStorageHistoryTso(ExecutionConfig.ORIGIN_TSO);
            clusterSnapshot.setStorages(new HashSet<>(Arrays.asList("storage1", "storage2")));

            serverConfigUtilMockedStatic.when(
                    () -> com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect(anyString()))
                .thenReturn(1234L);

            mockConfig(DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL, "0");

            StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
            storageHistoryInfo.setTso(ExecutionConfig.ORIGIN_TSO);

            List<StorageInfo> storageInfos = new ArrayList<>();
            StorageInfo storageInfo1 = new StorageInfo();
            storageInfo1.setStorageInstId("storage1");
            StorageInfo storageInfo2 = new StorageInfo();
            storageInfo2.setStorageInstId("storage3"); // Different storage
            storageInfos.add(storageInfo1);
            storageInfos.add(storageInfo2);

            // When
            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, clusterSnapshot, storageInfos, executionSnapshot, storageHistoryInfo);

            // Then
            assertTrue(result.isNeedRebalance());
            assertFalse(result.isFullRebalance());
            assertFalse(result.isForceIntervalRebalance());
        }
    }

    @Test
    public void testShouldRefreshTopology_WhenNewlyAddedContainers_ShouldReturnTrue() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class)) {
            // Given
            systemDbConfigMockedStatic.when(
                    () -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L);
            clusterSnapshot.setServerId(1234L);
            clusterSnapshot.setStorageHistoryTso(ExecutionConfig.ORIGIN_TSO);
            clusterSnapshot.setStorages(new HashSet<>(Arrays.asList("storage1", "storage2")));
            clusterSnapshot.setContainers(new HashSet<>(Arrays.asList("container1", "container2")));

            serverConfigUtilMockedStatic.when(
                    () -> com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect(anyString()))
                .thenReturn(1234L);

            mockConfig(DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL, "0");

            when(resourceManager.allOnlineContainers())
                .thenReturn(new HashSet<>(Arrays.asList("container1", "container2", "container3"))); // New container

            // When
            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, clusterSnapshot, new ArrayList<>(), executionSnapshot, null);

            // Then
            assertTrue(result.isNeedRebalance());
            assertFalse(result.isFullRebalance());
            assertFalse(result.isForceIntervalRebalance());
        }
    }

    @Test
    public void testShouldRefreshTopology_WhenNoChanges_ShouldReturnFalse() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class)) {
            // Given
            systemDbConfigMockedStatic.when(
                    () -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L);
            clusterSnapshot.setServerId(1234L);
            clusterSnapshot.setStorageHistoryTso(ExecutionConfig.ORIGIN_TSO);
            clusterSnapshot.setStorages(new HashSet<>(Arrays.asList("storage1", "storage2")));
            clusterSnapshot.setContainers(new HashSet<>(Arrays.asList("container1", "container2")));

            serverConfigUtilMockedStatic.when(
                    () -> com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect(anyString()))
                .thenReturn(1234L);

            mockConfig(DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL, "0");
            mockConfig(DAEMON_SUPPORT_REFRESH_TOPOLOGY_ONLY_DAEMON_DOWN, "false");

            StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
            storageHistoryInfo.setTso(ExecutionConfig.ORIGIN_TSO);

            List<StorageInfo> storageInfos = new ArrayList<>();
            StorageInfo storageInfo1 = new StorageInfo();
            storageInfo1.setStorageInstId("storage1");
            StorageInfo storageInfo2 = new StorageInfo();
            storageInfo2.setStorageInstId("storage2");
            storageInfos.add(storageInfo1);
            storageInfos.add(storageInfo2);

            when(resourceManager.allOnlineContainers())
                .thenReturn(new HashSet<>(Arrays.asList("container1", "container2")));

            when(executionSnapshot.isAllRunningOk()).thenReturn(true);

            // When
            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, clusterSnapshot, storageInfos, executionSnapshot, storageHistoryInfo);

            // Then
            assertFalse(result.isNeedRebalance());
            assertFalse(result.isFullRebalance());
            assertFalse(result.isForceIntervalRebalance());
        }
    }

    @Test
    public void testGetPreviousContainers_WhenForceRefreshIntervalIsZero_ShouldReturnContainers() {
        // Given
        int forceRefreshInterval = 0;
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        Set<String> expectedContainers = new HashSet<>(Arrays.asList("container1", "container2"));
        clusterSnapshot.setContainers(expectedContainers);

        // When
        Set<String> result = TopologyServiceHelper.getPreviousContainers(forceRefreshInterval, clusterSnapshot);

        // Then
        assertEquals(expectedContainers, result);
    }

    @Test
    public void testGetPreviousContainers_WhenForceRefreshIntervalIsNegative_ShouldReturnContainers() {
        // Given
        int forceRefreshInterval = -1;
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        Set<String> expectedContainers = new HashSet<>(Arrays.asList("container1", "container2"));
        clusterSnapshot.setContainers(expectedContainers);

        // When
        Set<String> result = TopologyServiceHelper.getPreviousContainers(forceRefreshInterval, clusterSnapshot);

        // Then
        assertEquals(expectedContainers, result);
    }

    @Test
    public void testGetPreviousContainers_WhenForceRefreshIntervalIsPositive_ShouldReturnContainersBeforeRandomRemove() {
        // Given
        int forceRefreshInterval = 5;
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        Set<String> expectedContainers = new HashSet<>(Arrays.asList("container1", "container2", "container3"));
        clusterSnapshot.setContainersBeforeRandomRemove(expectedContainers);

        // When
        Set<String> result = TopologyServiceHelper.getPreviousContainers(forceRefreshInterval, clusterSnapshot);

        // Then
        assertEquals(expectedContainers, result);
    }

    @Test
    public void testShouldRefreshTopology_WhenNewlyAddedSingleContainer_ShouldReturnTrue() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class)) {
            // Given
            systemDbConfigMockedStatic.when(
                    () -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L);
            clusterSnapshot.setServerId(1234L);
            clusterSnapshot.setStorageHistoryTso(ExecutionConfig.ORIGIN_TSO);
            clusterSnapshot.setStorages(new HashSet<>(Arrays.asList("storage1", "storage2")));
            clusterSnapshot.setContainers(new HashSet<>(Arrays.asList("container1", "container2")));

            serverConfigUtilMockedStatic.when(
                    () -> com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect(anyString()))
                .thenReturn(1234L);

            mockConfig(DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL, "0");

            when(resourceManager.allOnlineContainers())
                .thenReturn(
                    new HashSet<>(Arrays.asList("container1", "container2", "container3"))); // One new container

            // When
            List<StorageInfo> storageInfos = new ArrayList<>();
            StorageInfo storageInfo1 = new StorageInfo();
            storageInfo1.setStorageInstId("storage1");
            StorageInfo storageInfo2 = new StorageInfo();
            storageInfo2.setStorageInstId("storage2");
            storageInfos.add(storageInfo1);
            storageInfos.add(storageInfo2);

            StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
            storageHistoryInfo.setTso(ExecutionConfig.ORIGIN_TSO);

            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, clusterSnapshot, storageInfos, executionSnapshot, storageHistoryInfo);

            // Then
            assertTrue(result.isNeedRebalance());
            assertFalse(result.isFullRebalance());
            assertFalse(result.isForceIntervalRebalance());
        }
    }

    @Test
    public void testShouldRefreshTopology_WhenMissedContainersAndSupportRebuild_ShouldReturnTrue() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class)) {
            // Given
            systemDbConfigMockedStatic.when(
                    () -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L);
            clusterSnapshot.setServerId(1234L);
            clusterSnapshot.setStorageHistoryTso(ExecutionConfig.ORIGIN_TSO);
            clusterSnapshot.setStorages(new HashSet<>(Arrays.asList("storage1", "storage2")));
            clusterSnapshot.setContainers(new HashSet<>(Arrays.asList("container1", "container2", "container3")));

            serverConfigUtilMockedStatic.when(
                    () -> com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect(anyString()))
                .thenReturn(1234L);

            mockConfig(DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL, "0");
            mockConfig(DAEMON_SUPPORT_REFRESH_TOPOLOGY_ONLY_DAEMON_DOWN, "true");

            when(resourceManager.allOnlineContainers())
                .thenReturn(new HashSet<>(Arrays.asList("container1", "container2")));

            // When
            List<StorageInfo> storageInfos = new ArrayList<>();
            StorageInfo storageInfo1 = new StorageInfo();
            storageInfo1.setStorageInstId("storage1");
            StorageInfo storageInfo2 = new StorageInfo();
            storageInfo2.setStorageInstId("storage2");
            storageInfos.add(storageInfo1);
            storageInfos.add(storageInfo2);

            StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
            storageHistoryInfo.setTso(ExecutionConfig.ORIGIN_TSO);

            // When
            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, clusterSnapshot, storageInfos, executionSnapshot, storageHistoryInfo);

            // Then
            assertTrue(result.isNeedRebalance());
            assertFalse(result.isFullRebalance());
            assertFalse(result.isForceIntervalRebalance());
        }
    }

    @Test
    public void testShouldRefreshTopology_WhenMissedContainersAndTasksNotRunningAndContainerDeleted_ShouldReturnTrue() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class)) {
            // Given
            systemDbConfigMockedStatic.when(
                    () -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L);
            clusterSnapshot.setServerId(1234L);
            clusterSnapshot.setStorageHistoryTso(ExecutionConfig.ORIGIN_TSO);
            clusterSnapshot.setStorages(new HashSet<>(Arrays.asList("storage1", "storage2")));
            clusterSnapshot.setContainers(new HashSet<>(Arrays.asList("container1", "container2", "container3")));

            serverConfigUtilMockedStatic.when(
                    () -> com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect(anyString()))
                .thenReturn(1234L);

            mockConfig(DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL, "0");
            mockConfig(DAEMON_SUPPORT_REFRESH_TOPOLOGY_ONLY_DAEMON_DOWN, "false");

            when(resourceManager.allOnlineContainers())
                .thenReturn(new HashSet<>(Arrays.asList("container1", "container2")));

            when(resourceManager.isAllContainerExist(new HashSet<>(Arrays.asList("container3"))))
                .thenReturn(false); // Container was deleted

            when(executionSnapshot.isAllRunningOk()).thenReturn(false);
            when(executionSnapshot.isRunningOk4Container("container3")).thenReturn(true);

            List<StorageInfo> storageInfos = new ArrayList<>();
            StorageInfo storageInfo1 = new StorageInfo();
            storageInfo1.setStorageInstId("storage1");
            StorageInfo storageInfo2 = new StorageInfo();
            storageInfo2.setStorageInstId("storage2");
            storageInfos.add(storageInfo1);
            storageInfos.add(storageInfo2);

            StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
            storageHistoryInfo.setTso(ExecutionConfig.ORIGIN_TSO);

            // When
            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, clusterSnapshot, storageInfos, executionSnapshot, storageHistoryInfo);

            // Then
            assertTrue(result.isNeedRebalance());
            assertFalse(result.isFullRebalance());
            assertFalse(result.isForceIntervalRebalance());
        }
    }

    @Test
    public void testShouldRefreshTopology_WhenMissedContainersAndTasksNotRunningAndContainerDown_ShouldReturnTrue() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class)) {
            // Given
            systemDbConfigMockedStatic.when(
                    () -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L);
            clusterSnapshot.setServerId(1234L);
            clusterSnapshot.setStorageHistoryTso(ExecutionConfig.ORIGIN_TSO);
            clusterSnapshot.setStorages(new HashSet<>(Arrays.asList("storage1", "storage2")));
            clusterSnapshot.setContainers(new HashSet<>(Arrays.asList("container1", "container2", "container3")));

            serverConfigUtilMockedStatic.when(
                    () -> com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect(anyString()))
                .thenReturn(1234L);

            mockConfig(DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL, "0");
            mockConfig(DAEMON_SUPPORT_REFRESH_TOPOLOGY_ONLY_DAEMON_DOWN, "false");

            when(resourceManager.allOnlineContainers())
                .thenReturn(new HashSet<>(Arrays.asList("container1", "container2")));

            when(resourceManager.isAllContainerExist(new HashSet<>(Arrays.asList("container3"))))
                .thenReturn(true); // Container still exists

            when(executionSnapshot.isAllRunningOk()).thenReturn(false);
            when(executionSnapshot.isRunningOk4Container("container3")).thenReturn(false); // But daemon is down

            List<StorageInfo> storageInfos = new ArrayList<>();
            StorageInfo storageInfo1 = new StorageInfo();
            storageInfo1.setStorageInstId("storage1");
            StorageInfo storageInfo2 = new StorageInfo();
            storageInfo2.setStorageInstId("storage2");
            storageInfos.add(storageInfo1);
            storageInfos.add(storageInfo2);

            StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
            storageHistoryInfo.setTso(ExecutionConfig.ORIGIN_TSO);

            // When
            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, clusterSnapshot, storageInfos, executionSnapshot, storageHistoryInfo);

            // Then
            assertTrue(result.isNeedRebalance());
            assertFalse(result.isFullRebalance());
            assertFalse(result.isForceIntervalRebalance());
        }
    }

    @Test
    public void testShouldRefreshTopology_WhenMissedContainersButTasksStillRunning_ShouldReturnFalse() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class)) {
            // Given
            systemDbConfigMockedStatic.when(
                    () -> SystemDbConfig.getSystemDbConfig(ConfigKeys.CLUSTER_REBALANCE_INSTRUCTION))
                .thenReturn(ClusterRebalanceInstruction.UNSET_REBALANCE_INSTRUCTION);

            ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
            clusterSnapshot.setVersion(2L);
            clusterSnapshot.setServerId(1234L);
            clusterSnapshot.setStorageHistoryTso(ExecutionConfig.ORIGIN_TSO);
            clusterSnapshot.setStorages(new HashSet<>(Arrays.asList("storage1", "storage2")));
            clusterSnapshot.setContainers(new HashSet<>(Arrays.asList("container1", "container2", "container3")));

            serverConfigUtilMockedStatic.when(
                    () -> com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect(anyString()))
                .thenReturn(1234L);

            mockConfig(DAEMON_FORCE_REFRESH_TOPOLOGY_INTERVAL, "0");
            mockConfig(DAEMON_SUPPORT_REFRESH_TOPOLOGY_ONLY_DAEMON_DOWN, "false");

            when(resourceManager.allOnlineContainers())
                .thenReturn(new HashSet<>(Arrays.asList("container1", "container2")));

            when(resourceManager.isAllContainerExist(new HashSet<>(Arrays.asList("container3"))))
                .thenReturn(true); // Container still exists

            when(executionSnapshot.isAllRunningOk()).thenReturn(false);
            when(executionSnapshot.isRunningOk4Container("container3")).thenReturn(true); // Daemon still running

            List<StorageInfo> storageInfos = new ArrayList<>();
            StorageInfo storageInfo1 = new StorageInfo();
            storageInfo1.setStorageInstId("storage1");
            StorageInfo storageInfo2 = new StorageInfo();
            storageInfo2.setStorageInstId("storage2");
            storageInfos.add(storageInfo1);
            storageInfos.add(storageInfo2);

            StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
            storageHistoryInfo.setTso(ExecutionConfig.ORIGIN_TSO);

            // When
            TopologyServiceHelper.CheckResult result = TopologyServiceHelper.shouldRefreshTopology(
                resourceManager, clusterSnapshot, storageInfos, executionSnapshot, storageHistoryInfo);

            // Then
            assertFalse(result.isNeedRebalance());
            assertFalse(result.isFullRebalance());
            assertFalse(result.isForceIntervalRebalance());
        }
    }

}
