/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class BinlogConsumerMonitorTest extends BaseTest {

    private JdbcTemplate polarxJdbcTemplate;

    private JdbcTemplate metaTemplate;

    private BinlogConsumerMonitor binlogConsumerMonitor;

    @Before
    public void setUp() throws NoSuchFieldException, IllegalAccessException {
        polarxJdbcTemplate = Mockito.mock(JdbcTemplate.class);
        metaTemplate = Mockito.mock(JdbcTemplate.class);
        registerSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
        registerSpringObject("metaJdbcTemplate", metaTemplate);

        binlogConsumerMonitor = new BinlogConsumerMonitor("1", "BINLOG", "ConsumerChecker",
            1000);
    }

    @After
    public void after() {
        unregisterSpringObject("polarxJdbcTemplate", polarxJdbcTemplate);
        unregisterSpringObject("metaJdbcTemplate", metaTemplate);
    }

    @Test
    public void testCheckIfExistsConsumer_WithActiveConsumers() {
        Map<String, Object> processListRow = new HashMap<>();
        processListRow.put("Command", "Binlog Dump");
        when(polarxJdbcTemplate.queryForList(anyString())).thenReturn(
            java.util.Collections.singletonList(processListRow));
        when(metaTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1);
        String latestConsumeTime = binlogConsumerMonitor.checkIfExistsConsumer();
        assertNotNull(latestConsumeTime);
    }

    @Test
    public void testCheckIfExistsConsumer_WithoutActiveConsumers() {
        when(polarxJdbcTemplate.queryForList(anyString())).thenReturn(java.util.Collections.emptyList());
        when(metaTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);
        binlogConsumerMonitor.checkIfExistsConsumer();
        String latestConsumeTime = binlogConsumerMonitor.checkIfExistsConsumer();
        assertNull(latestConsumeTime);
    }

    @Test
    public void testCheckIfExistsConsumer_ColumnarTableMappingExists() {
        Map<String, Object> processListRow = new HashMap<>();
        processListRow.put("Command", "Other");
        when(polarxJdbcTemplate.queryForList(anyString())).thenReturn(
            java.util.Collections.singletonList(processListRow));
        when(metaTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(1, 2);
        String latestConsumeTime = binlogConsumerMonitor.checkIfExistsConsumer();
        assertNotNull(latestConsumeTime);
    }
}
