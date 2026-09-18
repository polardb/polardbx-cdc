/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.dao.BinlogTaskConfigMapper;
import com.aliyun.polardbx.binlog.dao.StorageHistoryInfoMapper;
import com.aliyun.polardbx.binlog.domain.MergeSourceType;
import com.aliyun.polardbx.binlog.domain.StorageContent;
import com.aliyun.polardbx.binlog.domain.TaskRuntimeConfig;
import com.aliyun.polardbx.binlog.domain.po.BinlogTaskConfig;
import com.aliyun.polardbx.binlog.domain.po.StorageHistoryInfo;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mybatis.dynamic.sql.select.SelectDSLCompleter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.aliyun.polardbx.binlog.ConfigKeys.CLUSTER_ID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class TaskRuntimeConfigProviderTest extends BaseTest {

    private TaskRuntimeConfigProvider provider;
    private static final String TEST_TASK_NAME = "test_task";
    private static final String TEST_CLUSTER_ID = "test_cluster_id";

    @Before
    public void setUp() {
        // 设置集群ID配置
        mockConfig(CLUSTER_ID, TEST_CLUSTER_ID);
        provider = new TaskRuntimeConfigProvider(TEST_TASK_NAME);
    }

    @After
    public void tearDown() {
        provider = null;
    }

    @Test
    public void testBuildBinlogMergeSourceInfo() {
        String storageInstId = "storage_inst_1";
        int seq = 0;

        // 调用被测试的方法
        com.aliyun.polardbx.binlog.domain.MergeSourceInfo result =
            TaskRuntimeConfigProvider.buildBinlogMergeSourceInfo(seq, storageInstId);

        // 验证结果
        assertNotNull(result);
        assertEquals(String.format("%s-db-%s", seq, storageInstId), result.getId());
        assertEquals(MergeSourceType.BINLOG, result.getType());
        assertNotNull(result.getBinlogParameter());
        assertEquals(storageInstId, result.getBinlogParameter().getStorageInstId());
    }

    @Test
    public void testBuildRpcMergeSourceInfo() {
        String sourceTaskName = "source_task_1";

        // 调用被测试的方法
        com.aliyun.polardbx.binlog.domain.MergeSourceInfo result =
            TaskRuntimeConfigProvider.buildRpcMergeSourceInfo(sourceTaskName);

        // 验证结果
        assertNotNull(result);
        assertEquals(String.format("merge-source-%s", sourceTaskName), result.getId());
        assertEquals(MergeSourceType.RPC, result.getType());
        assertNotNull(result.getRpcParameter());
        assertEquals(sourceTaskName, result.getRpcParameter().getTaskName());
        assertTrue(result.getRpcParameter().isDynamic());
    }

    @Test(expected = PolardbxException.class)
    public void testGetBinlogTaskConfigNotFound() {
        // 模拟mapper返回空结果
        BinlogTaskConfigMapper mapper = Mockito.mock(BinlogTaskConfigMapper.class);
        registerSpringObject("binlogTaskConfigMapper", mapper);
        when(mapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.empty());

        // 调用方法应该抛出异常
        provider.getBinlogTaskConfig();
    }

    @Test
    public void testGetBinlogTaskConfigSuccess() {
        // 准备测试数据
        BinlogTaskConfig expectedConfig = new BinlogTaskConfig();
        expectedConfig.setId(1L);
        expectedConfig.setTaskName(TEST_TASK_NAME);
        expectedConfig.setClusterId(TEST_CLUSTER_ID);

        // 模拟mapper返回结果
        BinlogTaskConfigMapper mapper = Mockito.mock(BinlogTaskConfigMapper.class);
        registerSpringObject("binlogTaskConfigMapper", mapper);
        when(mapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(Optional.of(expectedConfig));

        // 调用方法
        BinlogTaskConfig result = provider.getBinlogTaskConfig();

        // 验证结果
        assertNotNull(result);
        assertEquals(expectedConfig.getId(), result.getId());
        assertEquals(expectedConfig.getTaskName(), result.getTaskName());
        assertEquals(expectedConfig.getClusterId(), result.getClusterId());
    }

    @Test(expected = PolardbxException.class)
    public void testGetStorageContentNotFound() {
        // 模拟mapper返回空结果
        StorageHistoryInfoMapper mapper = Mockito.mock(StorageHistoryInfoMapper.class);
        registerSpringObject("storageHistoryInfoMapper", mapper);
        when(mapper.select(any())).thenReturn(new ArrayList<>());

        // 调用方法应该抛出异常
        provider.getStorageContent("test_tso");
    }

    @Test
    public void testGetStorageContentSuccess() {
        // 准备测试数据
        StorageContent storageContent = new StorageContent();
        storageContent.setRepaired(true);
        storageContent.setStorageInstIds(Collections.singletonList("storage1"));

        StorageHistoryInfo historyInfo = new StorageHistoryInfo();
        historyInfo.setStorageContent(com.alibaba.fastjson.JSONObject.toJSONString(storageContent));

        // 模拟mapper返回结果
        StorageHistoryInfoMapper mapper = Mockito.mock(StorageHistoryInfoMapper.class);
        registerSpringObject("storageHistoryInfoMapper", mapper);
        List<StorageHistoryInfo> historyInfoList = new ArrayList<>();
        historyInfoList.add(historyInfo);
        when(mapper.select(any())).thenReturn(historyInfoList);

        // 调用方法
        StorageContent result = provider.getStorageContent("test_tso");

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isRepaired());
        assertFalse(result.getStorageInstIds().isEmpty());
        assertEquals("storage1", result.getStorageInstIds().get(0));
    }

    @Test
    public void testBuildExecutionConfigWithRuntimeVersion() {
        // 准备测试数据
        BinlogTaskConfig config = new BinlogTaskConfig();
        String taskConfig = "{\"type\":\"BINLOG\",\"sources\":[\"storage1\"],\"tso\":\"12345\",\"runtimeVersion\":2}";
        config.setConfig(taskConfig);
        config.setVersion(1L);

        // 调用被测试的方法
        ExecutionConfig result = provider.buildExecutionConfig(config);

        // 验证结果
        assertNotNull(result);
        assertEquals(2, result.getRuntimeVersion()); // 应该使用config中的runtimeVersion
    }

    @Test
    public void testBuildExecutionConfigWithoutRuntimeVersion() {
        // 准备测试数据
        BinlogTaskConfig config = new BinlogTaskConfig();
        String taskConfig = "{\"type\":\"BINLOG\",\"sources\":[\"storage1\"],\"tso\":\"12345\"}";
        config.setConfig(taskConfig);
        config.setVersion(3L);

        // 调用被测试的方法
        ExecutionConfig result = provider.buildExecutionConfig(config);

        // 验证结果
        assertNotNull(result);
        assertEquals(3, result.getRuntimeVersion()); // 应该使用config的version字段
    }

    @Test
    public void testGetTaskRuntimeConfigWithBinlogSourceType() {
        // 准备测试数据
        BinlogTaskConfig binlogTaskConfig = new BinlogTaskConfig();
        binlogTaskConfig.setId(1L);
        binlogTaskConfig.setTaskName(TEST_TASK_NAME);
        binlogTaskConfig.setRole("Dumper");
        binlogTaskConfig.setPort(8080);
        binlogTaskConfig.setVersion(1L);

        ExecutionConfig executionConfig = new ExecutionConfig();
        executionConfig.setType("BINLOG");
        executionConfig.setSources(Collections.singletonList("storage1"));
        executionConfig.setServerId(1111L);
        executionConfig.setTso(ExecutionConfig.ORIGIN_TSO);

        binlogTaskConfig.setConfig(com.alibaba.fastjson.JSONObject.toJSONString(executionConfig));

        // 模拟BinlogTaskConfigMapper
        BinlogTaskConfigMapper binlogTaskConfigMapper = Mockito.mock(BinlogTaskConfigMapper.class);
        registerSpringObject("binlogTaskConfigMapper", binlogTaskConfigMapper);
        when(binlogTaskConfigMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(
            Optional.of(binlogTaskConfig));

        // 模拟StorageHistoryInfoMapper
        StorageContent storageContent = new StorageContent();
        storageContent.setRepaired(true);

        StorageHistoryInfo historyInfo = new StorageHistoryInfo();
        historyInfo.setStorageContent(com.alibaba.fastjson.JSONObject.toJSONString(storageContent));

        StorageHistoryInfoMapper storageHistoryInfoMapper = Mockito.mock(StorageHistoryInfoMapper.class);
        registerSpringObject("storageHistoryInfoMapper", storageHistoryInfoMapper);
        when(storageHistoryInfoMapper.select(any())).thenReturn(Collections.singletonList(historyInfo));

        // 调用方法
        TaskRuntimeConfig result = provider.getTaskRuntimeConfig();

        // 验证结果
        assertNotNull(result);
        assertEquals(binlogTaskConfig.getId(), result.getId());
        assertEquals(binlogTaskConfig.getTaskName(), result.getName());
        assertEquals(binlogTaskConfig.getPort(), result.getServerPort());
        assertNotNull(result.getExecutionConfig());
        assertNotNull(result.getMergeSourceInfos());
        assertFalse(result.getMergeSourceInfos().isEmpty());
        assertEquals(1, result.getMergeSourceInfos().size());
        assertEquals(MergeSourceType.BINLOG, result.getMergeSourceInfos().get(0).getType());
        assertTrue(result.isForceCompleteHbWindow());
    }

    @Test
    public void testGetTaskRuntimeConfigWithRpcSourceType() {
        // 准备测试数据
        BinlogTaskConfig binlogTaskConfig = new BinlogTaskConfig();
        binlogTaskConfig.setId(1L);
        binlogTaskConfig.setTaskName(TEST_TASK_NAME);
        binlogTaskConfig.setRole("Dumper");
        binlogTaskConfig.setPort(8080);
        binlogTaskConfig.setVersion(1L);

        ExecutionConfig executionConfig = new ExecutionConfig();
        executionConfig.setType("RPC");
        executionConfig.setSources(Collections.singletonList("source_task"));
        executionConfig.setServerId(111L);
        executionConfig.setTso(ExecutionConfig.ORIGIN_TSO);

        binlogTaskConfig.setConfig(com.alibaba.fastjson.JSONObject.toJSONString(executionConfig));

        // 模拟BinlogTaskConfigMapper
        BinlogTaskConfigMapper binlogTaskConfigMapper = Mockito.mock(BinlogTaskConfigMapper.class);
        registerSpringObject("binlogTaskConfigMapper", binlogTaskConfigMapper);
        when(binlogTaskConfigMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(
            Optional.of(binlogTaskConfig));

        // 模拟StorageHistoryInfoMapper
        StorageContent storageContent = new StorageContent();
        storageContent.setRepaired(false);

        StorageHistoryInfo historyInfo = new StorageHistoryInfo();
        historyInfo.setStorageContent(com.alibaba.fastjson.JSONObject.toJSONString(storageContent));

        StorageHistoryInfoMapper storageHistoryInfoMapper = Mockito.mock(StorageHistoryInfoMapper.class);
        registerSpringObject("storageHistoryInfoMapper", storageHistoryInfoMapper);
        when(storageHistoryInfoMapper.select(any())).thenReturn(Collections.singletonList(historyInfo));

        // 调用方法
        TaskRuntimeConfig result = provider.getTaskRuntimeConfig();

        // 验证结果
        assertNotNull(result);
        assertEquals(binlogTaskConfig.getId(), result.getId());
        assertEquals(binlogTaskConfig.getTaskName(), result.getName());
        assertEquals(binlogTaskConfig.getPort(), result.getServerPort());
        assertNotNull(result.getExecutionConfig());
        assertNotNull(result.getMergeSourceInfos());
        assertFalse(result.getMergeSourceInfos().isEmpty());
        assertEquals(1, result.getMergeSourceInfos().size());
        assertEquals(MergeSourceType.RPC, result.getMergeSourceInfos().get(0).getType());
        assertFalse(result.isForceCompleteHbWindow());
    }

    @Test(expected = Exception.class)
    public void testGetTaskRuntimeConfigWithInvalidSourceType() {
        // 准备测试数据
        BinlogTaskConfig binlogTaskConfig = new BinlogTaskConfig();
        binlogTaskConfig.setId(1L);
        binlogTaskConfig.setTaskName(TEST_TASK_NAME);
        binlogTaskConfig.setRole("Dumper");
        binlogTaskConfig.setPort(8080);
        binlogTaskConfig.setVersion(1L);

        ExecutionConfig executionConfig = new ExecutionConfig();
        executionConfig.setType("INVALID_TYPE");
        executionConfig.setSources(Collections.singletonList("source_task"));
        executionConfig.setServerId(111L);
        executionConfig.setTso(ExecutionConfig.ORIGIN_TSO);

        binlogTaskConfig.setConfig(com.alibaba.fastjson.JSONObject.toJSONString(executionConfig));

        // 模拟BinlogTaskConfigMapper
        BinlogTaskConfigMapper binlogTaskConfigMapper = Mockito.mock(BinlogTaskConfigMapper.class);
        registerSpringObject("binlogTaskConfigMapper", binlogTaskConfigMapper);
        when(binlogTaskConfigMapper.selectOne(any(SelectDSLCompleter.class))).thenReturn(
            Optional.of(binlogTaskConfig));

        // 模拟StorageHistoryInfoMapper
        StorageContent storageContent = new StorageContent();
        storageContent.setRepaired(false);

        StorageHistoryInfo historyInfo = new StorageHistoryInfo();
        historyInfo.setStorageContent(com.alibaba.fastjson.JSONObject.toJSONString(storageContent));

        StorageHistoryInfoMapper storageHistoryInfoMapper = Mockito.mock(StorageHistoryInfoMapper.class);
        registerSpringObject("storageHistoryInfoMapper", storageHistoryInfoMapper);
        when(storageHistoryInfoMapper.select(any())).thenReturn(Collections.singletonList(historyInfo));

        // 调用方法应该抛出异常
        provider.getTaskRuntimeConfig();
    }
}
