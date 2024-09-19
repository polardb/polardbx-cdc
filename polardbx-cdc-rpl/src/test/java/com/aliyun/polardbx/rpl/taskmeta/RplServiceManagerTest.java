/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplStateMachine;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.rpl.common.CommonUtil;
import com.aliyun.polardbx.rpl.common.fsmutil.FSMState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.Mockito;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class RplServiceManagerTest {

    @Mock
    private RplTask rplTask;

    @Mock
    private RplStateMachine rplStateMachine;

    @Mock
    private RplTaskConfig rplTaskConfig;

    private List<RplTask> tasks;
    private List<LinkedHashMap<String, String>> responses;

    @Before
    public void setUp() {
        tasks = new ArrayList<>();
        responses = new ArrayList<>();
        tasks.add(rplTask);
    }

    @Test
    public void testExtractStatusFromTask() {
        try (MockedStatic<CommonUtil> mockedStaticCommonUtil = mockStatic(CommonUtil.class);
            MockedStatic<DbTaskMetaManager> mockedStaticDbTaskMetaManager = mockStatic(DbTaskMetaManager.class);
            MockedStatic<FSMMetaManager> mockedStaticFSMMetaManager = mockStatic(FSMMetaManager.class)) {

            // Mock static methods
            mockedStaticCommonUtil.when(CommonUtil::getRplInitialPosition).thenReturn("0:4#0.0");
            mockedStaticDbTaskMetaManager.when(() -> DbTaskMetaManager.getTaskConfig(anyLong())).thenReturn(rplTaskConfig);
            mockedStaticFSMMetaManager.when(() -> FSMMetaManager.computeTaskDelay(any(RplTask.class))).thenReturn(10L);

            // Mock object behaviors
            when(rplTask.getId()).thenReturn(1L);
            when(rplTask.getPosition()).thenReturn("mysql.1:12345#123456789");
            when(rplTask.getStatus()).thenReturn("RUNNING");
            when(rplTask.getLastError()).thenReturn(null);
            when(rplStateMachine.getChannel()).thenReturn("channel");
            when(rplStateMachine.getState()).thenReturn("RUNNING");

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
            assertEquals("INCREMENTAL_MODE", response.get("Replicate_Mode"));
            assertEquals("RUNNING", response.get("Running_Stage"));
            assertEquals("channel", response.get("Channel_Name"));
            assertEquals("1", response.get("Sub_Channel_Name"));
        }
    }
}