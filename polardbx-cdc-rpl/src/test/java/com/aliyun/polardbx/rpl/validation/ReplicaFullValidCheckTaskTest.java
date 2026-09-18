/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.validation.fullvalid.task.ReplicaFullValidCheckTask;
import lombok.SneakyThrows;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.mockito.Mockito;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ReplicaFullValidCheckTaskTest extends BaseTest {

    @Test
    public void testValidSyncPointTso_null() throws Exception {
        ReplicaFullValidCheckTask task = mock(ReplicaFullValidCheckTask.class);
        task.setSyncPointTso(null);
        boolean result = task.validSyncPointTso();
        assertFalse(result);
    }

    @Test
    public void testValidSyncPointTso_empty() throws Exception {
        ReplicaFullValidCheckTask task = mock(ReplicaFullValidCheckTask.class);
        task.setSyncPointTso(Pair.of("", ""));
        boolean result = task.validSyncPointTso();
        assertFalse(result);
    }

    @Test
    @SneakyThrows
    public void testValidSyncPointTsoHelper_select1ThrowException() {
        ReplicaFullValidCheckTask task = mock(ReplicaFullValidCheckTask.class, Mockito.CALLS_REAL_METHODS);
        DataSource datasource = mock(DataSource.class);
        try (Connection conn = mock(Connection.class);
            Statement stmt = mock(Statement.class)) {
            when(datasource.getConnection()).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);

            boolean result = task.validSyncPointTsoHelper(datasource, ordinaryTableInfo(), "tso1");
            assertTrue(result);

            verify(stmt, times(1)).execute("SET SNAPSHOT_TS = tso1");
            verify(stmt, times(1)).execute("SET TRANSACTION_POLICY = TSO");
            verify(stmt, times(1)).execute("BEGIN");
            verify(stmt, times(1)).execute("/*+TDDL:scan()*/ SELECT 1 FROM `table1` LIMIT 1");
            verify(stmt, times(1)).execute("ROLLBACK ");
            verify(stmt, times(1)).execute("SET SNAPSHOT_TS = -1");
        }
    }

    @Test
    @SneakyThrows
    public void testValidSyncPointTso_allWell() {
        ReplicaFullValidCheckTask task = mock(ReplicaFullValidCheckTask.class, Mockito.CALLS_REAL_METHODS);
        DataSource datasource = mock(DataSource.class);
        try (Connection conn = mock(Connection.class);
            Statement stmt = mock(Statement.class)) {
            when(datasource.getConnection()).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);
            doThrow(SQLException.class).when(stmt).execute("/*+TDDL:scan()*/ SELECT 1 FROM `table1` LIMIT 1");

            boolean result = task.validSyncPointTsoHelper(datasource, ordinaryTableInfo(), "tso1");
            assertFalse(result);

            verify(stmt, times(1)).execute("SET SNAPSHOT_TS = tso1");
            verify(stmt, times(1)).execute("SET TRANSACTION_POLICY = TSO");
            verify(stmt, times(1)).execute("BEGIN");
            verify(stmt, times(1)).execute("/*+TDDL:scan()*/ SELECT 1 FROM `table1` LIMIT 1");
            verify(stmt, times(1)).execute("ROLLBACK ");
            verify(stmt, times(1)).execute("SET SNAPSHOT_TS = -1");
        }
    }

    @Test
    @SneakyThrows
    public void testValidSyncPointTsoHelper_externalizedTableUsesLogicalPrimaryKeyProbe() {
        ReplicaFullValidCheckTask task = mock(ReplicaFullValidCheckTask.class, Mockito.CALLS_REAL_METHODS);
        DataSource datasource = mock(DataSource.class);
        try (Connection conn = mock(Connection.class);
            Statement stmt = mock(Statement.class)) {
            when(datasource.getConnection()).thenReturn(conn);
            when(conn.createStatement()).thenReturn(stmt);

            TableInfo tableInfo = ordinaryTableInfo();
            tableInfo.setExternalizedColumnNames(Collections.singleton("body"));
            boolean result = task.validSyncPointTsoHelper(datasource, tableInfo, "tso1");
            assertTrue(result);

            verify(stmt, times(1)).execute("SELECT MIN(`id`) FROM `table1`");
            verify(stmt, times(0)).execute("/*+TDDL:scan()*/ SELECT 1 FROM `table1` LIMIT 1");
        }
    }

    private static TableInfo ordinaryTableInfo() {
        TableInfo tableInfo = new TableInfo("db1", "table1");
        tableInfo.getPks().add("id");
        return tableInfo;
    }
}
