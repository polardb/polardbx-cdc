/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogDumperInfoMapper;
import com.aliyun.polardbx.binlog.dao.TaskInfoMapper;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GmsTimeUtilTest extends BaseTest {

    @Mock
    private JdbcTemplate metaJdbcTemplate;

    @Mock
    private TaskInfoMapper taskInfoMapper;

    @Mock
    private BinlogDumperInfoMapper dumperInfoMapper;

    @Before
    public void setUp() {
        metaJdbcTemplate = mock(JdbcTemplate.class);
        taskInfoMapper = mock(TaskInfoMapper.class);
        dumperInfoMapper = mock(BinlogDumperInfoMapper.class);
    }

    @Test
    public void testGetCurrentTimeMillis_Success() {
        Long expectedTime = System.currentTimeMillis();
        when(metaJdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(expectedTime);

        try (MockedStatic<SpringContextHolder> mockedSpringContextHolder = mockStatic(SpringContextHolder.class)) {
            mockedSpringContextHolder.when(() -> SpringContextHolder.getObject("metaJdbcTemplate"))
                .thenReturn(metaJdbcTemplate);

            long actualTime = GmsTimeUtil.getCurrentTimeMillis();
            Assert.assertEquals(expectedTime.longValue(), actualTime);
            verify(metaJdbcTemplate, times(1))
                .queryForObject("SELECT ROUND(UNIX_TIMESTAMP(CURRENT_TIMESTAMP(4)) * 1000)", Long.class);
        }
    }

    @Test(expected = RuntimeException.class)
    public void testGetCurrentTimeMillis_NullResult() {
        when(metaJdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(null);

        try (MockedStatic<SpringContextHolder> mockedSpringContextHolder = mockStatic(SpringContextHolder.class)) {
            mockedSpringContextHolder.when(() -> SpringContextHolder.getObject("metaJdbcTemplate"))
                .thenReturn(metaJdbcTemplate);

            GmsTimeUtil.getCurrentTimeMillis();
        }
    }

    @Test(expected = RuntimeException.class)
    public void testGetCurrentTimeMillis_Exception() {
        when(metaJdbcTemplate.queryForObject(anyString(), eq(Long.class)))
            .thenThrow(new RuntimeException("Database error"));

        try (MockedStatic<SpringContextHolder> mockedSpringContextHolder = mockStatic(SpringContextHolder.class)) {
            mockedSpringContextHolder.when(() -> SpringContextHolder.getObject("metaJdbcTemplate"))
                .thenReturn(metaJdbcTemplate);

            GmsTimeUtil.getCurrentTimeMillis();
        }
    }

    @Test
    public void testGetCurrentDateTime() {
        Long expectedTime = System.currentTimeMillis();
        when(metaJdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(expectedTime);

        try (MockedStatic<SpringContextHolder> mockedSpringContextHolder = mockStatic(SpringContextHolder.class)) {
            mockedSpringContextHolder.when(() -> SpringContextHolder.getObject("metaJdbcTemplate"))
                .thenReturn(metaJdbcTemplate);

            DateTime dateTime = GmsTimeUtil.getCurrentDateTime();
            Assert.assertNotNull(dateTime);
            Assert.assertEquals(expectedTime.longValue(), dateTime.getMillis());
        }
    }

    @Test
    public void testGetHeartbeatInterval_ForTask() {
        String taskType = TaskType.Relay.name();
        String clusterId = "test-cluster";
        String taskName = "test-task";
        Long expectedInterval = 1000L;

        when(taskInfoMapper.getHeartbeatInterval(taskName, clusterId)).thenReturn(expectedInterval);

        try (MockedStatic<SpringContextHolder> mockedSpringContextHolder = mockStatic(SpringContextHolder.class)) {
            mockedSpringContextHolder.when(() -> SpringContextHolder.getObject(TaskInfoMapper.class))
                .thenReturn(taskInfoMapper);

            long actualInterval = GmsTimeUtil.getHeartbeatInterval(taskType, clusterId, taskName);
            Assert.assertEquals(expectedInterval.longValue(), actualInterval);
            verify(taskInfoMapper, times(1)).getHeartbeatInterval(taskName, clusterId);
        }
    }

    @Test
    public void testGetHeartbeatInterval_ForDumper() {
        String taskType = TaskType.Dumper.name();
        String clusterId = "test-cluster";
        String taskName = "test-dumper";
        Long expectedInterval = 2000L;

        when(dumperInfoMapper.getHeartbeatInterval(taskName, clusterId)).thenReturn(expectedInterval);

        try (MockedStatic<SpringContextHolder> mockedSpringContextHolder = mockStatic(SpringContextHolder.class)) {
            mockedSpringContextHolder.when(() -> SpringContextHolder.getObject(BinlogDumperInfoMapper.class))
                .thenReturn(dumperInfoMapper);

            long actualInterval = GmsTimeUtil.getHeartbeatInterval(taskType, clusterId, taskName);
            Assert.assertEquals(expectedInterval.longValue(), actualInterval);
            verify(dumperInfoMapper, times(1)).getHeartbeatInterval(taskName, clusterId);
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetHeartbeatInterval_IllegalTaskType() {
        String taskType = "InvalidType";
        String clusterId = "test-cluster";
        String taskName = "test-task";

        GmsTimeUtil.getHeartbeatInterval(taskType, clusterId, taskName);
    }
}