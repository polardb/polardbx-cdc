/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.dao.DumperInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.dumper.dump.logfile.LogFileManagerCollection;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class DumperControllerTest extends BaseTest {

    private TaskRuntimeConfig taskRuntimeConfig;
    private ExecutionConfig executionConfig;

    @Before
    public void setUp() {
        // 初始化测试数据
        mockConfig(ConfigKeys.BINLOGX_STREAM_GROUP_NAME, "test_group");
        mockConfig(ConfigKeys.CLUSTER_ID, "test_cluster");
        mockConfig(ConfigKeys.INST_ID, "test_inst");
        mockConfig(ConfigKeys.INST_IP, "127.0.0.1");
        mockConfig(ConfigKeys.RUNTIME_MODE, "LOCAL");
        mockConfig(ConfigKeys.BINLOG_DIR_PATH, "/tmp/test/binlog"); // 添加binlog目录路径配置
        mockConfig(ConfigKeys.BINLOGX_DIR_PATH_PREFIX, "/tmp/test/binlogx"); // 添加binlogx目录路径前缀配置
        mockConfig(ConfigKeys.BINLOG_FILE_SIZE, "134217728"); // 128MB
        mockConfig(ConfigKeys.BINLOG_WRITE_DRY_RUN_ENABLE, "false");
        mockConfig(ConfigKeys.BINLOG_WRITE_FLUSH_POLICY, "1");
        mockConfig(ConfigKeys.BINLOG_WRITE_FLUSH_INTERVAL, "1000");
        mockConfig(ConfigKeys.BINLOG_WRITE_BUFFER_SIZE, "1048576"); // 1MB
        mockConfig(ConfigKeys.BINLOG_DISK_SPACE_MAX_SIZE_MB, "10240"); // 10GB
        mockConfig(ConfigKeys.DISK_SIZE, "102400"); // 100GB
        mockConfig(ConfigKeys.BINLOG_PURGE_DISK_USE_RATIO, "0.9"); // 90%
        mockConfig(ConfigKeys.POLARX_INST_ID, "test_polarx_inst_id");
        mockConfig(ConfigKeys.BINLOGX_TRANSMIT_HASH_LEVEL, "TABLE"); // 添加hash level配置

        // 创建 BinlogTaskConfig
        BinlogTaskConfig binlogTaskConfig = new BinlogTaskConfig();
        binlogTaskConfig.setVersion(1L);
        binlogTaskConfig.setSubVersion(1L);

        // 创建 ExecutionConfig
        executionConfig = new ExecutionConfig();
        executionConfig.setRuntimeVersion(1L);
        executionConfig.setSubRuntimeVersion(1L);

        Set<String> streamNames = new HashSet<>();
        streamNames.add("stream1");
        streamNames.add("stream2");
        executionConfig.setStreamNameSet(streamNames);

        // 创建 TaskRuntimeConfig
        taskRuntimeConfig = new TaskRuntimeConfig();
        taskRuntimeConfig.setType(TaskType.DumperX);
        taskRuntimeConfig.setName("test_dumper");
        taskRuntimeConfig.setServerPort(8080);
        taskRuntimeConfig.setExecutionConfig(executionConfig);
        taskRuntimeConfig.setBinlogTaskConfig(binlogTaskConfig); // 设置binlogTaskConfig

        // Mock数据库相关组件
        DumperInfoMapper dumperInfoMapper = Mockito.mock(DumperInfoMapper.class);
        TransactionTemplate transactionTemplate = Mockito.mock(TransactionTemplate.class);
        com.aliyun.polardbx.binlog.dao.XStreamMapper xStreamMapper =
            Mockito.mock(com.aliyun.polardbx.binlog.dao.XStreamMapper.class);

        // 注册Spring对象
        registerSpringObject(DumperInfoMapper.class, dumperInfoMapper);
        registerSpringObject("metaTransactionTemplate", transactionTemplate);
        registerSpringObject("XStreamMapper", xStreamMapper);

        // 当执行事务时，直接执行回调
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            return invocation.getArgument(0, org.springframework.transaction.support.TransactionCallback.class)
                .doInTransaction(null);
        });

        // 创建一个模拟的 DumperInfo 对象
        com.aliyun.polardbx.binlog.domain.po.DumperInfo mockDumperInfo =
            new com.aliyun.polardbx.binlog.domain.po.DumperInfo();
        mockDumperInfo.setId(1L);
        mockDumperInfo.setClusterId("test_cluster");
        mockDumperInfo.setTaskName("test_dumper");
        mockDumperInfo.setIp("127.0.0.1");
        mockDumperInfo.setPort(8080);
        mockDumperInfo.setVersion(1L);
        mockDumperInfo.setSubVersion(1L);
        mockDumperInfo.setRole("XSTREAM");
        mockDumperInfo.setContainerId("test_inst");
        mockDumperInfo.setPolarxInstId("test_polarx_inst_id");
        mockDumperInfo.setEnableLightRebalance(true);

        // 当查询DumperInfo时，返回模拟的对象
        when(dumperInfoMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(
            java.util.Optional.of(mockDumperInfo));
    }

    @Test
    public void testConstructor() {
        DumperController controller = new DumperController(taskRuntimeConfig);
        Assert.assertNotNull(controller);
        Assert.assertEquals(taskRuntimeConfig, controller.getTaskRuntimeConfig());
    }

    @Test
    public void testStartAndStop() {
        DumperController controller = new DumperController(taskRuntimeConfig);

        // 初始状态应该是停止的
        Assert.assertFalse(controller.isRunning());

        // 启动控制器
        controller.start();
        Assert.assertTrue(controller.isRunning());

        // 再次启动不应该有变化
        controller.start();
        Assert.assertTrue(controller.isRunning());

        // 停止控制器
        controller.stop();
        Assert.assertFalse(controller.isRunning());

        // 再次停止不应该有变化
        controller.stop();
        Assert.assertFalse(controller.isRunning());
    }

    @Test
    public void testReloadForMultiStream() {
        DumperController controller = new DumperController(taskRuntimeConfig);

        // 创建新的配置用于重载
        ExecutionConfig newExecutionConfig = new ExecutionConfig();
        newExecutionConfig.setRuntimeVersion(1L);
        newExecutionConfig.setSubRuntimeVersion(2L);

        Set<String> newStreamNames = new HashSet<>();
        newStreamNames.add("stream1");
        newStreamNames.add("stream3"); // 替换stream2为stream3
        newExecutionConfig.setStreamNameSet(newStreamNames);

        // 创建 BinlogTaskConfig
        BinlogTaskConfig binlogTaskConfig = new BinlogTaskConfig();
        binlogTaskConfig.setVersion(1L);
        binlogTaskConfig.setSubVersion(2L);

        TaskRuntimeConfig newTaskRuntimeConfig = new TaskRuntimeConfig();
        newTaskRuntimeConfig.setType(TaskType.DumperX);
        newTaskRuntimeConfig.setName("test_dumper");
        newTaskRuntimeConfig.setServerPort(8080);
        newTaskRuntimeConfig.setExecutionConfig(newExecutionConfig);
        newTaskRuntimeConfig.setBinlogTaskConfig(binlogTaskConfig); // 设置binlogTaskConfig

        // 执行重载
        controller.reloadForMultiStream(newTaskRuntimeConfig);

        // 验证配置已更新
        Assert.assertEquals(newTaskRuntimeConfig, controller.getTaskRuntimeConfig());
    }

    @Test
    public void testReloadForSingleStream() {
        // 创建单流配置
        ExecutionConfig singleExecutionConfig = new ExecutionConfig();
        singleExecutionConfig.setRuntimeVersion(1L);
        singleExecutionConfig.setSubRuntimeVersion(1L);

        Set<String> singleStreamNames = new HashSet<>();
        singleStreamNames.add("stream_global");
        singleExecutionConfig.setStreamNameSet(singleStreamNames);

        // 创建 BinlogTaskConfig
        BinlogTaskConfig binlogTaskConfig = new BinlogTaskConfig();
        binlogTaskConfig.setVersion(1L);
        binlogTaskConfig.setSubVersion(1L);

        TaskRuntimeConfig singleTaskRuntimeConfig = new TaskRuntimeConfig();
        singleTaskRuntimeConfig.setType(TaskType.Dumper);
        singleTaskRuntimeConfig.setName("test_dumper");
        singleTaskRuntimeConfig.setServerPort(8080);
        singleTaskRuntimeConfig.setExecutionConfig(singleExecutionConfig);
        singleTaskRuntimeConfig.setBinlogTaskConfig(binlogTaskConfig); // 设置binlogTaskConfig

        DumperController controller = new DumperController(singleTaskRuntimeConfig);

        // 创建新的单流配置用于重载
        ExecutionConfig newSingleExecutionConfig = new ExecutionConfig();
        newSingleExecutionConfig.setRuntimeVersion(1L);
        newSingleExecutionConfig.setSubRuntimeVersion(2L);

        Set<String> newSingleStreamNames = new HashSet<>();
        newSingleStreamNames.add("stream_global");
        newSingleExecutionConfig.setStreamNameSet(newSingleStreamNames);

        // 创建 BinlogTaskConfig
        BinlogTaskConfig newBinlogTaskConfig = new BinlogTaskConfig();
        newBinlogTaskConfig.setVersion(1L);
        newBinlogTaskConfig.setSubVersion(2L);

        TaskRuntimeConfig newSingleTaskRuntimeConfig = new TaskRuntimeConfig();
        newSingleTaskRuntimeConfig.setType(TaskType.Dumper);
        newSingleTaskRuntimeConfig.setName("test_dumper");
        newSingleTaskRuntimeConfig.setServerPort(8080);
        newSingleTaskRuntimeConfig.setExecutionConfig(newSingleExecutionConfig);
        newSingleTaskRuntimeConfig.setBinlogTaskConfig(newBinlogTaskConfig); // 设置binlogTaskConfig

        // 执行重载
        controller.reloadForSingleStream(newSingleTaskRuntimeConfig);

        // 验证配置已更新
        Assert.assertEquals(newSingleTaskRuntimeConfig, controller.getTaskRuntimeConfig());
    }

    @Test
    public void testGetLogFileManagerCollection() {
        DumperController controller = new DumperController(taskRuntimeConfig);
        LogFileManagerCollection collection = controller.getLogFileManagerCollection();

        Assert.assertNotNull(collection);
    }
}
