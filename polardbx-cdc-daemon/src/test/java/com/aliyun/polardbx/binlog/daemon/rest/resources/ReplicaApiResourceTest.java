/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.ResultCode;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.domain.po.RplTask;
import com.aliyun.polardbx.binlog.domain.po.RplTaskConfig;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.taskmeta.DbTaskMetaManager;
import com.aliyun.polardbx.rpl.taskmeta.RdsExtractorConfig;
import com.aliyun.polardbx.rpl.taskmeta.ServiceType;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ReplicaApiResourceTest extends BaseTest {
    @Mock
    private DbTaskMetaManager dbTaskMetaManager;

    private ReplicaApiResource replicaApiResource;

    @Before
    public void setUp() {
        replicaApiResource = new ReplicaApiResource();
    }

    /**
     * 测试enableHeartbeat方法 - 成功启用心跳
     */
    @Test
    public void testEnableHeartbeat_Success() {
        Long taskId = 1L;
        Boolean enableHeartbeat = true;

        // Mock RplTask
        RplTask mockTask = Mockito.mock(RplTask.class);
        when(mockTask.getType()).thenReturn(ServiceType.INC_COPY.name());

        // Mock RplTaskConfig
        RplTaskConfig mockTaskConfig = Mockito.mock(RplTaskConfig.class);
        when(mockTaskConfig.getTaskId()).thenReturn(taskId);
        when(mockTaskConfig.getExtractorConfig()).thenReturn(
            "{\"enableDetectHeartbeat\":false,\"createHeartbeatTable\":false}");

        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(
            DbTaskMetaManager.class)) {
            // 设置DbTaskMetaManager的mock行为
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(mockTask);
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTaskConfig(taskId))
                .thenReturn(mockTaskConfig);
            dbTaskMetaManagerMockedStatic.when(
                    () -> DbTaskMetaManager.updateTaskConfig(anyLong(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    // 验证参数
                    Long taskIdArg = invocation.getArgument(0);
                    String extractorConfigArg = invocation.getArgument(1);

                    // 解析extractorConfig，验证心跳设置是否正确
                    RdsExtractorConfig configObj = JSON.parseObject(extractorConfigArg, RdsExtractorConfig.class);
                    Assert.assertEquals(enableHeartbeat, configObj.isEnableDetectHeartbeat());
                    Assert.assertEquals(enableHeartbeat, configObj.isCreateHeartbeatTable());

                    return null;
                });

            // 执行测试
            ResultCode<?> result = replicaApiResource.enableHeartbeat(taskId, enableHeartbeat);

            // 验证结果
            Assert.assertNotNull(result);
            Assert.assertEquals(RplConstants.SUCCESS_CODE, result.getCode());
            Assert.assertEquals("success", result.getMsg());
            Assert.assertEquals(RplConstants.SUCCESS, result.getData());

            // 验证方法调用
            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.getTask(taskId), times(1));
            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.getTaskConfig(taskId), times(1));
            dbTaskMetaManagerMockedStatic.verify(
                () -> DbTaskMetaManager.updateTaskConfig(anyLong(), anyString(), any(), any(), any()), times(1));
        }
    }

    /**
     * 测试enableHeartbeat方法 - 任务类型不正确
     */
    @Test
    public void testEnableHeartbeat_WrongTaskType() {
        Long taskId = 1L;
        Boolean enableHeartbeat = true;

        // Mock RplTask with wrong type
        RplTask mockTask = Mockito.mock(RplTask.class);
        when(mockTask.getType()).thenReturn(ServiceType.FULL_COPY.name()); // 不是INC_COPY类型

        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(
            DbTaskMetaManager.class)) {
            // 设置DbTaskMetaManager的mock行为
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(mockTask);

            // 执行测试
            ResultCode<?> result = replicaApiResource.enableHeartbeat(taskId, enableHeartbeat);

            // 验证结果
            Assert.assertNotNull(result);
            Assert.assertEquals(RplConstants.FAILURE_CODE, result.getCode());
            Assert.assertEquals("only support inc copy task edit heartbeat", result.getMsg());
            Assert.assertEquals(RplConstants.FAILURE, result.getData());

            // 验证方法调用
            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.getTask(taskId), times(1));
            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.getTaskConfig(taskId), times(0));
        }
    }

    /**
     * 测试enableHeartbeat方法 - 任务配置不存在
     */
    @Test
    public void testEnableHeartbeat_TaskConfigNotFound() {
        Long taskId = 1L;
        Boolean enableHeartbeat = true;

        // Mock RplTask
        RplTask mockTask = Mockito.mock(RplTask.class);
        when(mockTask.getType()).thenReturn(ServiceType.INC_COPY.name());

        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(
            DbTaskMetaManager.class)) {
            // 设置DbTaskMetaManager的mock行为
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(mockTask);
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTaskConfig(taskId)).thenReturn(null);

            // 执行测试
            ResultCode<?> result = replicaApiResource.enableHeartbeat(taskId, enableHeartbeat);

            // 验证结果
            Assert.assertNotNull(result);
            Assert.assertEquals(RplConstants.FAILURE_CODE, result.getCode());
            Assert.assertEquals("do not exist replica task with this taskId", result.getMsg());
            Assert.assertEquals(RplConstants.FAILURE, result.getData());

            // 验证方法调用
            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.getTask(taskId), times(1));
            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.getTaskConfig(taskId), times(1));
            dbTaskMetaManagerMockedStatic.verify(
                () -> DbTaskMetaManager.updateTaskConfig(anyLong(), anyString(), any(), any(), any()), times(0));
        }
    }

    /**
     * 测试enableHeartbeat方法 - 禁用心跳
     */
    @Test
    public void testEnableHeartbeat_DisableHeartbeat() {
        Long taskId = 1L;
        Boolean enableHeartbeat = false;

        // Mock RplTask
        RplTask mockTask = Mockito.mock(RplTask.class);
        when(mockTask.getType()).thenReturn(ServiceType.INC_COPY.name());

        // Mock RplTaskConfig
        RplTaskConfig mockTaskConfig = Mockito.mock(RplTaskConfig.class);
        when(mockTaskConfig.getTaskId()).thenReturn(taskId);
        when(mockTaskConfig.getExtractorConfig()).thenReturn(
            "{\"enableDetectHeartbeat\":true,\"createHeartbeatTable\":true}");

        try (MockedStatic<DbTaskMetaManager> dbTaskMetaManagerMockedStatic = Mockito.mockStatic(
            DbTaskMetaManager.class)) {
            // 设置DbTaskMetaManager的mock行为
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTask(taskId)).thenReturn(mockTask);
            dbTaskMetaManagerMockedStatic.when(() -> DbTaskMetaManager.getTaskConfig(taskId))
                .thenReturn(mockTaskConfig);
            dbTaskMetaManagerMockedStatic.when(
                    () -> DbTaskMetaManager.updateTaskConfig(anyLong(), anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    // 验证参数
                    Long taskIdArg = invocation.getArgument(0);
                    String extractorConfigArg = invocation.getArgument(1);

                    // 解析extractorConfig，验证心跳设置是否正确
                    RdsExtractorConfig configObj = JSON.parseObject(extractorConfigArg, RdsExtractorConfig.class);
                    Assert.assertEquals(enableHeartbeat, configObj.isEnableDetectHeartbeat());
                    Assert.assertEquals(enableHeartbeat, configObj.isCreateHeartbeatTable());

                    return null;
                });

            // 执行测试
            ResultCode<?> result = replicaApiResource.enableHeartbeat(taskId, enableHeartbeat);

            // 验证结果
            Assert.assertNotNull(result);
            Assert.assertEquals(RplConstants.SUCCESS_CODE, result.getCode());
            Assert.assertEquals("success", result.getMsg());
            Assert.assertEquals(RplConstants.SUCCESS, result.getData());

            // 验证方法调用
            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.getTask(taskId), times(1));
            dbTaskMetaManagerMockedStatic.verify(() -> DbTaskMetaManager.getTaskConfig(taskId), times(1));
            dbTaskMetaManagerMockedStatic.verify(
                () -> DbTaskMetaManager.updateTaskConfig(anyLong(), anyString(), any(), any(), any()), times(1));
        }
    }
}