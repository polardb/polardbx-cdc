/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.ConfigPropMap;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.ReplicaMode;
import com.aliyun.polardbx.rpl.common.RplConstants;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RplServiceManagerTest extends BaseTest {

    @Mock
    private RplTask rplTask;

    @Mock
    private RplStateMachine rplStateMachine;

    @Mock
    private RplTaskConfig rplTaskConfig;

    private List<RplTask> tasks;
    private List<LinkedHashMap<String, String>> responses;

    private JdbcTemplate polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);
    private MetaManagerTranProxy metaManagerTranProxy = Mockito.mock(MetaManagerTranProxy.class);

    @Before
    public void setUp() throws Exception {
        registerSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
        registerSpringObject("metaManagerTranProxy", metaManagerTranProxy);
    }

    @After
    public void after() {
        unregisterSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
        unregisterSpringObject("metaManagerTranProxy", metaManagerTranProxy);
    }

    @Test
    public void testExtractStatusFromTask() throws NoSuchFieldException, IllegalAccessException {
        tasks = new ArrayList<>();
        responses = new ArrayList<>();
        tasks.add(rplTask);

        try (MockedStatic<CommonUtil> mockedStaticCommonUtil = mockStatic(CommonUtil.class);
            MockedStatic<DbTaskMetaManager> mockedStaticDbTaskMetaManager = mockStatic(DbTaskMetaManager.class);
            MockedStatic<FSMMetaManager> mockedStaticFSMMetaManager = mockStatic(FSMMetaManager.class)) {

            // Mock static methods
            mockedStaticCommonUtil.when(CommonUtil::getRplInitialPosition).thenReturn("0:4#0.0");
            mockedStaticDbTaskMetaManager.when(() -> DbTaskMetaManager.getTaskConfig(anyLong()))
                .thenReturn(rplTaskConfig);
            mockedStaticFSMMetaManager.when(() -> FSMMetaManager.computeTaskDelay(any(RplTask.class))).thenReturn(10L);

            // Mock object behaviors
            when(rplTask.getId()).thenReturn(1L);
            // filename:position#masterid.timestamp.T().rtso()
            when(rplTask.getPosition()).thenReturn("mysql.1:12345#1234.12345.T(1).rtso(123456789)");
            when(rplTask.getStatus()).thenReturn("RUNNING");
            when(rplTask.getLastError()).thenReturn(null);
            when(rplStateMachine.getChannel()).thenReturn("channel");
            when(rplStateMachine.getState()).thenReturn("REPLICA_INC");

            when(rplTaskConfig.getExtractorConfig()).thenReturn(
                "{\"privateMeta\":\"{\\\"mode\\\":\\\"INCREMENTAL\\\",\\\"masterHost\\\":\\\"127.0.0.1\\\",\\\"masterPort\\\":3306,\\\"masterUser\\\":\\\"user\\\",\\\"masterPassword\\\":\\\"password\\\",\\\"ignoreServerIds\\\":\\\"\\\",\\\"streamGroup\\\":\\\"group\\\"}\"}");

            // Execute method
            RplServiceManager.extractStatusFromTask(tasks, rplStateMachine, responses);

            // Verify results
            assertEquals(1, responses.size());
            Map<String, String> response = responses.get(0);
            assertEquals("127.0.0.1", response.get("Master_Host"));
            assertEquals("user", response.get("Master_User"));
            assertEquals("3306", response.get("Master_Port"));
            assertEquals("mysql.1", response.get("Master_Log_File"));
            assertEquals("12345", response.get("Read_Master_Log_Pos"));
            assertEquals("mysql.1", response.get("Relay_Log_File"));
            assertEquals("12345", response.get("Relay_Log_Pos"));
            assertEquals("mysql.1", response.get("Relay_Master_Log_File"));
            assertEquals("Yes", response.get("Slave_IO_Running"));
            assertEquals("Yes", response.get("Slave_SQL_Running"));
            assertEquals("", response.get("Replicate_Do_DB"));
            assertEquals("", response.get("Replicate_Ignore_DB"));
            assertEquals("", response.get("Replicate_Do_Table"));
            assertEquals("", response.get("Replicate_Ignore_Table"));
            assertEquals("", response.get("Replicate_Wild_Do_Table"));
            assertEquals("", response.get("Replicate_Wild_Ignore_Table"));
            assertEquals("", response.get("Last_Error"));
            assertEquals("12345", response.get("Exec_Master_Log_Pos"));
            assertEquals("123456789", response.get("Exec_Master_Log_Tso"));
            assertEquals("None", response.get("Until_Condition"));
            assertEquals("No", response.get("Master_SSL_Allowed"));
            assertEquals("10", response.get("Seconds_Behind_Master"));
            assertEquals("No", response.get("Master_SSL_Verify_Server_Cert"));
            assertEquals("", response.get("Replicate_Ignore_Server_Ids"));
            assertEquals("NULL", response.get("SQL_Remaining_Delay"));
            assertEquals("Yes", response.get("Slave_SQL_Running_State"));
            assertEquals("0", response.get("Auto_Position"));
            assertEquals("", response.get("Replicate_Rewrite_DB"));
            assertEquals("INCREMENTAL", response.get("Replicate_Mode"));
            assertEquals("REPLICA_INC", response.get("Running_Stage"));
            assertEquals("channel", response.get("Channel_Name"));
            assertEquals("1", response.get("Sub_Channel_Name"));
            assertEquals("INCREMENTAL", response.get("Replicate_Mode"));
        }
    }

    @Test
    public void extractChangeMasterParams_AllParamsSet_CorrectlySetsReplicaMeta()
        throws NoSuchFieldException, IllegalAccessException {

        Map<String, String> params = new HashMap<>();
        params.put(RplConstants.CHANNEL, "testChannel");
        params.put(RplConstants.MODE, RplConstants.IMAGE_MODE);
        params.put(RplConstants.MASTER_HOST, "127.0.0.1");
        params.put(RplConstants.MASTER_PORT, "3306");
        params.put(RplConstants.MASTER_USER, "testUser");
        params.put(RplConstants.MASTER_PASSWORD, "testPassword");
        params.put(RplConstants.MASTER_LOG_FILE, "mysql-bin.000001");
        params.put(RplConstants.MASTER_LOG_POS, "12345");
        params.put(RplConstants.IGNORE_SERVER_IDS, "(1,2)");
        params.put(RplConstants.SOURCE_HOST_TYPE, RplConstants.POLARDBX);
        params.put(RplConstants.WRITE_TYPE, "MERGE");
        params.put(RplConstants.ENABLE_SRC_LOGICAL_META_SNAPSHOT, "true");
        params.put(RplConstants.INSERT_ON_UPDATE_MISS, "false");
        params.put(RplConstants.CONFLICT_STRATEGY, "OVERWRITE");
        params.put(RplConstants.MASTER_INST_ID, "testInstId");
        params.put(RplConstants.STREAM_GROUP, "testStreamGroup");
        params.put(RplConstants.ENABLE_DYNAMIC_MASTER_HOST, "true");
        params.put(RplConstants.WRITE_SERVER_ID, "100");

        ReplicaMeta replicaMeta = new ReplicaMeta();

        // 探活查询已改为DB侧时间比较，不再把JVM侧时间当做参数传入，因此无需mock GmsTimeUtil
        RplServiceManager.extractChangeMasterParams(params, replicaMeta);

        Assert.assertEquals("testChannel", replicaMeta.getChannel());
        Assert.assertSame(ReplicaMode.IMAGE, replicaMeta.getMode());
        Assert.assertEquals("127.0.0.1", replicaMeta.getMasterHost());
        Assert.assertEquals(3306, replicaMeta.getMasterPort());
        Assert.assertEquals("testUser", replicaMeta.getMasterUser());
        Assert.assertEquals("testPassword", replicaMeta.getMasterPassword());
        Assert.assertEquals("mysql-bin.000001:12345", replicaMeta.getPosition());
        Assert.assertEquals("1,2", replicaMeta.getIgnoreServerIds());
        Assert.assertEquals(HostType.POLARX2, replicaMeta.getMasterType());
        Assert.assertEquals(ApplierType.MERGE, replicaMeta.getApplierType());
        Assert.assertTrue(replicaMeta.isEnableSrcLogicalMetaSnapshot());
        Assert.assertFalse(replicaMeta.isInsertOnUpdateMiss());
        Assert.assertEquals(ConflictStrategy.OVERWRITE, replicaMeta.getConflictStrategy());
        Assert.assertEquals("testInstId", replicaMeta.getMasterInstId());
        Assert.assertEquals("testStreamGroup", replicaMeta.getStreamGroup());
        Assert.assertTrue(replicaMeta.isEnableDynamicMasterHost());
        Assert.assertEquals("100", replicaMeta.getServerId());
    }

    @Test
    public void extractChangeMasterParams_NoParamsSet_DefaultValues()
        throws NoSuchFieldException, IllegalAccessException {

        Field field = ConfigPropMap.class.getDeclaredField("CONFIG_MAP");
        field.setAccessible(true);
        Map<String, String> CONFIG_MAP = (Map<String, String>) field.get(null);
        String defaultWriteType = CONFIG_MAP.get(ConfigKeys.RPL_DEFAULT_WRITE_TYPE);

        Map<String, String> params = new HashMap<>();

        ReplicaMeta replicaMeta = new ReplicaMeta();
        // 探活查询已改为DB侧时间比较，不再把JVM侧时间当做参数传入，因此无需mock GmsTimeUtil
        RplServiceManager.extractChangeMasterParams(params, replicaMeta);

        Assert.assertNull(replicaMeta.getChannel());
        Assert.assertNotSame(replicaMeta.getMode(), ReplicaMode.IMAGE);
        Assert.assertNull(replicaMeta.getMasterHost());
        Assert.assertEquals(0, replicaMeta.getMasterPort());
        Assert.assertNull(replicaMeta.getMasterUser());
        Assert.assertNull(replicaMeta.getMasterPassword());
        Assert.assertNull(replicaMeta.getPosition());
        Assert.assertNull(replicaMeta.getIgnoreServerIds());
        Assert.assertEquals(HostType.MYSQL, replicaMeta.getMasterType());
        Assert.assertEquals(ApplierType.valueOf(defaultWriteType), replicaMeta.getApplierType());
        Assert.assertTrue(replicaMeta.isEnableSrcLogicalMetaSnapshot());
        Assert.assertTrue(replicaMeta.isInsertOnUpdateMiss());
        Assert.assertEquals(ConflictStrategy.OVERWRITE, replicaMeta.getConflictStrategy());
        Assert.assertNull(replicaMeta.getMasterInstId());
        Assert.assertNull(replicaMeta.getStreamGroup());
        Assert.assertFalse(replicaMeta.isEnableDynamicMasterHost());
        Assert.assertNull(replicaMeta.getServerId());
    }

    /**
     * change master 内部失败时必须把异常抛出事务边界，
     * 不能吞掉后仅返回失败码，否则真实原因会被提交阶段的 rollback-only 异常覆盖
     */
    @Test
    public void changeMasterWithTran_InnerFailure_ThrowExceptionWithRealCause() {
        Map<String, String> params = new HashMap<>();
        params.put(RplConstants.CHANNEL, "testChannel");

        RplStateMachine stateMachine = new RplStateMachine();
        stateMachine.setId(1L);
        stateMachine.setStatus(StateMachineStatus.STOPPED.name());

        try (MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class)) {
            mockedDb.when(() -> DbTaskMetaManager.getRplStateMachine("testChannel")).thenReturn(stateMachine);
            mockedDb.when(() -> DbTaskMetaManager.listTaskByStateMachine(1L))
                .thenThrow(new IllegalArgumentException("bound must be positive"));

            try {
                RplServiceManager.changeMasterWithTran(params);
                Assert.fail("should throw exception so that the real cause can be returned to client");
            } catch (PolardbxException e) {
                Assert.assertTrue(e.getMessage(), e.getMessage().contains("bound must be positive"));
            }
        }
    }

    // ============= generateLatestPosition 测试 =============

    /**
     * 构建 RplTaskConfig，使 extractMetaFromTaskConfig 能正确反序列化出带有指定 streamName 的 ReplicaMeta
     */
    private RplTaskConfig buildTaskConfigWithStreamName(String streamName) {
        ReplicaMeta meta = new ReplicaMeta();
        meta.setStreamName(streamName);

        ExtractorConfig ec = new ExtractorConfig();
        ec.setPrivateMeta(JSON.toJSONString(meta));

        RplTaskConfig tc = new RplTaskConfig();
        tc.setExtractorConfig(JSON.toJSONString(ec));
        return tc;
    }

    /**
     * 测试 generateLatestPosition - 有 streamGroup 时，按 streamName 匹配更新位点
     */
    @Test
    public void testGenerateLatestPosition_WithStreamGroup() throws Exception {
        ReplicaMeta configMeta = new ReplicaMeta();
        configMeta.setMasterHost("127.0.0.1");
        configMeta.setMasterPort(3306);
        configMeta.setMasterUser("user");
        configMeta.setMasterPassword("pass");
        configMeta.setStreamGroup("myGroup");

        RplStateMachine stateMachine = new RplStateMachine();
        stateMachine.setId(1L);
        stateMachine.setConfig(JSON.toJSONString(configMeta));

        RplService service = new RplService();
        service.setId(10L);

        RplTask task1 = new RplTask();
        task1.setId(100L);
        RplTask task2 = new RplTask();
        task2.setId(101L);
        List<RplTask> tasks = Arrays.asList(task1, task2);

        List<Pair<String, String>> streamPositions = Arrays.asList(
            Pair.of("stream_0", "binlog.000001:500"),
            Pair.of("stream_1", "binlog.000002:600")
        );

        Connection mockConnection = Mockito.mock(Connection.class);

        try (MockedStatic<DriverManager> mockedDriver = mockStatic(DriverManager.class);
            MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class);
            MockedStatic<CommonUtil> mockedCommonUtil = mockStatic(CommonUtil.class);
            MockedStatic<FSMMetaManager> mockedFSM = mockStatic(FSMMetaManager.class)) {

            mockedDriver.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                .thenReturn(mockConnection);
            mockedDb.when(() -> DbTaskMetaManager.getService(1L, ServiceType.REPLICA_INC))
                .thenReturn(service);
            mockedDb.when(() -> DbTaskMetaManager.listTaskByService(10L))
                .thenReturn(tasks);
            // 让 extractMetaFromTaskConfig 内部调用正常工作
            mockedDb.when(() -> DbTaskMetaManager.getTaskConfig(100L))
                .thenReturn(buildTaskConfigWithStreamName("stream_0"));
            mockedDb.when(() -> DbTaskMetaManager.getTaskConfig(101L))
                .thenReturn(buildTaskConfigWithStreamName("stream_1"));
            mockedCommonUtil.when(() -> CommonUtil.getStreamLatestPositions(mockConnection, "myGroup"))
                .thenReturn(streamPositions);

            RplServiceManager.generateLatestPosition(stateMachine);

            // 验证 updateReplicaTaskConfig 被调用了 2 次，position 正确
            mockedFSM.verify(() -> FSMMetaManager.updateReplicaTaskConfig(
                eq(task1),
                argThat(m -> "binlog.000001:500".equals(((ReplicaMeta) m).getPosition())
                    && "stream_0".equals(((ReplicaMeta) m).getStreamName())),
                eq(true)), times(1));
            mockedFSM.verify(() -> FSMMetaManager.updateReplicaTaskConfig(
                eq(task2),
                argThat(m -> "binlog.000002:600".equals(((ReplicaMeta) m).getPosition())
                    && "stream_1".equals(((ReplicaMeta) m).getStreamName())),
                eq(true)), times(1));
        }
    }

    /**
     * 测试 generateLatestPosition - 无 streamGroup 时，使用第一个 task 的 binary position
     */
    @Test
    public void testGenerateLatestPosition_WithoutStreamGroup() throws Exception {
        ReplicaMeta configMeta = new ReplicaMeta();
        configMeta.setMasterHost("127.0.0.1");
        configMeta.setMasterPort(3306);
        configMeta.setMasterUser("user");
        configMeta.setMasterPassword("pass");
        configMeta.setStreamGroup(null);

        RplStateMachine stateMachine = new RplStateMachine();
        stateMachine.setId(2L);
        stateMachine.setConfig(JSON.toJSONString(configMeta));

        RplService service = new RplService();
        service.setId(20L);

        RplTask task1 = new RplTask();
        task1.setId(200L);
        List<RplTask> tasks = Arrays.asList(task1);

        Connection mockConnection = Mockito.mock(Connection.class);

        try (MockedStatic<DriverManager> mockedDriver = mockStatic(DriverManager.class);
            MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class);
            MockedStatic<CommonUtil> mockedCommonUtil = mockStatic(CommonUtil.class);
            MockedStatic<FSMMetaManager> mockedFSM = mockStatic(FSMMetaManager.class)) {

            mockedDriver.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                .thenReturn(mockConnection);
            mockedDb.when(() -> DbTaskMetaManager.getService(2L, ServiceType.REPLICA_INC))
                .thenReturn(service);
            mockedDb.when(() -> DbTaskMetaManager.listTaskByService(20L))
                .thenReturn(tasks);
            mockedDb.when(() -> DbTaskMetaManager.getTaskConfig(200L))
                .thenReturn(buildTaskConfigWithStreamName(null));
            mockedCommonUtil.when(() -> CommonUtil.getBinaryLatestPosition(mockConnection))
                .thenReturn("mysql-bin.000099:7890");

            RplServiceManager.generateLatestPosition(stateMachine);

            // 验证 updateReplicaTaskConfig 被调用了 1 次，position 正确
            mockedFSM.verify(() -> FSMMetaManager.updateReplicaTaskConfig(
                eq(task1),
                argThat(m -> "mysql-bin.000099:7890".equals(((ReplicaMeta) m).getPosition())),
                eq(true)), times(1));
        }
    }

    /**
     * 测试 generateLatestPosition - streamGroup 非空但 streamName 不匹配
     */
    @Test
    public void testGenerateLatestPosition_WithStreamGroup_NoMatch() throws Exception {
        ReplicaMeta configMeta = new ReplicaMeta();
        configMeta.setMasterHost("127.0.0.1");
        configMeta.setMasterPort(3306);
        configMeta.setMasterUser("user");
        configMeta.setMasterPassword("pass");
        configMeta.setStreamGroup("groupX");

        RplStateMachine stateMachine = new RplStateMachine();
        stateMachine.setId(3L);
        stateMachine.setConfig(JSON.toJSONString(configMeta));

        RplService service = new RplService();
        service.setId(30L);

        RplTask task1 = new RplTask();
        task1.setId(300L);
        List<RplTask> tasks = Arrays.asList(task1);

        List<Pair<String, String>> streamPositions = Arrays.asList(
            Pair.of("stream_X", "binlog.000001:100")
        );

        Connection mockConnection = Mockito.mock(Connection.class);

        try (MockedStatic<DriverManager> mockedDriver = mockStatic(DriverManager.class);
            MockedStatic<DbTaskMetaManager> mockedDb = mockStatic(DbTaskMetaManager.class);
            MockedStatic<CommonUtil> mockedCommonUtil = mockStatic(CommonUtil.class);
            MockedStatic<FSMMetaManager> mockedFSM = mockStatic(FSMMetaManager.class)) {

            mockedDriver.when(() -> DriverManager.getConnection(anyString(), anyString(), anyString()))
                .thenReturn(mockConnection);
            mockedDb.when(() -> DbTaskMetaManager.getService(3L, ServiceType.REPLICA_INC))
                .thenReturn(service);
            mockedDb.when(() -> DbTaskMetaManager.listTaskByService(30L))
                .thenReturn(tasks);
            mockedDb.when(() -> DbTaskMetaManager.getTaskConfig(300L))
                .thenReturn(buildTaskConfigWithStreamName("unmatched_stream"));
            mockedCommonUtil.when(() -> CommonUtil.getStreamLatestPositions(mockConnection, "groupX"))
                .thenReturn(streamPositions);

            RplServiceManager.generateLatestPosition(stateMachine);

            // streamName 不匹配 → position 不应被更新（仍为 null）
            mockedFSM.verify(() -> FSMMetaManager.updateReplicaTaskConfig(
                eq(task1),
                argThat(m -> ((ReplicaMeta) m).getPosition() == null
                    && "unmatched_stream".equals(((ReplicaMeta) m).getStreamName())),
                eq(true)), times(1));
        }
    }
}
