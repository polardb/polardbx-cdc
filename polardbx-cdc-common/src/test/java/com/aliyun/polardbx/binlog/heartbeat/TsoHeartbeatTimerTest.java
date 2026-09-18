/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.heartbeat;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.DAEMON_TSO_HEARTBEAT_SUSPEND_ENABLED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TsoHeartbeatTimerTest extends BaseTest {

    private TsoHeartbeatTimer tsoHeartbeatTimer;

    private JdbcTemplate jdbcTemplate;

    private TransactionTemplate transactionTemplate;

    @Before
    public void setUp() throws Exception {
        jdbcTemplate = mock(JdbcTemplate.class);
        transactionTemplate = mock(TransactionTemplate.class);
        registerSpringObject("polarxJdbcTemplate", jdbcTemplate);
        registerSpringObject("polarxTransactionTemplate", transactionTemplate);

        tsoHeartbeatTimer = new TsoHeartbeatTimer();
    }

    @After
    public void tearDown() throws Exception {
        unregisterSpringObject("polarxJdbcTemplate", jdbcTemplate);
        unregisterSpringObject("polarxTransactionTemplate", transactionTemplate);
    }

    @Test
    public void testSendTsoHeartBeat_WhenSuspendEnabled_ShouldNotExecute() {
        // Given: 心跳暂停功能启用
        mockConfig(DAEMON_TSO_HEARTBEAT_SUSPEND_ENABLED, "true");

        // When: 调用 sendTsoHeartBeat 方法
        tsoHeartbeatTimer.sendTsoHeartBeat();

        // Then: 验证 transactionTemplate.execute 没有被调用
        verify(transactionTemplate, never()).execute(any());
    }

    @Test
    public void testSendTsoHeartBeat_WhenSuspendDisabled_ShouldExecute() {
        // Given: 心跳暂停功能禁用
        mockConfig(DAEMON_TSO_HEARTBEAT_SUSPEND_ENABLED, "false");

        // 模拟 transactionTemplate.execute 的行为
        doAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            callback.doInTransaction(null); // 执行回调
            return null;
        }).when(transactionTemplate).execute(any());

        // When: 调用 sendTsoHeartBeat 方法
        tsoHeartbeatTimer.sendTsoHeartBeat();

        // Then: 验证执行了必要的操作
        verify(transactionTemplate, times(1)).execute(any());
        verify(jdbcTemplate, times(1)).execute(TsoHeartbeatTimer.TRANSACTION_POLICY);
        verify(jdbcTemplate, times(1)).execute((String) argThat(argument ->
            argument != null && ((String) argument).startsWith("replace into `__cdc__`.`__cdc_heartbeat__`")));
    }

    @Test
    public void testCheckIfTableExists_TableExists() {
        // 准备测试数据
        List<Map<String, Object>> resultList = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("table_name", "__cdc_heartbeat__");
        resultList.add(row);

        // 设置mock行为
        when(jdbcTemplate.queryForList(anyString())).thenReturn(resultList);

        // 执行测试方法
        boolean result = tsoHeartbeatTimer.checkIfTableExists("__cdc__", "__cdc_heartbeat__");

        // 验证结果
        Assert.assertTrue(result);
        verify(jdbcTemplate).queryForList(anyString());
    }

    @Test
    public void testCheckIfTableExists_TableNotExists() {
        // 准备测试数据 - 空结果列表
        List<Map<String, Object>> resultList = new ArrayList<>();

        // 设置mock行为
        when(jdbcTemplate.queryForList(anyString())).thenReturn(resultList);

        // 执行测试方法
        boolean result = tsoHeartbeatTimer.checkIfTableExists("__cdc__", "__cdc_heartbeat__");

        // 验证结果
        Assert.assertFalse(result);
        verify(jdbcTemplate).queryForList(anyString());
    }
}
