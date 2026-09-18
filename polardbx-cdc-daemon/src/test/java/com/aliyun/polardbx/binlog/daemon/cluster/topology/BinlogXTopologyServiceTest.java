/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.dao.BinlogScheduleHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.NodeInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryDetailInfoMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogScheduleHistory;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryDetailInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.domain.po.StorageInfo;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.leader.RuntimeLeaderElector;
import com.aliyun.polardbx.binlog.scheduler.ClusterSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ExecutionSnapshot;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.ScheduleHistoryContent;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.service.XStreamService;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.ServerConfigUtil;
import com.aliyun.polardbx.binlog.util.SystemDbConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mockito.stubbing.Answer;
import org.mybatis.dynamic.sql.delete.DeleteDSLCompleter;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_STREAM_GROUP_NAME;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SNAPSHOT_VERSION_KEY;
import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_SUSPEND_TOPOLOGY_REBUILDING;
import static com.aliyun.polardbx.binlog.daemon.cluster.topology.TopologyServiceHelper.lockAndCheck;
import static com.aliyun.polardbx.binlog.util.ServerConfigUtil.getGlobalNumberVarDirect;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BinlogXTopologyServiceTest extends BaseTest {

    private static final String TEST_CLUSTER_ID = "test_cluster_id";
    private static final String TEST_CLUSTER_TYPE = "test_cluster_type";

    private AutoCloseable closeable;
    @Mock
    private BinlogTaskConfigMapper taskConfigMapper;

    @Mock
    private DumperInfoMapper dumperInfoMapper;

    @Mock
    private StorageHistoryInfoMapper storageHistoryMapper;

    @Mock
    private StorageHistoryDetailInfoMapper storageHistDetailInfoMapper;

    @Mock
    private BinlogScheduleHistoryMapper scheduleHistoryMapper;

    @Mock
    private BinlogTaskInfoMapper taskInfoMapper;

    @Mock
    private NodeInfoMapper nodeInfoMapper;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private ResourceManager resourceManager;

    @Mock
    private BinlogXTopologyBuilder topologyBuilder;

    private BinlogXTopologyService topologyService;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        // Mock all required DAOs
        registerSpringObject(BinlogTaskConfigMapper.class, taskConfigMapper);
        registerSpringObject(DumperInfoMapper.class, dumperInfoMapper);
        registerSpringObject(StorageHistoryInfoMapper.class, storageHistoryMapper);
        registerSpringObject(StorageHistoryDetailInfoMapper.class, storageHistDetailInfoMapper);
        registerSpringObject(BinlogScheduleHistoryMapper.class, scheduleHistoryMapper);
        registerSpringObject(BinlogTaskInfoMapper.class, taskInfoMapper);
        registerSpringObject(NodeInfoMapper.class, nodeInfoMapper);
        registerSpringObject("metaTransactionTemplate", transactionTemplate);
        registerSpringObject("polarxJdbcTemplate", JdbcTemplate.class);

        // Mock transaction template to execute the callback directly
        when(transactionTemplate.execute(any())).thenAnswer((Answer<Object>) invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        // Create the service instance
        topologyService =
            new BinlogXTopologyService(TEST_CLUSTER_ID, TEST_CLUSTER_TYPE, topologyBuilder, resourceManager);
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    public void testTryBuild_WhenClusterSuspended_ShouldSkipBuilding() {
        try (MockedStatic<SystemDbConfig> mockedStatic = mockStatic(SystemDbConfig.class)) {
            // Given
            when(SystemDbConfig.getSystemDbConfig(CLUSTER_SUSPEND_TOPOLOGY_REBUILDING)).thenReturn("true");

            // When
            topologyService.tryBuild();

            // Then
            // Should not call refreshTopology
            verify(resourceManager, never()).tryRandomRemoveContainer(anyList(), anyBoolean());
        }
    }

    @Test
    public void testTryBuild_WhenClusterNotSuspended_ShouldCallRefreshTopology() {
        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(ServerConfigUtil.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMockedStatic = mockStatic(
                RuntimeLeaderElector.class);
            MockedStatic<TopologyServiceHelper> topologyServiceHelperMockedStatic = mockStatic(
                TopologyServiceHelper.class)) {
            // Given
            mockConfig(CLUSTER_SUSPEND_TOPOLOGY_REBUILDING, "");

            // Mock SystemDbConfig.getSystemDbConfig
            ClusterSnapshot clusterSnapshot = createClusterSnapshot();
            systemDbConfigMockedStatic.when(() -> SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY))
                .thenReturn(JSONObject.toJSONString(clusterSnapshot));

            // Mock TopologyServiceHelper methods
            topologyServiceHelperMockedStatic.when(() -> TopologyServiceHelper.checkContainerStatus(any()))
                .thenAnswer(invocation -> null); // doNothing equivalent

            topologyServiceHelperMockedStatic.when(() -> TopologyServiceHelper.clearStaleMetaData(anyLong()))
                .thenAnswer(invocation -> null); // doNothing equivalent

            topologyServiceHelperMockedStatic.when(TopologyServiceHelper::buildExpectedStorageTso4BinlogX)
                .thenReturn(ExecutionConfig.ORIGIN_TSO);

            topologyServiceHelperMockedStatic.when(() -> TopologyServiceHelper.buildStorageHistoryInfo(anyString()))
                .thenReturn(null);

            TopologyServiceHelper.CheckResult checkResult = new TopologyServiceHelper.CheckResult(true, true, false);
            topologyServiceHelperMockedStatic.when(
                    () -> TopologyServiceHelper.shouldRefreshTopology(any(), any(), anyList(), any(), any()))
                .thenReturn(checkResult);

            serverConfigUtilMockedStatic.when(() -> getGlobalNumberVarDirect(anyString())).thenReturn(111L);

            // Mock RuntimeLeaderElector
            runtimeLeaderElectorMockedStatic.when(RuntimeLeaderElector::isDaemonLeader)
                .thenReturn(true);

            // Mock storage infos
            List<StorageInfo> storageInfos = Arrays.asList(createStorageInfo("storage1"),
                createStorageInfo("storage2"));
            topologyServiceHelperMockedStatic.when(() -> TopologyServiceHelper.buildStorageInfos(any()))
                .thenReturn(storageInfos);

            // Mock resource manager
            List<Container> containers =
                Arrays.asList(createContainer("container1"), createContainer("container2"));
            when(resourceManager.tryRandomRemoveContainer(anyList(), anyBoolean())).thenReturn(containers);
            when(resourceManager.getExecutionSnapshot()).thenReturn(new ExecutionSnapshot());

            // Mock topology builder
            TopologyEntity topologyEntity = createTopologyEntity();
            topologyEntity.setContainerStreamMap(new HashMap<>());
            when(topologyBuilder.buildTopology(anyList(), anyList(), any(), anyString(), anyLong(),
                anyLong(),
                anyLong(),
                anyString()))
                .thenReturn(topologyEntity);

            // When
            topologyService.tryBuild();

            // Then
            // Should call refreshTopology but not trigger rebalance
            verify(resourceManager, times(1)).availableContainers();
        }
    }

    @Test
    public void testTryBuild_WhenNeedRebalance_ShouldPersistTopology() {
        // Given
        mockConfig(CLUSTER_SUSPEND_TOPOLOGY_REBUILDING, "");

        // Mock SystemDbConfig.getSystemDbConfig
        ClusterSnapshot clusterSnapshot = createClusterSnapshot();

        try (MockedStatic<SystemDbConfig> systemDbConfigMockedStatic = mockStatic(SystemDbConfig.class);
            MockedStatic<TopologyServiceHelper> topologyServiceHelperMockedStatic = mockStatic(
                TopologyServiceHelper.class);
            MockedStatic<com.aliyun.polardbx.binlog.util.ServerConfigUtil> serverConfigUtilMockedStatic = mockStatic(
                com.aliyun.polardbx.binlog.util.ServerConfigUtil.class);
            MockedStatic<RebalanceUtil> rebalanceUtilMockedStatic = mockStatic(RebalanceUtil.class);
            MockedStatic<RuntimeLeaderElector> runtimeLeaderElectorMockedStatic = mockStatic(
                RuntimeLeaderElector.class)) {

            systemDbConfigMockedStatic.when(() -> SystemDbConfig.getSystemDbConfig(CLUSTER_SNAPSHOT_VERSION_KEY))
                .thenReturn(JSONObject.toJSONString(clusterSnapshot));

            // Mock TopologyServiceHelper methods
            topologyServiceHelperMockedStatic.when(() -> TopologyServiceHelper.checkContainerStatus(any()))
                .thenAnswer(invocation -> null); // doNothing equivalent

            topologyServiceHelperMockedStatic.when(() -> TopologyServiceHelper.clearStaleMetaData(anyLong()))
                .thenAnswer(invocation -> null); // doNothing equivalent

            topologyServiceHelperMockedStatic.when(TopologyServiceHelper::buildExpectedStorageTso4BinlogX)
                .thenReturn(ExecutionConfig.ORIGIN_TSO);

            topologyServiceHelperMockedStatic.when(() -> TopologyServiceHelper.buildStorageHistoryInfo(anyString()))
                .thenReturn(null);

            List<StorageInfo> storageInfos = Arrays.asList(createStorageInfo("storage1"),
                createStorageInfo("storage2"));
            topologyServiceHelperMockedStatic.when(() -> TopologyServiceHelper.buildStorageInfos(any()))
                .thenReturn(storageInfos);

            TopologyServiceHelper.CheckResult checkResult =
                new TopologyServiceHelper.CheckResult(true, true, false);
            topologyServiceHelperMockedStatic.when(
                    () -> TopologyServiceHelper.shouldRefreshTopology(any(), any(), anyList(), any(), any()))
                .thenReturn(checkResult);

            // Mock resource manager
            List<Container> containers =
                Arrays.asList(createContainer("container1"), createContainer("container2"));
            when(resourceManager.tryRandomRemoveContainer(anyList(), anyBoolean())).thenReturn(containers);
            when(resourceManager.getExecutionSnapshot()).thenReturn(new ExecutionSnapshot());

            // Mock ServerConfigUtil
            serverConfigUtilMockedStatic.when(
                    () -> getGlobalNumberVarDirect(anyString()))
                .thenReturn(1234L);

            // Mock RebalanceUtil
            rebalanceUtilMockedStatic.when(() -> RebalanceUtil.isLightRebalance(anyString()))
                .thenReturn(false);

            // Mock topology builder
            TopologyEntity topologyEntity = createTopologyEntity();
            when(topologyBuilder.buildTopology(anyList(), anyList(), any(), anyString(), anyLong(),
                anyLong(),
                anyLong(),
                anyString()))
                .thenReturn(topologyEntity);

            // Mock RuntimeLeaderElector
            runtimeLeaderElectorMockedStatic.when(RuntimeLeaderElector::isDaemonLeader)
                .thenReturn(true);

            topologyServiceHelperMockedStatic.when(() -> lockAndCheck(any())).thenReturn(true);

            // When
            topologyService.tryBuild();

            // Then
            verify(transactionTemplate).execute(any());
        }
    }

    @Test
    public void testUpdateBinlogTaskConfig_WithExistingConfig_ShouldUpdate() {
        // Given
        List<BinlogTaskConfig> taskConfigs = Arrays.asList(
            createBinlogTaskConfig("task1", TaskType.DumperX),
            createBinlogTaskConfig("task2", TaskType.Dispatcher)
        );

        BinlogTaskConfig existingConfig = new BinlogTaskConfig();
        existingConfig.setId(1L);
        existingConfig.setTaskName("task1");
        existingConfig.setClusterId(TEST_CLUSTER_ID);

        when(taskConfigMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.of(existingConfig));

        // When
        topologyService.updateBinlogTaskConfig(TEST_CLUSTER_ID, taskConfigs);

        // Then
        verify(taskConfigMapper, times(2)).updateByPrimaryKeySelective(any(BinlogTaskConfig.class));
        verify(taskConfigMapper).delete(any(DeleteDSLCompleter.class));
    }

    @Test
    public void testUpdateBinlogTaskConfig_WithNewConfig_ShouldInsert() {
        // Given
        List<BinlogTaskConfig> taskConfigs = Arrays.asList(
            createBinlogTaskConfig("task1", TaskType.DumperX),
            createBinlogTaskConfig("task2", TaskType.Dispatcher)
        );

        when(taskConfigMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());

        // When
        topologyService.updateBinlogTaskConfig(TEST_CLUSTER_ID, taskConfigs);

        // Then
        verify(taskConfigMapper, times(2)).insert(any(BinlogTaskConfig.class));
        verify(taskConfigMapper).delete(any(DeleteDSLCompleter.class));
    }

    @Test
    public void testCleanStaleRunningInfo_WithLightRebalance_ShouldDeleteSpecificTasks() {
        // Given
        List<BinlogTaskConfig> taskConfigs = Arrays.asList(
            createBinlogTaskConfig("dumper_task", TaskType.DumperX),
            createBinlogTaskConfig("dispatcher_task", TaskType.Dispatcher)
        );

        // When
        topologyService.cleanStaleRunningInfo(taskConfigs, true); // lightRebalance = true

        // Then
        verify(dumperInfoMapper).delete(any(DeleteDSLCompleter.class));
        verify(taskInfoMapper).delete(any(DeleteDSLCompleter.class));
    }

    @Test
    public void testCleanStaleRunningInfo_WithFullRebalance_ShouldDeleteAllTasks() {
        // Given
        List<BinlogTaskConfig> taskConfigs = Arrays.asList(
            createBinlogTaskConfig("dumper_task", TaskType.DumperX),
            createBinlogTaskConfig("dispatcher_task", TaskType.Dispatcher)
        );

        // When
        topologyService.cleanStaleRunningInfo(taskConfigs, false); // lightRebalance = false

        // Then
        verify(dumperInfoMapper).delete(any(DeleteDSLCompleter.class));
        verify(taskInfoMapper).delete(any(DeleteDSLCompleter.class));
    }

    @Test
    public void testInitStorageHistory_WithNullStorageHistory_ShouldCreateNew() {
        try (MockedStatic<XStreamService> mockedXStreamService = mockStatic(XStreamService.class)) {
            // Given
            StorageHistoryInfo storageHistoryInfo = null;
            List<StorageInfo> storageInfos =
                Arrays.asList(createStorageInfo("storage1"), createStorageInfo("storage2"));

            List<XStream> xStreams = Arrays.asList(createXStream("stream1"), createXStream("stream2"));
            when(XStreamService.getXStreamsInCurrentCluster()).thenReturn(xStreams);

            mockConfig(BINLOGX_STREAM_GROUP_NAME, "test_group");

            // When
            topologyService.initStorageHistory(storageHistoryInfo, storageInfos, TEST_CLUSTER_ID);

            // Then
            verify(storageHistoryMapper).insert(any(StorageHistoryInfo.class));
            verify(storageHistDetailInfoMapper, times(2)).insert(any(StorageHistoryDetailInfo.class));
        }
    }

    @Test
    public void testInitStorageHistory_WithNonNullStorageHistory_ShouldDoNothing() {
        // Given
        StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
        List<StorageInfo> storageInfos = Arrays.asList(createStorageInfo("storage1"), createStorageInfo("storage2"));

        // When
        topologyService.initStorageHistory(storageHistoryInfo, storageInfos, TEST_CLUSTER_ID);

        // Then
        verify(storageHistoryMapper, never()).insert(any(StorageHistoryInfo.class));
        verify(storageHistDetailInfoMapper, never()).insert(any(StorageHistoryDetailInfo.class));
    }

    @Test
    public void testDeleteNonAvailableNode_WithEmptyContainers_ShouldDoNothing() {
        // Given
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        clusterSnapshot.setContainers(Collections.emptySet());

        // When
        topologyService.deleteNonAvailableNode(TEST_CLUSTER_ID, clusterSnapshot);

        // Then
        verify(nodeInfoMapper, never()).delete(any(DeleteDSLCompleter.class));
    }

    @Test
    public void testDeleteNonAvailableNode_WithNonEmptyContainers_ShouldDeleteNodes() {
        // Given
        ClusterSnapshot clusterSnapshot = new ClusterSnapshot();
        clusterSnapshot.setContainers(Collections.singleton("container1"));

        // When
        topologyService.deleteNonAvailableNode(TEST_CLUSTER_ID, clusterSnapshot);

        // Then
        verify(nodeInfoMapper).delete(any(DeleteDSLCompleter.class));
    }

    @Test
    public void testRecordScheduleHistory_ShouldInsertHistory() {
        // Given
        ExecutionSnapshot executionSnapshot = new ExecutionSnapshot();
        ClusterSnapshot clusterSnapshot = createClusterSnapshot();
        List<BinlogTaskConfig> taskConfigs = Arrays.asList(
            createBinlogTaskConfig("task1", TaskType.DumperX),
            createBinlogTaskConfig("task2", TaskType.Dispatcher)
        );

        // When
        topologyService.recordScheduleHistory(executionSnapshot, clusterSnapshot, taskConfigs);

        // Then
        ArgumentCaptor<BinlogScheduleHistory> captor = ArgumentCaptor.forClass(BinlogScheduleHistory.class);
        verify(scheduleHistoryMapper).insert(captor.capture());

        BinlogScheduleHistory captured = captor.getValue();
        assertEquals(clusterSnapshot.getVersion(), captured.getVersion().longValue());
        assertEquals(clusterSnapshot.getSubVersion(), captured.getSubVersion());
        assertEquals(TEST_CLUSTER_ID, captured.getClusterId());
        assertNotNull(captured.getContent());

        ScheduleHistoryContent content = JSONObject.parseObject(captured.getContent(), ScheduleHistoryContent.class);
        assertNotNull(content.getExecutionSnapshot());
        assertNotNull(content.getTaskConfigs());
        assertNotNull(content.getClusterSnapshot());
    }

    @Test
    public void testPersist_WhenNotLeader_ShouldReturnEarly() {
        try (MockedStatic<RuntimeLeaderElector> mockedLeader = mockStatic(RuntimeLeaderElector.class)) {
            // Given
            when(RuntimeLeaderElector.isDaemonLeader()).thenReturn(false);

            List<BinlogTaskConfig> taskConfigs = new ArrayList<>();
            List<StorageInfo> storageInfos = new ArrayList<>();
            ClusterSnapshot preClusterSnapshot = createClusterSnapshot();
            ClusterSnapshot postClusterSnapshot = createClusterSnapshot();
            StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
            ExecutionSnapshot executionSnapshot = new ExecutionSnapshot();

            // When
            topologyService.persist(taskConfigs, storageInfos, preClusterSnapshot, postClusterSnapshot,
                storageHistoryInfo, executionSnapshot, false);

            // Then
            verify(transactionTemplate, never()).execute(any());
        }
    }

    @Test
    public void testPersist_WhenLockCheckFails_ShouldReturnEarly() {
        try (MockedStatic<TopologyServiceHelper> mockedHelper = mockStatic(TopologyServiceHelper.class);
            MockedStatic<RuntimeLeaderElector> mockedLeader = mockStatic(RuntimeLeaderElector.class)) {
            // Given
            mockedLeader.when(RuntimeLeaderElector::isDaemonLeader).thenReturn(true);

            mockedHelper.when(() -> lockAndCheck(any())).thenReturn(false);

            List<BinlogTaskConfig> taskConfigs = new ArrayList<>();
            List<StorageInfo> storageInfos = new ArrayList<>();
            ClusterSnapshot preClusterSnapshot = createClusterSnapshot();
            ClusterSnapshot postClusterSnapshot = createClusterSnapshot();
            StorageHistoryInfo storageHistoryInfo = new StorageHistoryInfo();
            ExecutionSnapshot executionSnapshot = new ExecutionSnapshot();

            // When
            topologyService.persist(taskConfigs, storageInfos, preClusterSnapshot, postClusterSnapshot,
                storageHistoryInfo, executionSnapshot, false);

            // Then
            verify(transactionTemplate).execute(any());
            verify(taskConfigMapper, never()).selectOne(any(SelectDSLCompleter.class));
        }
    }

    // Helper methods to create test objects
    private ClusterSnapshot createClusterSnapshot() {
        ClusterSnapshot snapshot = new ClusterSnapshot();
        snapshot.setVersion(1L);
        snapshot.setTimestamp(System.currentTimeMillis());
        snapshot.setContainers(new HashSet<>(Arrays.asList("container1", "container2")));
        snapshot.setStorages(new HashSet<>(Arrays.asList("storage1", "storage2")));
        snapshot.setStorageHistoryTso(ExecutionConfig.ORIGIN_TSO);
        snapshot.setServerId(1234L);
        snapshot.setSubVersion(1L);
        return snapshot;
    }

    private StorageInfo createStorageInfo(String storageInstId) {
        StorageInfo storageInfo = new StorageInfo();
        storageInfo.setStorageInstId(storageInstId);
        return storageInfo;
    }

    private Container createContainer(String containerId) {
        Container container = Container.builder().containerId(containerId).build();
        container.setContainerId(containerId);
        return container;
    }

    private BinlogTaskConfig createBinlogTaskConfig(String taskName, TaskType taskType) {
        BinlogTaskConfig config = new BinlogTaskConfig();
        config.setTaskName(taskName);
        config.setRole(taskType.name());
        config.setClusterId(TEST_CLUSTER_ID);
        return config;
    }

    private XStream createXStream(String streamName) {
        XStream stream = new XStream();
        stream.setStreamName(streamName);
        return stream;
    }

    private TopologyEntity createTopologyEntity() {
        TopologyEntity entity = new TopologyEntity();
        entity.setTaskConfigs(Arrays.asList(
            createBinlogTaskConfig("dumper_task", TaskType.DumperX),
            createBinlogTaskConfig("dispatcher_task", TaskType.Dispatcher)
        ));
        entity.setServerId(1234L);
        return entity;
    }
}
