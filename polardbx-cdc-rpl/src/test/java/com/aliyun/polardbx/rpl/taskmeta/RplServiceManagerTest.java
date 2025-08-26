/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.ConfigPropMap;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.RplConstants;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

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
                "{\"privateMeta\":\"{\\\"masterHost\\\":\\\"127.0.0.1\\\",\\\"masterPort\\\":3306,\\\"masterUser\\\":\\\"user\\\",\\\"masterPassword\\\":\\\"password\\\",\\\"ignoreServerIds\\\":\\\"\\\",\\\"streamGroup\\\":\\\"group\\\"}\"}");

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
        params.put(RplConstants.COMPARE_ALL, "true");
        params.put(RplConstants.ENABLE_SRC_LOGICAL_META_SNAPSHOT, "true");
        params.put(RplConstants.INSERT_ON_UPDATE_MISS, "false");
        params.put(RplConstants.CONFLICT_STRATEGY, "OVERWRITE");
        params.put(RplConstants.MASTER_INST_ID, "testInstId");
        params.put(RplConstants.STREAM_GROUP, "testStreamGroup");
        params.put(RplConstants.ENABLE_DYNAMIC_MASTER_HOST, "true");
        params.put(RplConstants.WRITE_SERVER_ID, "100");

        ReplicaMeta replicaMeta = new ReplicaMeta();
        RplServiceManager.extractChangeMasterParams(params, replicaMeta);

        Assert.assertEquals("testChannel", replicaMeta.getChannel());
        Assert.assertTrue(replicaMeta.isImageMode());
        Assert.assertEquals("127.0.0.1", replicaMeta.getMasterHost());
        Assert.assertEquals(3306, replicaMeta.getMasterPort());
        Assert.assertEquals("testUser", replicaMeta.getMasterUser());
        Assert.assertEquals("testPassword", replicaMeta.getMasterPassword());
        Assert.assertEquals("mysql-bin.000001:12345", replicaMeta.getPosition());
        Assert.assertEquals("1,2", replicaMeta.getIgnoreServerIds());
        Assert.assertEquals(HostType.POLARX2, replicaMeta.getMasterType());
        Assert.assertEquals(ApplierType.MERGE, replicaMeta.getApplierType());
        Assert.assertTrue(replicaMeta.isCompareAll());
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
        RplServiceManager.extractChangeMasterParams(params, replicaMeta);

        Assert.assertNull(replicaMeta.getChannel());
        Assert.assertFalse(replicaMeta.isImageMode());
        Assert.assertNull(replicaMeta.getMasterHost());
        Assert.assertEquals(0, replicaMeta.getMasterPort());
        Assert.assertNull(replicaMeta.getMasterUser());
        Assert.assertNull(replicaMeta.getMasterPassword());
        Assert.assertNull(replicaMeta.getPosition());
        Assert.assertNull(replicaMeta.getIgnoreServerIds());
        Assert.assertEquals(HostType.MYSQL, replicaMeta.getMasterType());
        Assert.assertEquals(ApplierType.valueOf(defaultWriteType), replicaMeta.getApplierType());
        Assert.assertFalse(replicaMeta.isCompareAll());
        Assert.assertTrue(replicaMeta.isEnableSrcLogicalMetaSnapshot());
        Assert.assertTrue(replicaMeta.isInsertOnUpdateMiss());
        Assert.assertEquals(ConflictStrategy.OVERWRITE, replicaMeta.getConflictStrategy());
        Assert.assertNull(replicaMeta.getMasterInstId());
        Assert.assertNull(replicaMeta.getStreamGroup());
        Assert.assertFalse(replicaMeta.isEnableDynamicMasterHost());
        Assert.assertNull(replicaMeta.getServerId());
    }
}