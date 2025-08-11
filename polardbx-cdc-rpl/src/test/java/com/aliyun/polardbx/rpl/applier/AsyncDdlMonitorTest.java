/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.domain.po.RplDdl;
import com.aliyun.polardbx.binlog.domain.po.RplService;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.common.TaskContext;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.MockitoJUnitRunner;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class AsyncDdlMonitorTest extends BaseTest {

    @Mock
    private DbMetaCache dbMetaCache;

    @Mock
    private DataSource dataSource;

    @Mock
    private Connection connection;

    @Mock
    private Statement statement;

    @Mock
    private ResultSet resultSet;

    @Before
    public void setUp() throws SQLException {
        when(dbMetaCache.getBuiltInDefaultDataSource()).thenReturn(dataSource);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery(anyString())).thenReturn(resultSet);
        RplService rplService = new RplService();
        rplService.setId(1L);
        TaskContext.getInstance().setService(rplService);
    }

    @Test
    public void stopDbTasks_AllTasksStoppedSuccessfully() throws SQLException {
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = mockStatic(DbTaskMetaManager.class)) {
            AsyncDdlMonitor.getInstance().setDbMetaCache(dbMetaCache);
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.listTaskByService(anyLong()))
                .thenReturn(new ArrayList<>());
            RplDdl rplDdl1 = new RplDdl();
            rplDdl1.setId(1L);
            rplDdl1.setToken("token1");
            RplDdl rplDdl2 = new RplDdl();
            rplDdl2.setId(2L);
            rplDdl2.setToken("token2");

            AsyncDdlMonitor.getInstance().submitDbDdl(rplDdl1);
            AsyncDdlMonitor.getInstance().submitDbDdl(rplDdl2);

            when(resultSet.next()).thenReturn(true, true, false);
            when(resultSet.getInt("Id")).thenReturn(1, 2);
            when(resultSet.getString("Info")).thenReturn("info1", "info2");

            AsyncDdlMonitor.getInstance().stopDbTasks();

            verify(statement, times(2)).execute(anyString());
        }
    }

    @Test
    public void stopDbTasks_ExceptionDuringKillConnection() throws SQLException {
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = mockStatic(DbTaskMetaManager.class)) {
            AsyncDdlMonitor.getInstance().setDbMetaCache(dbMetaCache);
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.listTaskByService(anyLong()))
                .thenReturn(new ArrayList<>());
            RplDdl rplDdl = new RplDdl();
            rplDdl.setToken("token");

            RplDdl rplDdl1 = new RplDdl();
            rplDdl1.setId(1L);
            rplDdl1.setToken("token1");

            AsyncDdlMonitor.getInstance().submitDbDdl(rplDdl1);

            when(resultSet.next()).thenReturn(true, false);
            when(resultSet.getInt("Id")).thenReturn(1);
            when(resultSet.getString("Info")).thenReturn("info");
            doThrow(new SQLException("Test exception")).when(statement).execute(anyString());

            AsyncDdlMonitor.getInstance().stopDbTasks();

            verify(statement, times(1)).execute(anyString());
        }
    }
}