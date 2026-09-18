/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.lock;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class LogFileLockManagerCollectionTest extends BaseTest {

    private LogFileLockManagerCollection logFileLockManagerCollection;

    @Mock
    private LogFileLockManager logFileLockManager1;

    @Mock
    private LogFileLockManager logFileLockManager2;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        logFileLockManagerCollection = new LogFileLockManagerCollection();
    }

    @Test
    public void testAddAndGet() {
        // 测试添加和获取功能
        logFileLockManagerCollection.add("stream1", logFileLockManager1);
        logFileLockManagerCollection.add("stream2", logFileLockManager2);

        assertEquals(logFileLockManager1, logFileLockManagerCollection.get("stream1"));
        assertEquals(logFileLockManager2, logFileLockManagerCollection.get("stream2"));
        assertNull(logFileLockManagerCollection.get("nonexistent"));
    }

    @Test
    public void testStart() {
        // 准备测试数据
        Map<String, LogFileLockManager> managers = new HashMap<>();
        managers.put("stream1", logFileLockManager1);
        managers.put("stream2", logFileLockManager2);

        // 模拟init方法调用
        doNothing().when(logFileLockManager1).init();
        doNothing().when(logFileLockManager2).init();

        // 执行测试
        logFileLockManagerCollection.start(managers);

        // 验证结果
        assertEquals(logFileLockManager1, logFileLockManagerCollection.get("stream1"));
        assertEquals(logFileLockManager2, logFileLockManagerCollection.get("stream2"));
        verify(logFileLockManager1, times(1)).init();
        verify(logFileLockManager2, times(1)).init();
    }

    @Test
    public void testStop() {
        // 先添加一些manager
        logFileLockManagerCollection.add("stream1", logFileLockManager1);
        logFileLockManagerCollection.add("stream2", logFileLockManager2);

        // 执行stop操作
        logFileLockManagerCollection.stop("stream1");

        // 验证结果
        assertNull(logFileLockManagerCollection.get("stream1"));
        assertEquals(logFileLockManager2, logFileLockManagerCollection.get("stream2"));
    }
}
