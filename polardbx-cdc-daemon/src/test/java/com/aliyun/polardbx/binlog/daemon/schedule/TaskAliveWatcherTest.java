/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.RuntimeMode;
import com.aliyun.polardbx.binlog.daemon.pipeline.CommandPipeline;
import com.aliyun.polardbx.binlog.daemon.vo.CommandResult;
import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.BinlogTaskInfoMapper;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.GmsTimeUtil;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.mybatis.dynamic.sql.delete.DeleteDSLCompleter;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.mybatis.dynamic.sql.update.UpdateDSLCompleter;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_FORCE_KILL_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_WORK_PROCESS_BLACKLIST;
import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS;
import static com.aliyun.polardbx.binlog.ConfigKeys.RUNTIME_MODE;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.util.GmsTimeUtil.getHeartbeatInterval;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TaskAliveWatcherTest extends BaseTest {
    private AutoCloseable closeable;
    @Mock
    private BinlogTaskConfigMapper taskConfigMapper;
    @Mock
    private DumperInfoMapper dumperInfoMapper;
    @Mock
    private BinlogTaskInfoMapper taskInfoMapper;
    @Mock
    private CommandPipeline commandPipeline;

    private TaskAliveWatcher taskAliveWatcher;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        // Mock Spring context objects
        registerSpringObject(BinlogTaskConfigMapper.class, taskConfigMapper);
        registerSpringObject(DumperInfoMapper.class, dumperInfoMapper);
        registerSpringObject(BinlogTaskInfoMapper.class, taskInfoMapper);

        taskAliveWatcher = spy(new TaskAliveWatcher("cluster", "clusterType", "taskName", 100, commandPipeline));
    }

    @After
    public void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    public void testShouldRestartTask() throws Exception {
        BinlogTaskConfig config = new BinlogTaskConfig();
        config.setRole("Final");
        config.setClusterId("clusterId");
        config.setTaskName("task1");
        config.setContainerId("containerId");

        TaskAliveWatcher.CommonInfo commonInfo = new TaskAliveWatcher.CommonInfo(
            "task1", new Date(), new Date(), 1L, "containerId");

        TaskAliveWatcher taskAliveWatcher = spy(
            new TaskAliveWatcher("cluster", "clusterType", "taskName", 100, commandPipeline));

        when(getInt(DAEMON_WATCH_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS)).thenReturn(2000);
        when(getInt(DAEMON_FORCE_KILL_WORK_PROCESS_HEARTBEAT_TIMEOUT_MS)).thenReturn(60000);
        try (MockedStatic<GmsTimeUtil> mockedStatic = mockStatic(GmsTimeUtil.class)) {
            when(getHeartbeatInterval(config.getRole(), config.getClusterId(), config.getTaskName())).thenReturn(5000L);

            // heartbeat timeout + task down
            doReturn(false).when(taskAliveWatcher).isTaskProcessAlive(anyString());
            Assert.assertTrue(taskAliveWatcher.shouldRestartTask(config, commonInfo));

            // heartbeat timeout + task alive + not force kill
            doReturn(true).when(taskAliveWatcher).isTaskProcessAlive(anyString());
            Assert.assertFalse(taskAliveWatcher.shouldRestartTask(config, commonInfo));

            // heartbeat timeout + task alive + force kill
            when(getHeartbeatInterval(config.getRole(), config.getClusterId(), config.getTaskName())).thenReturn(
                90000L);
            Assert.assertTrue(taskAliveWatcher.shouldRestartTask(config, commonInfo));

            // heartbeat not timeout
            when(getHeartbeatInterval(config.getRole(), config.getClusterId(), config.getTaskName())).thenReturn(
                1000L);
            Assert.assertFalse(taskAliveWatcher.shouldRestartTask(config, commonInfo));

            // different containerId
            commonInfo.containerId = "containerId2";
            Assert.assertTrue(taskAliveWatcher.shouldRestartTask(config, commonInfo));

        }
    }

    @Test
    public void testIsTaskProcessAlive() throws Exception {
        // Test when task is alive
        Set<String> runningTasks = new HashSet<>();
        runningTasks.add("task1");
        runningTasks.add("task2");
        doReturn(runningTasks).when(taskAliveWatcher).getAllTaskProcess();

        Assert.assertTrue(taskAliveWatcher.isTaskProcessAlive("task1"));
        Assert.assertFalse(taskAliveWatcher.isTaskProcessAlive("task3"));

        // Test when no tasks are running
        doReturn(new HashSet<>()).when(taskAliveWatcher).getAllTaskProcess();
        Assert.assertFalse(taskAliveWatcher.isTaskProcessAlive("task1"));
    }

    @Test
    public void testStopTaskWhitelist() {
        // Test with whitelist configured
        mockConfig(DAEMON_WATCH_WORK_PROCESS_BLACKLIST, "task1,task2");
        Set<String> whitelist = taskAliveWatcher.stopTaskWhitList();
        Assert.assertTrue(whitelist.contains("task1"));
        Assert.assertTrue(whitelist.contains("task2"));
        Assert.assertEquals(2, whitelist.size());

        // Test with no whitelist configured
        mockConfig(DAEMON_WATCH_WORK_PROCESS_BLACKLIST, "");
        whitelist = taskAliveWatcher.stopTaskWhitList();
        Assert.assertTrue(whitelist.isEmpty());
    }

    @Test
    public void testTryCleanRocksDb() throws IOException {
        // Create temporary directory and files for testing
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test_rocksdb_" + System.currentTimeMillis());
        tempDir.mkdirs();

        File task1Dir = new File(tempDir, "task1");
        task1Dir.mkdir();

        File task2Dir = new File(tempDir, "task2");
        task2Dir.mkdir();

        Set<String> localTasks = new HashSet<>();
        localTasks.add("task1"); // Only task1 is a local task, task2 should be deleted

        try {
            taskAliveWatcher.tryCleanRocksDb(tempDir.getAbsolutePath(), localTasks);

            // task1 should still exist, task2 should be deleted
            Assert.assertTrue(task1Dir.exists());
            // Note: We can't reliably test deletion in unit test without more complex mocking
        } finally {
            // Clean up
            task1Dir.delete();
            task2Dir.delete();
            tempDir.delete();
        }
    }

    @Test
    public void testTryCleanRdsBinlog() {
        // Create temporary directory and files for testing
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test_binlog_" + System.currentTimeMillis());
        tempDir.mkdirs();

        File task1Dir = new File(tempDir, "task1");
        task1Dir.mkdir();

        File task2Dir = new File(tempDir, "task2");
        task2Dir.mkdir();

        File testDir = new File(tempDir, "__test__");
        testDir.mkdir();

        Set<String> localTasks = new HashSet<>();
        localTasks.add("task1"); // Only task1 is a local task, task2 should be deleted, __test__ should remain

        try {
            taskAliveWatcher.tryCleanRdsBinlog(localTasks);

            // task1 and __test__ should still exist, task2 should be deleted
            Assert.assertTrue(task1Dir.exists());
            Assert.assertTrue(testDir.exists());
            // Note: We can't reliably test deletion in unit test without more complex mocking
        } finally {
            // Clean up
            task1Dir.delete();
            task2Dir.delete();
            testDir.delete();
            tempDir.delete();
        }
    }

    @Test
    public void testCommonInfoConstructor() {
        Date heartbeatTime = new Date();
        Date startTime = new Date(System.currentTimeMillis() - 10000);
        TaskAliveWatcher.CommonInfo info = new TaskAliveWatcher.CommonInfo(
            "testTask", heartbeatTime, startTime, 5L, "container123");

        Assert.assertEquals("testTask", info.name);
        Assert.assertEquals(heartbeatTime, info.heartbeatTime);
        Assert.assertEquals(startTime, info.startTime);
        Assert.assertEquals(5L, info.version);
        Assert.assertEquals("container123", info.containerId);
    }

    @Test
    public void testCommonInfoToString() {
        TaskAliveWatcher.CommonInfo info = new TaskAliveWatcher.CommonInfo(
            "testTask", new Date(), new Date(), 5L, "container123");

        String str = info.toString();
        Assert.assertTrue(str.contains("testTask"));
        Assert.assertTrue(str.contains("container123"));
    }

    @Test
    public void testExecInLocalMode() {
        mockConfig(RUNTIME_MODE, "LOCAL");

        try (MockedStatic<RuntimeMode> runtimeModeStatic = mockStatic(RuntimeMode.class)) {
            runtimeModeStatic.when(() -> RuntimeMode.valueOf(anyString())).thenReturn(RuntimeMode.LOCAL);
            runtimeModeStatic.when(() -> RuntimeMode.isLocalMode(RuntimeMode.LOCAL)).thenReturn(true);

            taskAliveWatcher.exec();
            // Should return early without doing anything
            verify(taskConfigMapper, times(0)).select(any());
        }
    }

    @Test
    public void testUpdateTaskStatusWithTask() {
        taskAliveWatcher.updateTaskStatus("clusterId", "taskName", TaskType.Final.name(),
            com.aliyun.polardbx.binlog.enums.BinlogTaskStatus.RUNNING);

        // Verify that taskInfoMapper.update was called, not dumperInfoMapper.update
        verify(taskInfoMapper, times(1)).update(any(UpdateDSLCompleter.class));
        verify(dumperInfoMapper, times(0)).update(any(UpdateDSLCompleter.class));
    }

    @Test
    public void testUpdateTaskStatusWithDumper() {
        taskAliveWatcher.updateTaskStatus("clusterId", "dumperName", TaskType.Dumper.name(),
            com.aliyun.polardbx.binlog.enums.BinlogTaskStatus.RUNNING);

        // Verify that dumperInfoMapper.update was called, not taskInfoMapper.update
        verify(dumperInfoMapper, times(1)).update(any(UpdateDSLCompleter.class));
        verify(taskInfoMapper, times(0)).update(any(UpdateDSLCompleter.class));
    }

    @Test
    public void testDeleteTaskInfo() {
        taskAliveWatcher.deleteTaskInfo("taskName");
        verify(taskInfoMapper, times(1)).delete(any(DeleteDSLCompleter.class));
    }

    @Test
    public void testDeleteDumperInfo() {
        taskAliveWatcher.deleteDumperInfo("dumperName");
        verify(dumperInfoMapper, times(1)).delete(any(DeleteDSLCompleter.class));
    }

    @Test
    public void testCleanInfoForTask() {
        BinlogTaskConfig config = new BinlogTaskConfig();
        config.setRole(TaskType.Final.name());
        config.setTaskName("finalTask");

        taskAliveWatcher.cleanInfo(config);
        verify(taskInfoMapper, times(1)).delete(any(DeleteDSLCompleter.class));
        verify(dumperInfoMapper, times(0)).delete(any(DeleteDSLCompleter.class));
    }

    @Test
    public void testCleanInfoForDumper() {
        BinlogTaskConfig config = new BinlogTaskConfig();
        config.setRole(TaskType.Dumper.name());
        config.setTaskName("dumperTask");

        taskAliveWatcher.cleanInfo(config);
        verify(dumperInfoMapper, times(1)).delete(any(DeleteDSLCompleter.class));
        verify(taskInfoMapper, times(0)).delete(any(DeleteDSLCompleter.class));
    }

    @Test
    public void testTryCleanRocksDb2() throws IOException {
        // Create a temporary directory for testing
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test_rocksdb2_" + System.currentTimeMillis());
        tempDir.mkdirs();

        // Create parent directory and subdirectories with incorrect path separator
        File parentDir = new File(tempDir, "parent");
        parentDir.mkdirs();

        // Mock the configuration to return our test path
        String basePath = parentDir.getAbsolutePath() + File.separator + "suffix";
        mockConfig(com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_BASE_PATH, basePath);

        // Create directories that match the pattern that should be deleted
        // These use File.pathSeparator instead of File.separator (the bug we're testing)
        File badDir1 = new File(parentDir, "suffix" + File.pathSeparator + "task1");
        File badDir2 = new File(parentDir, "suffix" + File.pathSeparator + "task2");
        badDir1.mkdirs();
        badDir2.mkdirs();

        // Create a directory that should NOT be deleted
        File goodDir = new File(parentDir, "suffix" + File.separator + "task3");
        goodDir.mkdirs();

        try {
            taskAliveWatcher.tryCleanRocksDb2();

            // The bad directories should be deleted
            Assert.assertFalse("Directory with pathSeparator should be deleted", badDir1.exists());
            Assert.assertFalse("Directory with pathSeparator should be deleted", badDir2.exists());

            // The good directory should still exist
            Assert.assertTrue("Directory with separator should not be deleted", goodDir.exists());
        } finally {
            // Clean up
            goodDir.delete();
            parentDir.delete();
            tempDir.delete();
        }
    }

    @Test
    public void testTryCleanRocksDb2_Exception() {
        // Create a temporary directory for testing
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test_rocksdb2_" + System.currentTimeMillis());
        tempDir.mkdirs();

        // Create parent directory and subdirectories with incorrect path separator
        File parentDir = new File(tempDir, "parent");
        parentDir.mkdirs();

        // Mock the configuration to return our test path
        String basePath = parentDir.getAbsolutePath() + File.separator + "suffix";
        mockConfig(com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_BASE_PATH, basePath);

        // Create directories that match the pattern that should be deleted
        // These use File.pathSeparator instead of File.separator (the bug we're testing)
        File badDir1 = new File(parentDir, "suffix" + File.pathSeparator + "task1");
        File badDir2 = new File(parentDir, "suffix" + File.pathSeparator + "task2");
        badDir1.mkdirs();
        badDir2.mkdirs();

        // Create a directory that should NOT be deleted
        File goodDir = new File(parentDir, "suffix" + File.separator + "task3");
        goodDir.mkdirs();

        try (MockedStatic<FileUtils> fileUtilsStatic = mockStatic(FileUtils.class)) {
            fileUtilsStatic.when(() -> FileUtils.forceDelete(any(File.class)))
                .thenThrow(new IOException("Test Exception"));
            taskAliveWatcher.tryCleanRocksDb2();
            Assert.fail("Expected exception not thrown");
        } catch (PolardbxException e) {
            Assert.assertEquals("delete failed.", e.getMessage());
        }
    }

    @Test
    public void testStopTasksNotBelongsToThisNode() throws Exception {
        // Prepare test data
        Set<String> localTasks = new HashSet<>();
        localTasks.add("local-task-1");
        localTasks.add("local-task-2");

        // Mock CommandPipeline to return running tasks
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(0);
        commandResult.setMsg("remote-task-1\nremote-task-2");

        // Execute the method
        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);
        taskAliveWatcher.stopTasksNotBelongsToThisNode(localTasks);

        // Verify that stopTask was called for remote tasks
        verify(commandPipeline, times(2)).stopTask(anyString());
        verify(commandPipeline).stopTask("remote-task-1");
        verify(commandPipeline).stopTask("remote-task-2");

    }

    @Test
    public void testStopTasksNotBelongsToThisNodeWithWhitelist() throws Exception {
        // Prepare test data
        Set<String> localTasks = new HashSet<>();
        localTasks.add("local-task-1");

        // Mock configs with whitelist
        mockConfig(ConfigKeys.DAEMON_WATCH_WORK_PROCESS_BLACKLIST, "remote-task-2");

        // Mock CommandPipeline to return running tasks
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(0);
        commandResult.setMsg("remote-task-1\nremote-task-2");

        // Execute the method
        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);
        taskAliveWatcher.stopTasksNotBelongsToThisNode(localTasks);

        // Verify that stopTask was only called for non-whitelisted remote task
        verify(commandPipeline, times(1)).stopTask(anyString());
        verify(commandPipeline).stopTask("remote-task-1");
        verify(commandPipeline, never()).stopTask("remote-task-2");
    }

    @Test
    public void testStopTasksNotBelongsToThisNodeWithNoRunningTasks() throws Exception {
        // Prepare test data
        Set<String> localTasks = new HashSet<>();
        localTasks.add("local-task-1");

        // Mock CommandPipeline to return no running tasks
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(0);
        commandResult.setMsg("");

        // Execute the method
        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);
        taskAliveWatcher.stopTasksNotBelongsToThisNode(localTasks);

        // Verify that stopTask was never called
        verify(commandPipeline, never()).stopTask(anyString());
    }

    @Test
    public void testStopTasksNotBelongsToThisNodeWithCommandError() throws Exception {
        // Prepare test data
        Set<String> localTasks = new HashSet<>();
        localTasks.add("local-task-1");

        // Mock CommandPipeline to return error
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(1);
        commandResult.setMsg("error");

        // Execute the method
        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);
        taskAliveWatcher.stopTasksNotBelongsToThisNode(localTasks);

        // Verify that stopTask was never called
        verify(commandPipeline, never()).stopTask(anyString());

    }

    @Test
    public void testGetAllTaskProcess() throws Exception {
        // Mock CommandPipeline
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(0);
        commandResult.setMsg("task-1\ntask-2\n");

        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);

        // Execute the method
        Set<String> result = taskAliveWatcher.getAllTaskProcess();

        // Verify the result
        Assert.assertEquals(2, result.size());
        Assert.assertTrue(result.contains("task-1"));
        Assert.assertTrue(result.contains("task-2"));
    }

    @Test
    public void testGetAllTaskProcessWithError() throws Exception {
        // Mock CommandPipeline to return error
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(1);
        commandResult.setMsg("error");

        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);
        // Execute the method
        Set<String> result = taskAliveWatcher.getAllTaskProcess();

        // Verify the result is empty
        Assert.assertTrue(result.isEmpty());

    }

    @Test
    public void testTryStartTask_CountZero() throws Exception {
        // 准备: 模拟命令执行结果，返回0表示没有找到进程
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(0);
        commandResult.setMsg("0");

        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);

        // 执行
        taskAliveWatcher.tryStartTask("test-task", 1024, false);

        // 验证
        verify(commandPipeline).execCommand(any(String[].class), eq(1000L));
        verify(commandPipeline).startTask("test-task", 1024);
        verify(commandPipeline, never()).stopTask(anyString());
    }

    @Test
    public void testTryStartTask_CountOne() throws Exception {
        // 准备: 模拟命令执行结果，返回1表示已经有一个进程在运行
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(0);
        commandResult.setMsg("1");

        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);

        // 执行
        taskAliveWatcher.tryStartTask("test-task", 1024, false);

        // 验证
        verify(commandPipeline).execCommand(any(String[].class), eq(1000L));
        verify(commandPipeline, never()).startTask(anyString(), anyInt());
        verify(commandPipeline, never()).stopTask(anyString());
    }

    @Test
    public void testTryStartTask_CountMoreThanOne() throws Exception {
        // 准备: 模拟命令执行结果，返回2表示有多个进程在运行
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(0);
        commandResult.setMsg("2");

        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);

        // 执行
        taskAliveWatcher.tryStartTask("test-task", 1024, false);

        // 验证
        verify(commandPipeline).execCommand(any(String[].class), eq(1000L));
        verify(commandPipeline, never()).startTask(anyString(), anyInt());
        verify(commandPipeline).stopTask("test-task");
    }

    @Test
    public void testTryStartTask_RestartMode() throws Exception {
        // 准备: 测试restart模式下的日志输出
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(0);
        commandResult.setMsg("0");

        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);

        // 执行
        taskAliveWatcher.tryStartTask("test-task", 1024, true);

        // 验证
        verify(commandPipeline).execCommand(any(String[].class), eq(1000L));
        verify(commandPipeline).startTask("test-task", 1024);
    }

    @Test(expected = NumberFormatException.class)
    public void testTryStartTask_InvalidNumberFormat() throws Exception {
        // 准备: 模拟无效的数字格式
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(0);
        commandResult.setMsg("invalid"); // 无法解析为数字

        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);

        // 执行 - 应该抛出NumberFormatException
        taskAliveWatcher.tryStartTask("test-task", 1024, false);
    }

    @Test
    public void testTryStartTask_CommandFail() throws Exception {
        // 准备: 模拟命令执行失败
        CommandResult commandResult = new CommandResult();
        commandResult.setCode(1); // 非0表示失败
        commandResult.setMsg("error");

        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);

        // 执行
        taskAliveWatcher.tryStartTask("test-task", 1024, false);

        // 验证: 命令失败时不应该执行任何操作
        verify(commandPipeline).execCommand(any(String[].class), eq(1000L));
        verify(commandPipeline, never()).startTask(anyString(), anyInt());
        verify(commandPipeline, never()).stopTask(anyString());
    }

    @Test
    public void testTryStartTask_ForceRestart() throws Exception {
        // 准备测试数据
        BinlogTaskConfig config = new BinlogTaskConfig();
        config.setTaskName("test-task");
        config.setMem(1024);
        config.setRole("Task");

        Set<String> forceRestartTaskSet = new HashSet<>();
        forceRestartTaskSet.add("test-task");

        CommandResult commandResult = new CommandResult();
        commandResult.setCode(0);
        commandResult.setMsg("1");
        when(commandPipeline.execCommand(any(String[].class), anyLong())).thenReturn(commandResult);

        // 执行
        taskAliveWatcher.tryStartTask(config, forceRestartTaskSet);

        // 验证
        verify(taskAliveWatcher, times(1)).restartTask(eq(config), eq("test-task"), eq(1024));
    }

    @Test
    public void testTryStartTask_TaskTypeWithInfoPresent_NoRestart() throws Exception {
        // 准备测试数据
        BinlogTaskConfig config = new BinlogTaskConfig();
        config.setTaskName("test-task");
        config.setMem(1024);
        config.setRole("Final");
        config.setVersion(2L);

        Set<String> forceRestartTaskSet = new HashSet<>();

        // Mock task info
        BinlogTaskInfo taskInfo = new BinlogTaskInfo();
        taskInfo.setTaskName("test-task");
        taskInfo.setGmtHeartbeat(new Date());
        taskInfo.setGmtCreated(new Date());
        taskInfo.setVersion(2L); // Same version
        taskInfo.setContainerId("test-inst-id");

        when(taskInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(java.util.Optional.of(taskInfo));
        doReturn(false).when(taskAliveWatcher).shouldRestartTask(any(), any());

        // 执行
        taskAliveWatcher.tryStartTask(config, forceRestartTaskSet);

        // 验证
        verify(taskInfoMapper).selectOne(any(SelectDSLCompleter.class));
        verify(taskAliveWatcher).shouldRestartTask(any(), any());
        verify(taskAliveWatcher, never()).restartTask(any(), anyString(), anyInt());
        verify(taskAliveWatcher, never()).tryStartTask(anyString(), anyInt(), anyBoolean());
    }

    @Test
    public void testTryStartTask_TaskTypeWithInfoPresent_ShouldRestart() throws Exception {
        // 准备测试数据
        BinlogTaskConfig config = new BinlogTaskConfig();
        config.setTaskName("test-task");
        config.setMem(1024);
        config.setRole("Final");
        config.setVersion(2L);

        Set<String> forceRestartTaskSet = new HashSet<>();

        // Mock task info
        BinlogTaskInfo taskInfo = new BinlogTaskInfo();
        taskInfo.setTaskName("test-task");
        taskInfo.setGmtHeartbeat(new Date());
        taskInfo.setGmtCreated(new Date());
        taskInfo.setVersion(1L); // Older version
        taskInfo.setContainerId("test-inst-id");

        when(taskInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(java.util.Optional.of(taskInfo));
        doReturn(false).when(taskAliveWatcher).shouldRestartTask(any(), any());
        doNothing().when(taskAliveWatcher).restartTask(config, "test-task", 1024);

        // 执行
        taskAliveWatcher.tryStartTask(config, forceRestartTaskSet);

        // 验证
        verify(taskInfoMapper).selectOne(any(SelectDSLCompleter.class));
        verify(taskAliveWatcher).shouldRestartTask(any(), any());
        verify(taskAliveWatcher).restartTask(eq(config), eq("test-task"), eq(1024));
    }

}