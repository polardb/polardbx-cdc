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
package com.aliyun.polardbx.rpl.validation;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.validation.fullvalid.task.ReplicaFullValidCheckTask;
import com.mysql.jdbc.StatementImpl;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

public class ReplicaFullValidCheckTaskTest extends BaseTest {

    @Test
    public void testValidSyncPointTso_null() throws Exception {
        ReplicaFullValidCheckTask task = mock(ReplicaFullValidCheckTask.class);
        Whitebox.setInternalState(task, "syncPointTso", (Pair<String, String>) null);
        boolean result = Whitebox.invokeMethod(task, "validSyncPointTso");
        assertFalse(result);
    }

    @Test
    public void testValidSyncPointTso_empty() throws Exception {
        ReplicaFullValidCheckTask task = mock(ReplicaFullValidCheckTask.class);
        Whitebox.setInternalState(task, "syncPointTso", Pair.of("", ""));
        boolean result = Whitebox.invokeMethod(task, "validSyncPointTso");
        assertFalse(result);
    }

    @Test
    @SneakyThrows
    public void testValidSyncPointTsoHelper_select1ThrowException() {
        ReplicaFullValidCheckTask task = mock(ReplicaFullValidCheckTask.class);
        DataSource datasource = mock(DataSource.class);
        try (Connection conn = mock(Connection.class);
            StatementImpl stmt = mock(StatementImpl.class)) {
            when(datasource.getConnection()).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);

            boolean result = Whitebox.invokeMethod(task, "validSyncPointTsoHelper", datasource, "table1", "tso1");
            assertTrue(result);

            verify(stmt, times(1)).execute("SET SNAPSHOT_TS = tso1");
            verify(stmt, times(1)).execute("SET TRANSACTION_POLICY = TSO");
            verify(stmt, times(1)).execute("BEGIN");
            verify(stmt, times(1)).execute("/*+TDDL:scan()*/ SELECT 1 FROM table1 LIMIT 1");
            verify(stmt, times(1)).execute("ROLLBACK ");
            verify(stmt, times(1)).execute("SET SNAPSHOT_TS = -1");
        }
    }

    @Test
    @SneakyThrows
    public void testValidSyncPointTso_allWell() {
        ReplicaFullValidCheckTask task = mock(ReplicaFullValidCheckTask.class);
        DataSource datasource = mock(DataSource.class);
        try (Connection conn = mock(Connection.class);
            StatementImpl stmt = mock(StatementImpl.class)) {
            when(datasource.getConnection()).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);
            doThrow(SQLException.class).when(stmt).execute("/*+TDDL:scan()*/ SELECT 1 FROM table1 LIMIT 1");

            boolean result = Whitebox.invokeMethod(task, "validSyncPointTsoHelper", datasource, "table1", "tso1");
            assertFalse(result);

            verify(stmt, times(1)).execute("SET SNAPSHOT_TS = tso1");
            verify(stmt, times(1)).execute("SET TRANSACTION_POLICY = TSO");
            verify(stmt, times(1)).execute("BEGIN");
            verify(stmt, times(1)).execute("/*+TDDL:scan()*/ SELECT 1 FROM table1 LIMIT 1");
            verify(stmt, times(1)).execute("ROLLBACK ");
            verify(stmt, times(1)).execute("SET SNAPSHOT_TS = -1");
        }
    }
}
