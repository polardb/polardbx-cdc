/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.task;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.po.DumperInfo;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.XStream;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.ResourceManager;
import com.aliyun.polardbx.binlog.scheduler.model.Container;
import com.aliyun.polardbx.binlog.scheduler.model.Resource;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpc.cdc.CdcServiceGrpc;
import com.aliyun.polardbx.rpc.cdc.MasterStatus;
import com.aliyun.polardbx.rpc.cdc.Request;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.common.fsmutil.DataImportTaskDetailInfo;
import com.aliyun.polardbx.rpl.taskmeta.ApplierConfig;
import com.aliyun.polardbx.rpl.taskmeta.ApplierType;
import com.aliyun.polardbx.rpl.taskmeta.DataImportMeta;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.ExtractorType;
import com.aliyun.polardbx.rpl.taskmeta.FSMMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.FilterType;
import com.aliyun.polardbx.rpl.taskmeta.FullExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.HostInfo;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.aliyun.polardbx.rpl.taskmeta.PipelineConfig;
import com.aliyun.polardbx.rpl.taskmeta.RdsExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import io.grpc.stub.StreamObserver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

public class FSMMetaManagerTest extends BaseTest {

    private JdbcTemplate polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        // 模拟 DynamicApplicationConfig 的配置
        setConfig(ConfigKeys.CLUSTER_TYPE, "REPLICA");
        setConfig(ConfigKeys.RPL_RESOURCE_USE_RATIO, "0.95");
        setConfig(ConfigKeys.RPL_DEFAULT_LOWER_MEMORY, "1024");

        registerSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
    }

    @After
    public void after() {
        unregisterSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
    }

    @Test
    public void testGetStateMachineDetail() {
        ResultCode<DataImportTaskDetailInfo> ret = FSMMetaManager.getStateMachineDetail(1);
        Assert.assertNotNull(ret);
    }

    @Test
    public void testComputeIncTaskMemory_ReplicaCluster() {

        ResourceManager resourceManager = mock(ResourceManager.class);
        // 模拟 ResourceManager 的可用容器
        List<Container> workers = new ArrayList<>();
        workers.add(Container.builder().hostString("worker1")
            .capability(Resource.builder().memory_mb(8192).build()).build());
        workers.add(Container.builder().hostString("worker2")
            .capability(Resource.builder().memory_mb(8192).build()).build());
        workers.add(Container.builder().hostString("worker3")
            .capability(Resource.builder().memory_mb(8192).build()).build());
        when(resourceManager.availableContainers()).thenReturn(workers);
        workers.add(Container.builder().hostString("worker4")
            .capability(Resource.builder().memory_mb(8192).build()).build());
        when(resourceManager.availableContainers()).thenReturn(workers);
        workers.add(Container.builder().hostString("worker5")
            .capability(Resource.builder().memory_mb(8192).build()).build());
        when(resourceManager.availableContainers()).thenReturn(workers);
        workers.add(Container.builder().hostString("worker6")
            .capability(Resource.builder().memory_mb(8192).build()).build());
        when(resourceManager.availableContainers()).thenReturn(workers);

        int taskNum = 10;
        int expectedMemory = ((((int) (8192 * 0.95) - 1024) / 4)) / 8 * 8;
        FSMMetaManager.RESOURCE_MANAGER = resourceManager;
        int result = FSMMetaManager.computeIncTaskMemory(taskNum);
        Assert.assertEquals(expectedMemory, result);

    }

    @Test(expected = PolardbxException.class)
    public void testComputeIncTaskMemory_NoAvailableWorkers() {
        ResourceManager resourceManager = mock(ResourceManager.class);
        // 模拟 ResourceManager 无可用容器
        when(resourceManager.availableContainers()).thenReturn(new ArrayList<>());
        int taskNum = 10;
        FSMMetaManager.RESOURCE_MANAGER = resourceManager;
        FSMMetaManager.computeIncTaskMemory(taskNum);
    }

    @Test
    public void testComputeIncTaskMemory_SingleWorker() {
        ResourceManager resourceManager = mock(ResourceManager.class);
        // 模拟 ResourceManager 只有一个可用容器
        List<Container> workers = new ArrayList<>();
        workers.add(Container.builder().hostString("worker1")
            .capability(Resource.builder().memory_mb(8192).build()).build());
        when(resourceManager.availableContainers()).thenReturn(workers);
        int taskNum = 10;
        int expectedMemory = ((((int) (8192 * 0.95) - 1024) / 10)) / 8 * 8;
        FSMMetaManager.RESOURCE_MANAGER = resourceManager;
        int result = FSMMetaManager.computeIncTaskMemory(taskNum);
        Assert.assertEquals(expectedMemory, result);
    }

    @Test
    public void testComputeIncTaskMemory_MainInstance() {
        // 模拟在主实例上运行
        setConfig("cluster_type", "BINLOG");
        int taskNum = 10;
        int expectedMemory = DynamicApplicationConfig.getInt(ConfigKeys.RPL_DEFAULT_MEMORY); // 使用默认内存
        int result = FSMMetaManager.computeIncTaskMemory(taskNum);
        Assert.assertEquals(expectedMemory, result);
        setConfig("cluster_type", "REPLICA");
    }

    @Test
    public void findMainStartPosition_LeaderExists_ReturnsPosition() throws Throwable {
        DumperInfoMapper dumperInfoMapper = Mockito.mock(DumperInfoMapper.class);
        CdcServiceGrpc.CdcServiceStub cdcServiceStub = Mockito.mock(CdcServiceGrpc.CdcServiceStub.class);
        try (final MockedStatic<CdcServiceGrpc> cdcServiceGrpcMockedStatic = mockStatic(CdcServiceGrpc.class)) {
            cdcServiceGrpcMockedStatic.when(() -> CdcServiceGrpc.newStub(any())).thenReturn(cdcServiceStub);
            DumperInfo dumperInfo = new DumperInfo();
            dumperInfo.setIp("127.0.0.1");
            dumperInfo.setPort(12345);
            Mockito.when(dumperInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.of(dumperInfo));

            registerSpringObject("dumperInfoMapper", dumperInfoMapper);

            BinlogPosition expectedPosition = new BinlogPosition("file1", 100, -1, -1);
            Mockito.doAnswer(invocation -> {
                    StreamObserver<MasterStatus> observer = invocation.getArgument(1);
                    observer.onNext(MasterStatus.newBuilder().setFile("file1").setPosition(100).build());
                    observer.onCompleted();
                    return null;
                }).when(cdcServiceStub)
                .showMasterStatus(ArgumentMatchers.any(Request.class), ArgumentMatchers.any(StreamObserver.class));

            BinlogPosition position = FSMMetaManager.findMainStartPosition();
            Assert.assertEquals(expectedPosition, position);
            unregisterSpringObject("dumperInfoMapper", dumperInfoMapper);
        }
    }

    @Test(expected = NullPointerException.class)
    public void findMainStartPosition_LeaderDoesNotExist_ThrowsException() throws Throwable {
        DumperInfoMapper dumperInfoMapper = Mockito.mock(DumperInfoMapper.class);
        registerSpringObject("dumperInfoMapper", dumperInfoMapper);
        Mockito.when(dumperInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());
        FSMMetaManager.findMainStartPosition();
        unregisterSpringObject("dumperInfoMapper", dumperInfoMapper);
    }

    @Test
    public void findStreamStartPosition_StreamExists_ReturnsPosition() {
        XStream xStream = new XStream();
        xStream.setLatestCursor("{\"fileName\":\"file1\",\"filePosition\":100}");
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = mockStatic(DbTaskMetaManager.class)) {
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getXStreamByStreamName("stream1"))
                .thenReturn(xStream);
            BinlogPosition position = FSMMetaManager.findStreamStartPosition("stream1");
            Assert.assertEquals(new BinlogPosition("file1", 0, -1, -1), position);
        }
    }

    @Test
    public void findStreamStartPosition_StreamDoesNotExist_ReturnsNull() {
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = mockStatic(DbTaskMetaManager.class)) {
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getXStreamByStreamName("stream1"))
                .thenReturn(null);
            BinlogPosition position = FSMMetaManager.findStreamStartPosition("stream1");
            Assert.assertNull(position);
        }
    }

    @Test
    public void createOneImportTask_FullCopy_CreatesCorrectConfigs() {
        RplService rplService = new RplService();
        rplService.setId(1L);
        rplService.setServiceType(ServiceType.FULL_COPY.name());
        rplService.setStateMachineId(1L);

        DataImportMeta meta = new DataImportMeta();
        meta.setProducerParallelCount(4);
        meta.setFetchBatchSize(100);
        meta.setRingBufferSize(1024);
        meta.setConsumerParallelCount(2);
        meta.setApplierType(ApplierType.FULL_COPY);
        meta.setFullMergeBatchSize(500);
        meta.setCdcClusterId("cluster1");

        DataImportMeta.PhysicalMeta physicalMeta = new DataImportMeta.PhysicalMeta();
        physicalMeta.setRdsUid("uid1");
        physicalMeta.setRdsBid("bid1");
        physicalMeta.setRdsInstanceId("instance1");

        int sequenceId = 1;

        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = mockStatic(DbTaskMetaManager.class)) {
            dbTaskMetaManagerMockedStatic.when(
                    () -> DbTaskMetaManager.addTaskWithMemory(anyLong(), anyLong(), anyString(), anyString(), anyString(),
                        any(ServiceType.class), anyInt(), anyString(), anyInt()))
                .thenReturn(new RplTask());
            FSMMetaManager.createOneImportTask(rplService, meta, physicalMeta, sequenceId);

            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.addTaskWithMemory(
                eq(rplService.getStateMachineId()),
                eq(rplService.getId()),
                argThat(extractorConfigStr -> {
                    FullExtractorConfig config = JSON.parseObject(extractorConfigStr, FullExtractorConfig.class);
                    return config.getExtractorType() == ExtractorType.DATA_IMPORT_FULL &&
                        config.getFilterType() == FilterType.IMPORT_FILTER &&
                        config.getPrivateMeta().equals(JSON.toJSONString(physicalMeta));
                }),
                argThat(pipelineConfigStr -> {
                    PipelineConfig config = JSON.parseObject(pipelineConfigStr, PipelineConfig.class);
                    return config.isSupportXa();
                }),
                argThat(applierConfigStr -> {
                    ApplierConfig config = JSON.parseObject(applierConfigStr, ApplierConfig.class);
                    return config.getApplierType() == ApplierType.FULL_COPY &&
                        !config.isEnableDdl() &&
                        config.getLogCommitLevel() == RplConstants.LOG_NO_COMMIT;
                }),
                eq(ServiceType.FULL_COPY),
                eq(sequenceId),
                eq("cluster1"),
                eq(RplConstants.DEFAULT_MEMORY_SIZE_FOR_FULL_COPY)
            ));
        }
    }

    @Test
    public void createOneImportTask_IncCopy_CreatesCorrectConfigs() {
        RplService rplService = new RplService();
        rplService.setId(1L);
        rplService.setServiceType(ServiceType.INC_COPY.name());
        rplService.setStateMachineId(1L);

        DataImportMeta meta = new DataImportMeta();
        meta.setProducerParallelCount(4);
        meta.setFetchBatchSize(100);
        meta.setRingBufferSize(1024);
        meta.setConsumerParallelCount(2);
        meta.setApplierType(ApplierType.MERGE);
        meta.setIncMergeBatchSize(500);
        meta.setCdcClusterId("cluster1");

        DataImportMeta.PhysicalMeta physicalMeta = new DataImportMeta.PhysicalMeta();
        physicalMeta.setRdsUid("uid1");
        physicalMeta.setRdsBid("bid1");
        physicalMeta.setRdsInstanceId("instance1");

        int sequenceId = 1;
        int incMemory = DynamicApplicationConfig.getInt(ConfigKeys.RPL_DEFAULT_MEMORY);

        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = mockStatic(DbTaskMetaManager.class)) {
            RplTask task = new RplTask();
            task.setId(1L);
            dbTaskMetaManagerMockedStatic.when(
                    () -> DbTaskMetaManager.addTaskWithMemory(anyLong(), anyLong(), anyString(), anyString(), anyString(),
                        any(ServiceType.class), anyInt(), anyString(), anyInt()))
                .thenReturn(task);
            FSMMetaManager.createOneImportTask(rplService, meta, physicalMeta, sequenceId);
            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.addTaskWithMemory(
                eq(rplService.getStateMachineId()),
                eq(rplService.getId()),
                argThat(extractorConfigStr -> {
                    RdsExtractorConfig config = JSON.parseObject(extractorConfigStr, RdsExtractorConfig.class);
                    return config.getExtractorType() == ExtractorType.DATA_IMPORT_INC &&
                        config.getFilterType() == FilterType.IMPORT_FILTER &&
                        config.getPrivateMeta().equals(JSON.toJSONString(physicalMeta)) &&
                        config.getUid().equals("uid1") &&
                        config.getBid().equals("bid1") &&
                        config.getRdsInstanceId().equals("instance1");
                }),
                argThat(pipelineConfigStr -> {
                    PipelineConfig config = JSON.parseObject(pipelineConfigStr, PipelineConfig.class);
                    return config.isSupportXa();
                }),
                argThat(applierConfigStr -> {
                    ApplierConfig config = JSON.parseObject(applierConfigStr, ApplierConfig.class);
                    return config.getApplierType() == ApplierType.MERGE &&
                        !config.isEnableDdl() &&
                        config.getLogCommitLevel() == RplConstants.LOG_ALL_COMMIT;
                }),
                eq(ServiceType.INC_COPY),
                eq(sequenceId),
                eq("cluster1"),
                eq(incMemory)
            ));

            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.updateBinlogPosition(anyLong(), anyString()));
        }
    }

    @Test
    public void getBackflowApplierHostInfo_NonEmptyBackFlowMetaList_ReturnsHostInfo() {
        // 测试用例：backFlowMetaList包含一个PhysicalMeta对象
        DataImportMeta dataImportMeta = new DataImportMeta();
        DataImportMeta.PhysicalMeta physicalMeta = new DataImportMeta.PhysicalMeta();
        physicalMeta.setDstHost("localhost");
        physicalMeta.setDstPort(3306);
        physicalMeta.setDstUser("user");
        physicalMeta.setDstPassword("password");
        physicalMeta.setDstType(HostType.POLARX1);
        physicalMeta.setDstServerId(100L);

        List<DataImportMeta.PhysicalMeta> backFlowMetaList = new ArrayList<>();
        backFlowMetaList.add(physicalMeta);
        dataImportMeta.setBackFlowMetaList(backFlowMetaList);

        HostInfo hostInfo = FSMMetaManager.getBackflowApplierHostInfo(dataImportMeta);

        Assert.assertEquals("localhost", hostInfo.getHost());
        Assert.assertEquals(3306, hostInfo.getPort());
        Assert.assertEquals("user", hostInfo.getUserName());
        Assert.assertEquals("password", hostInfo.getPassword());
        Assert.assertEquals(HostType.POLARX1, hostInfo.getType());
        Assert.assertEquals(100L, hostInfo.getServerId());
    }

    @Test
    public void createImportTasks() {
        RplService rplService = new RplService();
        DataImportMeta meta = new DataImportMeta();
        rplService.setId(1L);
        rplService.setStateMachineId(1L);
        meta.setProducerParallelCount(1);
        meta.setFetchBatchSize(100);
        meta.setRingBufferSize(1024);
        meta.setConsumerParallelCount(1);
        meta.setIncMergeBatchSize(10);
        meta.setFullMergeBatchSize(100);
        meta.setApplierType(ApplierType.MERGE);
        meta.setCdcClusterId("111");

        List<DataImportMeta.PhysicalMeta> metaList = new ArrayList<>();
        DataImportMeta.PhysicalMeta physicalMeta = new DataImportMeta.PhysicalMeta();
        physicalMeta.setSrcHost("srcHost");
        physicalMeta.setSrcPort(3306);
        physicalMeta.setSrcUser("srcUser");
        physicalMeta.setSrcPassword("srcPassword");
        physicalMeta.setSrcType(HostType.MYSQL);
        metaList.add(physicalMeta);
        meta.setMetaList(metaList);

        meta.setValidationMeta(new DataImportMeta.ValidationMeta());
        meta.getValidationMeta().setSrcLogicalConnInfo(new DataImportMeta.ConnInfo());

        List<DataImportMeta.PhysicalMeta> backFlowMetaList = new ArrayList<>();
        DataImportMeta.PhysicalMeta backFlowPhysicalMeta = new DataImportMeta.PhysicalMeta();
        backFlowPhysicalMeta.setDstHost("dstHost");
        backFlowPhysicalMeta.setDstPort(3306);
        backFlowPhysicalMeta.setDstUser("dstUser");
        backFlowPhysicalMeta.setDstPassword("dstPassword");
        backFlowPhysicalMeta.setDstType(HostType.MYSQL);
        backFlowMetaList.add(backFlowPhysicalMeta);
        meta.setBackFlowMetaList(backFlowMetaList);
        RplTask rplTask = new RplTask();
        rplTask.setId(1L);
        try (
            MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(DbTaskMetaManager.class);
            MockedStatic<CommonUtil> commonUtilMockedStatic = Mockito.mockStatic(CommonUtil.class)) {
            dbTaskMetaManagerMockedStatic.when(
                    () -> DbTaskMetaManager.addTaskWithMemory(anyLong(), anyLong(), anyString(), anyString(), anyString(),
                        any(ServiceType.class), anyInt(), anyString(), anyInt()))
                .thenReturn(rplTask);
            commonUtilMockedStatic.when(CommonUtil::createInitialBinlogPosition).thenReturn("initialPosition");

            // full
            rplService.setServiceType(ServiceType.FULL_COPY.name());
            FSMMetaManager.createImportTasks(rplService, meta);
            dbTaskMetaManagerMockedStatic.verify(() ->
                DbTaskMetaManager.addTaskWithMemory(anyLong(), anyLong(), anyString(), anyString(), anyString(),
                    eq(ServiceType.FULL_COPY), anyInt(), anyString(), anyInt()));

            // inc
            rplService.setServiceType(ServiceType.INC_COPY.name());
            FSMMetaManager.createImportTasks(rplService, meta);
            dbTaskMetaManagerMockedStatic.verify(() ->
                DbTaskMetaManager.addTaskWithMemory(anyLong(), anyLong(), anyString(), anyString(), anyString(),
                    eq(ServiceType.INC_COPY), anyInt(), anyString(), anyInt()));
            dbTaskMetaManagerMockedStatic.verify(() ->
                DbTaskMetaManager.updateBinlogPosition(eq(1L), eq("initialPosition")));

            // full validation
            rplService.setServiceType(ServiceType.FULL_VALIDATION.name());
            FSMMetaManager.createImportTasks(rplService, meta);
            dbTaskMetaManagerMockedStatic.verify(() ->
                DbTaskMetaManager.addTaskWithMemory(anyLong(), anyLong(), anyString(), anyString(), anyString(),
                    eq(ServiceType.FULL_VALIDATION), anyInt(), anyString(), anyInt()));

            // reconciliation
            rplService.setServiceType(ServiceType.RECONCILIATION.name());
            FSMMetaManager.createImportTasks(rplService, meta);
            dbTaskMetaManagerMockedStatic.verify(() ->
                DbTaskMetaManager.addTaskWithMemory(anyLong(), anyLong(), anyString(), anyString(), anyString(),
                    eq(ServiceType.RECONCILIATION), anyInt(), anyString(), anyInt()));

            // cdc inc
            rplService.setServiceType(ServiceType.CDC_INC.name());
            FSMMetaManager.createImportTasks(rplService, meta);
            dbTaskMetaManagerMockedStatic.verify(() ->
                DbTaskMetaManager.addTaskWithMemory(anyLong(), anyLong(), anyString(), anyString(), anyString(),
                    eq(ServiceType.CDC_INC), anyInt(), anyString(), anyInt()));
        }
    }
}
