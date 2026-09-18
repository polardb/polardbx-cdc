/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.TaskStatus;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertNotEquals;

@RunWith(MockitoJUnitRunner.class)
public class HeartbeatManagerTest {

    long taskId = 1L;

    @BeforeClass
    public static void initializeDbTaskMetaManagerWithoutSpringContext() throws ClassNotFoundException {
        try (MockedStatic<SpringContextHolder> ignored = Mockito.mockStatic(SpringContextHolder.class)) {
            Class.forName(DbTaskMetaManager.class.getName(), true, DbTaskMetaManager.class.getClassLoader());
        }
    }

    @Before
    public void setUp() {
        HeartbeatManager.getInstance().init(taskId);
    }

    @Test
    public void flushHeartbeat_TaskIsNull_ExitsProcess() {
        try (
            MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(DbTaskMetaManager.class);
            MockedStatic<Runtime> runtimeMockedStatic = Mockito.mockStatic(Runtime.class)) {
            Runtime mockRuntime = Mockito.mock(Runtime.class);
            runtimeMockedStatic.when(Runtime::getRuntime).thenReturn(mockRuntime);
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(null);
            HeartbeatManager.getInstance().flushHeartbeat();
            Mockito.verify(mockRuntime).halt(1);
        }
    }

    @Test
    public void flushHeartbeat_TaskRunning_UpdatesGmtHeartBeat() {
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(
            DbTaskMetaManager.class)) {
            RplTask task = new RplTask();
            task.setStatus(TaskStatus.RUNNING.name());
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(task);
            HeartbeatManager.getInstance().flushHeartbeat();
            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.updateTask(
                Mockito.eq(taskId), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(), Mockito.isNull(),
                Mockito.any(Date.class)));
        }
    }

    @Test
    public void flushHeartbeat_TransientFailure_RetriesThreeTimes() {
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(
            DbTaskMetaManager.class)) {
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId))
                .thenThrow(new IllegalStateException("temporary failure"));

            HeartbeatManager.getInstance().flushHeartbeat();

            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.getTask(taskId), Mockito.times(3));
        }
    }

    @Test
    public void flushHeartbeat_InterruptedDuringRetry_PreservesInterruptFlag() {
        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(
            DbTaskMetaManager.class)) {
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId))
                .thenThrow(new IllegalStateException("temporary failure"));
            Thread.currentThread().interrupt();

            HeartbeatManager.getInstance().flushHeartbeat();

            Assert.assertTrue("interrupt flag must be preserved", Thread.interrupted());
            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.getTask(taskId), Mockito.times(1));
        }
    }

    @Test
    public void flushHeartbeat_TaskNotRunning_ExitsProcess() {
        try (
            MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(DbTaskMetaManager.class);
            MockedStatic<Runtime> runtimeMockedStatic = Mockito.mockStatic(Runtime.class)) {
            Runtime mockRuntime = Mockito.mock(Runtime.class);
            runtimeMockedStatic.when(Runtime::getRuntime).thenReturn(mockRuntime);
            RplTask task = new RplTask();
            task.setStatus(TaskStatus.FINISHED.name());
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(task);
            HeartbeatManager.getInstance().flushHeartbeat();
            Mockito.verify(mockRuntime).halt(1);
        }
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
