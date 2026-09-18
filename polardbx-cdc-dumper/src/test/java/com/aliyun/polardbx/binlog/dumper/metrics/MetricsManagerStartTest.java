/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.metrics;

import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.dumper.dump.constants.EnumClientType;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MetricsManagerStartTest extends BaseTest {

    private MetricsManager metricsManager;

    @Before
    public void setUp() {
        // 创建一个真实的MetricsManager实例，不使用mock
        metricsManager = new MetricsManager(1L, "test-task", TaskType.Dumper);
    }

    @After
    public void tearDown() {
        if (metricsManager != null) {
            // 停止metrics manager以清理资源
            metricsManager.stop();
        }
    }

    @Test
    public void testStartReportConsumerExists() throws Exception {
        // 通过反射修改REPORT_INTERVAL为1秒，避免等待太久
        Field reportIntervalField = MetricsManager.class.getDeclaredField("REPORT_INTERVAL");
        reportIntervalField.setAccessible(true);

        // 移除final修饰符
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        modifiersField.setAccessible(true);
        modifiersField.setInt(reportIntervalField, reportIntervalField.getModifiers() & ~Modifier.FINAL);

        // 设置REPORT_INTERVAL为1秒
        reportIntervalField.setLong(null, TimeUnit.SECONDS.toMillis(1));

        // 通过反射将consumerExists设为true
        Field consumerExistsField = MetricsManager.class.getDeclaredField("consumerExists");
        consumerExistsField.setAccessible(true);
        consumerExistsField.setBoolean(metricsManager, true);

        // 启动report consumer exists功能
        metricsManager.startReportConsumerExists();

        // 等待一段时间让调度任务执行
        Thread.sleep(1500);

        // 验证consumerExists被设置为false（说明任务已执行）
        assertFalse("consumerExists should be false after task execution",
            consumerExistsField.getBoolean(metricsManager));
    }
}