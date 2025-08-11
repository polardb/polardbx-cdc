/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;


import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.TaskStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotEquals;

@RunWith(MockitoJUnitRunner.class)
public class HeartbeatManagerTest extends BaseTest {

    long taskId = 1L;

    @Before
    public void setUp() {
        HeartbeatManager.getInstance().init(taskId);
    }

    @Test(expected = RuntimeException.class)
    public void flushHeartbeat_TaskIsNull_LogsError() {
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(DbTaskMetaManager.class)) {
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(null);
            HeartbeatManager.getInstance().flushHeartbeat();
        }
    }

    @Test
    public void flushHeartbeat_TaskRunning_UpdatesGmtHeartBeat() {
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(DbTaskMetaManager.class)) {
            RplTask task = new RplTask();
            task.setStatus(TaskStatus.RUNNING.name());
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(task);
            HeartbeatManager.getInstance().flushHeartbeat();
        }
    }

    @Test(expected = RuntimeException.class)
    public void flushHeartbeat_TaskNotRunning_LogsError() {
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(DbTaskMetaManager.class)) {
            RplTask task = new RplTask();
            task.setStatus(TaskStatus.FINISHED.name());
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(task);
            HeartbeatManager.getInstance().flushHeartbeat();
        }
        HeartbeatManager.getInstance().flushHeartbeat();
    }

    @Test
    public void heartbeat_UpdatesLastEventTimestamp() throws InterruptedException {
        long initialTimestamp = HeartbeatManager.getInstance().getLastEventTimestamp();
        Thread.sleep(10);
        HeartbeatManager.getInstance().heartbeat();
        long updatedTimestamp = HeartbeatManager.getInstance().getLastEventTimestamp();
        assertNotEquals(initialTimestamp, updatedTimestamp);
    }

    @Test
    public void start_SchedulesFlushHeartbeat() {
        ScheduledExecutorService mockExecutorService = Mockito.mock(ScheduledExecutorService.class);
        HeartbeatManager.getInstance().setExecutorService(mockExecutorService);
        HeartbeatManager.getInstance().start();
        Mockito.verify(HeartbeatManager.getInstance().getExecutorService()).scheduleAtFixedRate(
            Mockito.any(Runnable.class),
            Mockito.eq(0L),
            Mockito.eq(5L),
            Mockito.eq(TimeUnit.SECONDS)
        );
    }
}
