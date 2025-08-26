/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.GmsTimeUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_FORCE_KILL_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.util.GmsTimeUtil.getHeartbeatInterval;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

public class TaskAliveWatcherTest extends BaseTest {

    @Test
    public void testShouldRestartTask() throws Exception {
        BinlogTaskConfig config = new BinlogTaskConfig();
        config.setRole("Final");
        config.setClusterId("clusterId");
        config.setTaskName("task1");

        TaskAliveWatcher taskAliveWatcher = Mockito.spy(
            new TaskAliveWatcher("cluster", "clusterType", "taskName", 100));

        when(getInt(DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS)).thenReturn(2000);
        when(getInt(DAEMON_FORCE_KILL_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS)).thenReturn(60000);
        try (MockedStatic<GmsTimeUtil> mockedStatic = Mockito.mockStatic(GmsTimeUtil.class)) {
            when(getHeartbeatInterval(config.getRole(), config.getClusterId(), config.getTaskName())).thenReturn(5000L);

            // heartbeat timeout + task down
            doReturn(false).when(taskAliveWatcher).isTaskProcessAlive(anyString());
            Assert.assertTrue(taskAliveWatcher.shouldRestartTask(config));

            // heartbeat timeout + task alive + not force kill
            doReturn(true).when(taskAliveWatcher).isTaskProcessAlive(anyString());
            Assert.assertFalse(taskAliveWatcher.shouldRestartTask(config));

            // heartbeat timeout + task alive + force kill
            when(getHeartbeatInterval(config.getRole(), config.getClusterId(), config.getTaskName())).thenReturn(
                90000L);
            Assert.assertTrue(taskAliveWatcher.shouldRestartTask(config));

            // heartbeat not timeout
            when(getHeartbeatInterval(config.getRole(), config.getClusterId(), config.getTaskName())).thenReturn(
                1000L);
            Assert.assertFalse(taskAliveWatcher.shouldRestartTask(config));

        }
    }
}
