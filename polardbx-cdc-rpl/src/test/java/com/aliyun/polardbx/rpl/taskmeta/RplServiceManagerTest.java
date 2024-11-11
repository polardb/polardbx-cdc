/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.aliyun.polardbx.rpl.common.CommonUtil;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RplServiceManagerTest extends BaseTestWithGmsTables {

    @Mock
    private RplTask rplTask;

    @Mock
    private RplStateMachine rplStateMachine;

    @Mock
    private RplTaskConfig rplTaskConfig;

    private List<RplTask> tasks;
    private List<LinkedHashMap<String, String>> responses;


    @Test
    public void testExtractStatusFromTask() throws NoSuchFieldException, IllegalAccessException {
        tasks = new ArrayList<>();
        responses = new ArrayList<>();
        tasks.add(rplTask);
        JdbcTemplate polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);
        MetaManagerTranProxy metaManagerTranProxy = Mockito.mock(MetaManagerTranProxy.class);

        Field field = SpringContextHolder.class.getDeclaredField("applicationContext");
        field.setAccessible(true);
        ApplicationContext applicationContext = (ApplicationContext) field.get(null);
        DefaultListableBeanFactory listableBeanFactory =
            (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
        listableBeanFactory.destroySingleton("polarxJdbcTemplate");
        listableBeanFactory.registerSingleton("polarxJdbcTemplate", polarxJdbcTemplate);


        listableBeanFactory.destroySingleton("metaManagerTranProxy");
        listableBeanFactory.registerSingleton("metaManagerTranProxy", metaManagerTranProxy);


        try (MockedStatic<CommonUtil> mockedStaticCommonUtil = mockStatic(CommonUtil.class);
            MockedStatic<DbTaskMetaManager> mockedStaticDbTaskMetaManager = mockStatic(DbTaskMetaManager.class);
            MockedStatic<FSMMetaManager> mockedStaticFSMMetaManager = mockStatic(FSMMetaManager.class)) {

            // Mock static methods
            mockedStaticCommonUtil.when(CommonUtil::getRplInitialPosition).thenReturn("0:4#0.0");
            mockedStaticDbTaskMetaManager.when(() -> DbTaskMetaManager.getTaskConfig(anyLong())).thenReturn(rplTaskConfig);
            mockedStaticFSMMetaManager.when(() -> FSMMetaManager.computeTaskDelay(any(RplTask.class))).thenReturn(10L);

            // Mock object behaviors
            when(rplTask.getId()).thenReturn(1L);
            // filename:position#masterid.timestamp.T().rtso()
            when(rplTask.getPosition()).thenReturn("mysql.1:12345#1234.12345.T(1).rtso(123456789)");
            when(rplTask.getStatus()).thenReturn("RUNNING");
            when(rplTask.getLastError()).thenReturn(null);
            when(rplStateMachine.getChannel()).thenReturn("channel");
            when(rplStateMachine.getState()).thenReturn("REPLICA_INC");

            when(rplTaskConfig.getExtractorConfig()).thenReturn("{\"privateMeta\":\"{\\\"masterHost\\\":\\\"127.0.0.1\\\",\\\"masterPort\\\":3306,\\\"masterUser\\\":\\\"user\\\",\\\"masterPassword\\\":\\\"password\\\",\\\"ignoreServerIds\\\":\\\"\\\",\\\"streamGroup\\\":\\\"group\\\"}\"}");

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
}