/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

// 导入需要的类
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.rpl.taskmeta.RdsExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.apache.commons.lang3.tuple.MutableTriple;
import com.aliyun.polardbx.rpl.taskmeta.ReplicaMeta;

/**
 * FSMMetaManager测试类
 */
public class FSMMetaManagerTest extends BaseTest {

    private static final long TEST_FSM_ID = 1L;
    private static final long TEST_SERVICE_ID = 10L;
    private static final long TEST_TASK_ID = 100L;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        // 初始化测试环境
        JdbcTemplate polarxJdbcTemplate = mock(JdbcTemplate.class);
        registerSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
    }

    /**
     * 测试enableHeartbeat方法 - 验证正常情况下的功能
     */
    @Test
    public void testEnableHeartbeat_NormalCase() {
        // 准备测试数据
        RplService mockService = mock(RplService.class);
        when(mockService.getId()).thenReturn(TEST_SERVICE_ID);

        RplTask mockTask = mock(RplTask.class);
        when(mockTask.getId()).thenReturn(TEST_TASK_ID);

        RdsExtractorConfig extractorConfig = new RdsExtractorConfig();
        extractorConfig.setEnableDetectHeartbeat(false);
        extractorConfig.setCreateHeartbeatTable(false);
        String configStr = JSON.toJSONString(extractorConfig);

        RplTaskConfig mockTaskConfig = mock(RplTaskConfig.class);
        when(mockTaskConfig.getTaskId()).thenReturn(TEST_TASK_ID);
        when(mockTaskConfig.getExtractorConfig()).thenReturn(configStr);

        List<RplTask> mockTasks = new ArrayList<>();
        mockTasks.add(mockTask);

        List<RplTaskConfig> mockTaskConfigs = new ArrayList<>();
        mockTaskConfigs.add(mockTaskConfig);

        Set<Long> taskIds = new HashSet<>();
        taskIds.add(TEST_TASK_ID);

        // 使用MockedStatic来mock静态方法
        try (MockedStatic<DbTaskMetaManager> mockedDbTaskMetaManager = mockStatic(DbTaskMetaManager.class)) {
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.getService(TEST_FSM_ID, ServiceType.INC_COPY))
                .thenReturn(mockService);
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.listTaskByService(TEST_SERVICE_ID))
                .thenReturn(mockTasks);
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.listTaskConfig(taskIds)).thenReturn(mockTaskConfigs);

            // 调用被测试的方法
            ResultCode<?> result = FSMMetaManager.enableHeartbeat(TEST_FSM_ID, true);

            // 验证结果
            Assert.assertNotNull(result);
            Assert.assertTrue((Boolean) result.getData());
            Assert.assertEquals("success", result.getMsg());

            // 验证DbTaskMetaManager.updateTaskConfig被调用
            mockedDbTaskMetaManager.verify(
                () -> DbTaskMetaManager.updateTaskConfig(eq(TEST_TASK_ID), anyString(), eq(null), eq(null), eq(null)),
                times(1));
        }
    }

    /**
     * 测试enableHeartbeat方法 - 验证当没有任务时的情况
     */
    @Test
    public void testEnableHeartbeat_NoTasks() {
        // 准备测试数据
        RplService mockService = mock(RplService.class);
        when(mockService.getId()).thenReturn(TEST_SERVICE_ID);

        List<RplTask> mockTasks = new ArrayList<>(); // 空列表

        // 使用MockedStatic来mock静态方法
        try (MockedStatic<DbTaskMetaManager> mockedDbTaskMetaManager = mockStatic(DbTaskMetaManager.class)) {
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.getService(TEST_FSM_ID, ServiceType.INC_COPY))
                .thenReturn(mockService);
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.listTaskByService(TEST_SERVICE_ID))
                .thenReturn(mockTasks);

            // 调用被测试的方法
            ResultCode<?> result = FSMMetaManager.enableHeartbeat(TEST_FSM_ID, true);

            // 验证结果
            Assert.assertNotNull(result);
            Assert.assertTrue((Boolean) result.getData());
            Assert.assertEquals("success", result.getMsg());

            // 验证DbTaskMetaManager.updateTaskConfig没有被调用
            mockedDbTaskMetaManager.verify(
                () -> DbTaskMetaManager.updateTaskConfig(anyLong(), anyString(), any(), any(), any()), never());
        }
    }

    /**
     * 测试enableHeartbeat方法 - 验证enableHeartbeat参数为false的情况
     */
    @Test
    public void testEnableHeartbeat_DisableHeartbeat() {
        // 准备测试数据
        RplService mockService = mock(RplService.class);
        when(mockService.getId()).thenReturn(TEST_SERVICE_ID);

        RplTask mockTask = mock(RplTask.class);
        when(mockTask.getId()).thenReturn(TEST_TASK_ID);

        RdsExtractorConfig extractorConfig = new RdsExtractorConfig();
        extractorConfig.setEnableDetectHeartbeat(true);
        extractorConfig.setCreateHeartbeatTable(true);
        String configStr = JSON.toJSONString(extractorConfig);

        RplTaskConfig mockTaskConfig = mock(RplTaskConfig.class);
        when(mockTaskConfig.getTaskId()).thenReturn(TEST_TASK_ID);
        when(mockTaskConfig.getExtractorConfig()).thenReturn(configStr);

        List<RplTask> mockTasks = new ArrayList<>();
        mockTasks.add(mockTask);

        List<RplTaskConfig> mockTaskConfigs = new ArrayList<>();
        mockTaskConfigs.add(mockTaskConfig);

        Set<Long> taskIds = new HashSet<>();
        taskIds.add(TEST_TASK_ID);

        // 使用MockedStatic来mock静态方法
        try (MockedStatic<DbTaskMetaManager> mockedDbTaskMetaManager = mockStatic(DbTaskMetaManager.class)) {
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.getService(TEST_FSM_ID, ServiceType.INC_COPY))
                .thenReturn(mockService);
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.listTaskByService(TEST_SERVICE_ID))
                .thenReturn(mockTasks);
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.listTaskConfig(taskIds)).thenReturn(mockTaskConfigs);

            // 调用被测试的方法
            ResultCode<?> result = FSMMetaManager.enableHeartbeat(TEST_FSM_ID, false);

            // 验证结果
            Assert.assertNotNull(result);
            Assert.assertTrue((Boolean) result.getData());
            Assert.assertEquals("success", result.getMsg());

            // 验证DbTaskMetaManager.updateTaskConfig被调用
            mockedDbTaskMetaManager.verify(
                () -> DbTaskMetaManager.updateTaskConfig(eq(TEST_TASK_ID), anyString(), eq(null), eq(null), eq(null)),
                times(1));
        }
    }

    /**
     * 测试enableHeartbeat方法 - 验证1451-1452行代码逻辑
     * 该测试验证当enableHeartbeat为true时，RdsExtractorConfig的createHeartbeatTable和enableDetectHeartbeat属性被正确设置
     */
    @Test
    public void testEnableHeartbeat_EnableHeartbeatTrue() {
        // 准备测试数据
        RplService mockService = mock(RplService.class);
        when(mockService.getId()).thenReturn(TEST_SERVICE_ID);

        RplTask mockTask = mock(RplTask.class);
        when(mockTask.getId()).thenReturn(TEST_TASK_ID);

        // 创建一个初始配置，其中心跳相关属性为false
        RdsExtractorConfig extractorConfig = new RdsExtractorConfig();
        extractorConfig.setEnableDetectHeartbeat(false);
        extractorConfig.setCreateHeartbeatTable(false);
        String configStr = JSON.toJSONString(extractorConfig);

        RplTaskConfig mockTaskConfig = mock(RplTaskConfig.class);
        when(mockTaskConfig.getTaskId()).thenReturn(TEST_TASK_ID);
        when(mockTaskConfig.getExtractorConfig()).thenReturn(configStr);

        List<RplTask> mockTasks = new ArrayList<>();
        mockTasks.add(mockTask);

        List<RplTaskConfig> mockTaskConfigs = new ArrayList<>();
        mockTaskConfigs.add(mockTaskConfig);

        Set<Long> taskIds = new HashSet<>();
        taskIds.add(TEST_TASK_ID);

        // 使用MockedStatic来mock静态方法
        try (MockedStatic<DbTaskMetaManager> mockedDbTaskMetaManager = mockStatic(DbTaskMetaManager.class)) {
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.getService(TEST_FSM_ID, ServiceType.INC_COPY))
                .thenReturn(mockService);
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.listTaskByService(TEST_SERVICE_ID))
                .thenReturn(mockTasks);
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.listTaskConfig(taskIds)).thenReturn(mockTaskConfigs);

            // 调用被测试的方法，启用心跳
            ResultCode<?> result = FSMMetaManager.enableHeartbeat(TEST_FSM_ID, true);

            // 验证结果
            Assert.assertNotNull(result);
            Assert.assertTrue((Boolean) result.getData());
            Assert.assertEquals("success", result.getMsg());

            // 验证DbTaskMetaManager.updateTaskConfig被调用
            mockedDbTaskMetaManager.verify(
                () -> DbTaskMetaManager.updateTaskConfig(eq(TEST_TASK_ID), anyString(), eq(null), eq(null), eq(null)),
                times(1));

            // 验证传递给updateTaskConfig的配置字符串中，心跳相关属性被设置为true
            mockedDbTaskMetaManager.verify(
                () -> DbTaskMetaManager.updateTaskConfig(eq(TEST_TASK_ID), argThat(config -> {
                    RdsExtractorConfig updatedConfig = JSON.parseObject(config, RdsExtractorConfig.class);
                    return updatedConfig.isEnableDetectHeartbeat() && updatedConfig.isCreateHeartbeatTable();
                }), eq(null), eq(null), eq(null)), times(1));
        }
    }

    /**
     * 测试enableHeartbeat方法 - 验证1451-1452行代码逻辑
     * 该测试验证当enableHeartbeat为false时，RdsExtractorConfig的createHeartbeatTable和enableDetectHeartbeat属性被正确设置
     */
    @Test
    public void testEnableHeartbeat_EnableHeartbeatFalse() {
        // 准备测试数据
        RplService mockService = mock(RplService.class);
        when(mockService.getId()).thenReturn(TEST_SERVICE_ID);

        RplTask mockTask = mock(RplTask.class);
        when(mockTask.getId()).thenReturn(TEST_TASK_ID);

        // 创建一个初始配置，其中心跳相关属性为true
        RdsExtractorConfig extractorConfig = new RdsExtractorConfig();
        extractorConfig.setEnableDetectHeartbeat(true);
        extractorConfig.setCreateHeartbeatTable(true);
        String configStr = JSON.toJSONString(extractorConfig);

        RplTaskConfig mockTaskConfig = mock(RplTaskConfig.class);
        when(mockTaskConfig.getTaskId()).thenReturn(TEST_TASK_ID);
        when(mockTaskConfig.getExtractorConfig()).thenReturn(configStr);

        List<RplTask> mockTasks = new ArrayList<>();
        mockTasks.add(mockTask);

        List<RplTaskConfig> mockTaskConfigs = new ArrayList<>();
        mockTaskConfigs.add(mockTaskConfig);

        Set<Long> taskIds = new HashSet<>();
        taskIds.add(TEST_TASK_ID);

        // 使用MockedStatic来mock静态方法
        try (MockedStatic<DbTaskMetaManager> mockedDbTaskMetaManager = mockStatic(DbTaskMetaManager.class)) {
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.getService(TEST_FSM_ID, ServiceType.INC_COPY))
                .thenReturn(mockService);
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.listTaskByService(TEST_SERVICE_ID))
                .thenReturn(mockTasks);
            mockedDbTaskMetaManager.when(() -> DbTaskMetaManager.listTaskConfig(taskIds)).thenReturn(mockTaskConfigs);

            // 调用被测试的方法，禁用心跳
            ResultCode<?> result = FSMMetaManager.enableHeartbeat(TEST_FSM_ID, false);

            // 验证结果
            Assert.assertNotNull(result);
            Assert.assertTrue((Boolean) result.getData());
            Assert.assertEquals("success", result.getMsg());

            // 验证DbTaskMetaManager.updateTaskConfig被调用
            mockedDbTaskMetaManager.verify(
                () -> DbTaskMetaManager.updateTaskConfig(eq(TEST_TASK_ID), anyString(), eq(null), eq(null), eq(null)),
                times(1));

            // 验证传递给updateTaskConfig的配置字符串中，心跳相关属性被设置为false
            mockedDbTaskMetaManager.verify(
                () -> DbTaskMetaManager.updateTaskConfig(eq(TEST_TASK_ID), argThat(config -> {
                    RdsExtractorConfig updatedConfig = JSON.parseObject(config, RdsExtractorConfig.class);
                    return !updatedConfig.isEnableDetectHeartbeat() && !updatedConfig.isCreateHeartbeatTable();
                }), eq(null), eq(null), eq(null)), times(1));
        }
    }

    /**
     * 测试checkMasterHostAndPort方法 - 验证MasterHost和MasterPort在masterInfos列表中的情况
     */
    @Test
    public void testCheckMasterHostAndPort_HostPortInList() {
        // 准备测试数据
        ReplicaMeta meta = new ReplicaMeta();
        meta.setMasterHost("localhost");
        meta.setMasterPort(3306);

        List<MutableTriple<String, Integer, String>> masterInfos = new ArrayList<>();
        masterInfos.add(new MutableTriple<>("localhost", 3306, "inst1"));
        masterInfos.add(new MutableTriple<>("otherhost", 3307, "inst2"));

        // 调用被测试的方法，应该不抛出异常
        FSMMetaManager.checkMasterHostAndPort(masterInfos, meta);

        // 如果没有抛出异常，测试通过
        Assert.assertTrue(true);
    }

    /**
     * 测试checkMasterHostAndPort方法 - 验证MasterHost和MasterPort在masterInfos列表中第一个元素的情况
     */
    @Test
    public void testCheckMasterHostAndPort_HostPortInFirstElement() {
        // 准备测试数据
        ReplicaMeta meta = new ReplicaMeta();
        meta.setMasterHost("localhost");
        meta.setMasterPort(3306);

        List<MutableTriple<String, Integer, String>> masterInfos = new ArrayList<>();
        masterInfos.add(new MutableTriple<>("localhost", 3306, "inst1"));
        masterInfos.add(new MutableTriple<>("otherhost", 3307, "inst2"));
        masterInfos.add(new MutableTriple<>("anotherhost", 3308, "inst3"));

        // 调用被测试的方法，应该不抛出异常
        FSMMetaManager.checkMasterHostAndPort(masterInfos, meta);

        // 如果没有抛出异常，测试通过
        Assert.assertTrue(true);
    }

    /**
     * 测试checkMasterHostAndPort方法 - 验证MasterHost和MasterPort在masterInfos列表中最后一个元素的情况
     */
    @Test
    public void testCheckMasterHostAndPort_HostPortInLastElement() {
        // 准备测试数据
        ReplicaMeta meta = new ReplicaMeta();
        meta.setMasterHost("localhost");
        meta.setMasterPort(3306);

        List<MutableTriple<String, Integer, String>> masterInfos = new ArrayList<>();
        masterInfos.add(new MutableTriple<>("otherhost", 3307, "inst2"));
        masterInfos.add(new MutableTriple<>("anotherhost", 3308, "inst3"));
        masterInfos.add(new MutableTriple<>("localhost", 3306, "inst1"));

        // 调用被测试的方法，应该不抛出异常
        FSMMetaManager.checkMasterHostAndPort(masterInfos, meta);

        // 如果没有抛出异常，测试通过
        Assert.assertTrue(true);
    }

    /**
     * 测试checkMasterHostAndPort方法 - 验证MasterHost和MasterPort不在masterInfos列表中时抛出异常的情况
     */
    @Test
    public void testCheckMasterHostAndPort_HostPortNotInList() {
        // 准备测试数据
        ReplicaMeta meta = new ReplicaMeta();
        meta.setMasterHost("localhost");
        meta.setMasterPort(3306);

        List<MutableTriple<String, Integer, String>> masterInfos = new ArrayList<>();
        masterInfos.add(new MutableTriple<>("otherhost", 3307, "inst1"));
        masterInfos.add(new MutableTriple<>("anotherhost", 3308, "inst2"));
        boolean exception = false;

        // 调用被测试的方法并验证异常
        try {
            FSMMetaManager.checkMasterHostAndPort(masterInfos, meta);
            Assert.fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            Assert.assertEquals("MasterHost,MasterPort not in masterInfos", e.getMessage());
            exception = true;
        }
        Assert.assertTrue(exception);
    }

    /**
     * 测试checkMasterHostAndPort方法 - 验证MasterHost匹配但MasterPort不匹配的情况
     */
    @Test
    public void testCheckMasterHostAndPort_HostMatchPortNotMatch() {
        // 准备测试数据
        ReplicaMeta meta = new ReplicaMeta();
        meta.setMasterHost("localhost");
        meta.setMasterPort(3306);

        List<MutableTriple<String, Integer, String>> masterInfos = new ArrayList<>();
        masterInfos.add(new MutableTriple<>("localhost", 3307, "inst1")); // 端口不匹配
        masterInfos.add(new MutableTriple<>("otherhost", 3306, "inst2")); // 主机不匹配

        boolean exception = false;

        // 调用被测试的方法并验证异常
        try {
            FSMMetaManager.checkMasterHostAndPort(masterInfos, meta);
            Assert.fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            Assert.assertEquals("MasterHost,MasterPort not in masterInfos", e.getMessage());
            exception = true;
        }
        Assert.assertTrue(exception);
    }

    /**
     * 测试checkMasterHostAndPort方法 - 验证空列表的情况
     */
    @Test
    public void testCheckMasterHostAndPort_EmptyList() {
        // 准备测试数据
        ReplicaMeta meta = new ReplicaMeta();
        meta.setMasterHost("localhost");
        meta.setMasterPort(3306);

        List<MutableTriple<String, Integer, String>> masterInfos = new ArrayList<>();

        boolean exception = false;
        // 调用被测试的方法并验证异常
        try {
            FSMMetaManager.checkMasterHostAndPort(masterInfos, meta);
            Assert.fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            Assert.assertEquals("MasterHost,MasterPort not in masterInfos", e.getMessage());
            exception = true;
        }
        Assert.assertTrue(exception);
    }

    /**
     * 测试checkInstId方法 - 验证sourceInstId与selfInstId不同时不抛出异常的情况
     */
    @Test
    public void testCheckInstId_DifferentInstId() {
        // Mock DynamicApplicationConfig.getString方法返回不同的实例ID
        mockConfig(ConfigKeys.INST_ID, "selfInstId");

        // 调用被测试的方法，应该不抛出异常
        FSMMetaManager.checkInstId("sourceInstId");

        // 如果没有抛出异常，测试通过
        Assert.assertTrue(true);
    }

    /**
     * 测试checkInstId方法 - 验证sourceInstId与selfInstId相同时抛出异常的情况
     */
    @Test
    public void testCheckInstId_SameInstId() {
        // Mock DynamicApplicationConfig.getString方法返回相同的实例ID
        mockConfig(ConfigKeys.INST_ID, "sameInstId");

        // 调用被测试的方法并验证异常
        try {
            FSMMetaManager.checkInstId("sameInstId");
            Assert.fail("Expected RuntimeException to be thrown");
        } catch (RuntimeException e) {
            Assert.assertEquals("should not use self as replica source", e.getMessage());
        }
    }

    /**
     * 测试checkInstId方法 - 验证sourceInstId为null时不抛出异常的情况
     */
    @Test
    public void testCheckInstId_NullSourceInstId() {
        // Mock DynamicApplicationConfig.getString方法返回一个有效的实例ID
        mockConfig(ConfigKeys.INST_ID, "selfInstId");

        // 调用被测试的方法，应该不抛出异常
        FSMMetaManager.checkInstId(null);

        // 如果没有抛出异常，测试通过
        Assert.assertTrue(true);
    }

    /**
     * 测试checkInstId方法 - 验证sourceInstId为空字符串时不抛出异常的情况
     */
    @Test
    public void testCheckInstId_EmptySourceInstId() {
        // Mock DynamicApplicationConfig.getString方法返回一个有效的实例ID
        mockConfig(ConfigKeys.INST_ID, "selfInstId");

        // 调用被测试的方法，应该不抛出异常
        FSMMetaManager.checkInstId("");

        // 如果没有抛出异常，测试通过
        Assert.assertTrue(true);
    }

    // ============= createStreamReplicaTasks 测试 =============

    /**
     * 通过反射获取 createStreamReplicaTasks 方法
     */
    private Method getCreateStreamReplicaTasksMethod() throws NoSuchMethodException {
        Method method = FSMMetaManager.class.getDeclaredMethod("createStreamReplicaTasks",
            RplService.class, ReplicaMeta.class, Connection.class, List.class);
        method.setAccessible(true);
        return method;
    }

    /**
     * 测试 createStreamReplicaTasks - 当 meta.position 为空时，应该使用 stream 的最新位点
     */
    @Test
    public void testCreateStreamReplicaTasks_WithoutMetaPosition() throws Exception {
        // 准备 RplService
        RplService rplService = new RplService();
        rplService.setId(TEST_SERVICE_ID);
        rplService.setStateMachineId(TEST_FSM_ID);
        rplService.setServiceType(ServiceType.REPLICA_INC.name());

        // 准备 ReplicaMeta，position 为空 → useMetaPosition = false
        ReplicaMeta meta = new ReplicaMeta();
        meta.setPosition(null);
        meta.setStreamGroup("testGroup");
        meta.setClusterId("cluster1");

        // 准备 masterInfos
        List<MutableTriple<String, Integer, String>> masterInfos = new ArrayList<>();
        masterInfos.add(new MutableTriple<>("host1", 3306, "inst1"));

        // 准备 streamPositions
        List<Pair<String, String>> streamPositions = Arrays.asList(
            Pair.of("stream_0", "binlog.000001:100"),
            Pair.of("stream_1", "binlog.000002:200")
        );

        Connection mockConnection = mock(Connection.class);
        RplTask mockRplTask = new RplTask();
        mockRplTask.setId(TEST_TASK_ID);

        try (MockedStatic<CommonUtil> mockedCommonUtil = mockStatic(CommonUtil.class);
            MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class);
            MockedStatic<FSMMetaManager> mockedFSM = mockStatic(FSMMetaManager.class, CALLS_REAL_METHODS)) {

            mockedCommonUtil.when(() -> CommonUtil.getStreamLatestPositions(mockConnection, "testGroup"))
                .thenReturn(streamPositions);
            mockedDb.when(() -> DbTaskMetaManager.addTaskWithMemory(
                    anyLong(), anyLong(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(mockRplTask);
            mockedFSM.when(() -> FSMMetaManager.computeIncTaskMemory(anyInt())).thenReturn(1024);
            mockedFSM.when(() -> FSMMetaManager.updateReplicaTaskConfig(any(), any(), anyBoolean())).then(inv -> null);

            // 反射调用 createStreamReplicaTasks
            Method method = getCreateStreamReplicaTasksMethod();
            method.invoke(null, rplService, meta, mockConnection, masterInfos);

            // 验证: position 应该被设置为最后一个 stream 的位点（循环中最后一次赋值）
            Assert.assertEquals("stream_1", meta.getStreamName());
            // 因为 useMetaPosition=false，所以 position 应该被更新
            Assert.assertEquals("binlog.000002:200", meta.getPosition());

            // 验证 addTaskWithMemory 被调用了两次（两个 stream）
            mockedDb.verify(() -> DbTaskMetaManager.addTaskWithMemory(
                anyLong(), anyLong(), any(), any(), any(), any(), anyInt(), any(), anyInt()), times(2));

            // 验证 updateReplicaTaskConfig 被调用了两次
            mockedFSM.verify(() -> FSMMetaManager.updateReplicaTaskConfig(
                any(RplTask.class), any(ReplicaMeta.class), eq(true)), times(2));
        }
    }

    /**
     * 测试 createStreamReplicaTasks - 当 meta.position 非空时，保留原位点不被 stream 覆盖
     */
    @Test
    public void testCreateStreamReplicaTasks_WithMetaPosition() throws Exception {
        // 准备 RplService
        RplService rplService = new RplService();
        rplService.setId(TEST_SERVICE_ID);
        rplService.setStateMachineId(TEST_FSM_ID);
        rplService.setServiceType(ServiceType.REPLICA_INC.name());

        // 准备 ReplicaMeta，position 非空 → useMetaPosition = true
        ReplicaMeta meta = new ReplicaMeta();
        meta.setPosition("original:999");
        meta.setStreamGroup("testGroup");
        meta.setClusterId("cluster1");

        List<MutableTriple<String, Integer, String>> masterInfos = new ArrayList<>();
        masterInfos.add(new MutableTriple<>("host1", 3306, "inst1"));
        masterInfos.add(new MutableTriple<>("host2", 3307, "inst2"));

        List<Pair<String, String>> streamPositions = Arrays.asList(
            Pair.of("stream_A", "binlog.000001:100"),
            Pair.of("stream_B", "binlog.000002:200"),
            Pair.of("stream_C", "binlog.000003:300")
        );

        Connection mockConnection = mock(Connection.class);
        RplTask mockRplTask = new RplTask();
        mockRplTask.setId(TEST_TASK_ID);

        try (MockedStatic<CommonUtil> mockedCommonUtil = mockStatic(CommonUtil.class);
            MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class);
            MockedStatic<FSMMetaManager> mockedFSM = mockStatic(FSMMetaManager.class, CALLS_REAL_METHODS)) {

            mockedCommonUtil.when(() -> CommonUtil.getStreamLatestPositions(mockConnection, "testGroup"))
                .thenReturn(streamPositions);
            mockedDb.when(() -> DbTaskMetaManager.addTaskWithMemory(
                    anyLong(), anyLong(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(mockRplTask);
            mockedFSM.when(() -> FSMMetaManager.computeIncTaskMemory(anyInt())).thenReturn(1024);
            mockedFSM.when(() -> FSMMetaManager.updateReplicaTaskConfig(any(), any(), anyBoolean())).then(inv -> null);

            Method method = getCreateStreamReplicaTasksMethod();
            method.invoke(null, rplService, meta, mockConnection, masterInfos);

            // 验证 streamName 被正确设置（最后一次循环是 stream_C）
            Assert.assertEquals("stream_C", meta.getStreamName());
            // useMetaPosition=true → position 保持原值
            Assert.assertEquals("original:999", meta.getPosition());

            // 验证 masterHost 轮询分配：3个stream，2个master → i%2 = 0,1,0
            // 最后一次循环 i=2, i%2=0 → host1
            Assert.assertEquals("host1", meta.getMasterHost());
            Assert.assertEquals(3306, meta.getMasterPort());

            // 验证 addTaskWithMemory 被调用了三次
            mockedDb.verify(() -> DbTaskMetaManager.addTaskWithMemory(
                anyLong(), anyLong(), any(), any(), any(), any(), anyInt(), any(), anyInt()), times(3));
        }
    }

    /**
     * 测试 createStreamReplicaTasks - masterInfos 轮询分配逻辑
     */
    @Test
    public void testCreateStreamReplicaTasks_MasterInfoRotation() throws Exception {
        RplService rplService = new RplService();
        rplService.setId(TEST_SERVICE_ID);
        rplService.setStateMachineId(TEST_FSM_ID);
        rplService.setServiceType(ServiceType.REPLICA_INC.name());

        ReplicaMeta meta = new ReplicaMeta();
        meta.setPosition("");
        meta.setStreamGroup("group1");
        meta.setClusterId("c1");

        // 1 个 master，3 个 stream → 全部分配到同一个 master
        List<MutableTriple<String, Integer, String>> masterInfos = new ArrayList<>();
        masterInfos.add(new MutableTriple<>("single_host", 9999, "inst_single"));

        List<Pair<String, String>> streamPositions = Arrays.asList(
            Pair.of("s0", "pos0"),
            Pair.of("s1", "pos1"),
            Pair.of("s2", "pos2")
        );

        Connection mockConnection = mock(Connection.class);
        RplTask mockRplTask = new RplTask();
        mockRplTask.setId(TEST_TASK_ID);

        try (MockedStatic<CommonUtil> mockedCommonUtil = mockStatic(CommonUtil.class);
            MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class);
            MockedStatic<FSMMetaManager> mockedFSM = mockStatic(FSMMetaManager.class, CALLS_REAL_METHODS)) {

            mockedCommonUtil.when(() -> CommonUtil.getStreamLatestPositions(mockConnection, "group1"))
                .thenReturn(streamPositions);
            mockedDb.when(() -> DbTaskMetaManager.addTaskWithMemory(
                    anyLong(), anyLong(), any(), any(), any(), any(), anyInt(), any(), anyInt()))
                .thenReturn(mockRplTask);
            mockedFSM.when(() -> FSMMetaManager.computeIncTaskMemory(anyInt())).thenReturn(512);
            mockedFSM.when(() -> FSMMetaManager.updateReplicaTaskConfig(any(), any(), anyBoolean())).then(inv -> null);

            Method method = getCreateStreamReplicaTasksMethod();
            method.invoke(null, rplService, meta, mockConnection, masterInfos);

            // 单 master → 所有 stream 都分配到 single_host
            Assert.assertEquals("single_host", meta.getMasterHost());
            Assert.assertEquals(9999, meta.getMasterPort());

            // position 为空字符串（isNotBlank=false） → 使用 stream position
            Assert.assertEquals("pos2", meta.getPosition());
            Assert.assertEquals("s2", meta.getStreamName());
        }
    }
}