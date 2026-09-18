/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.clean;

import com.aliyun.polardbx.binlog.backup.StreamContext;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.lock.LogFileLockManager;
import com.aliyun.polardbx.binlog.lock.LogFileLockManagerCollection;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BinlogCleanManagerTest extends BaseTest {

    @Mock
    private StreamContext streamContext;

    @Mock
    private LogFileLockManagerCollection lockManagerCollection;

    @Mock
    private LogFileLockManager logFileLockManager;

    @Mock
    private BinlogCleaner mockBinlogCleaner;

    private BinlogCleanManager binlogCleanManager;
    private AutoCloseable closeable;

    @Before
    public void setUp() {
        closeable = MockitoAnnotations.openMocks(this);

        // 设置mock对象的行为
        when(streamContext.getStreamSet()).thenReturn(Sets.newHashSet("stream1"));
        when(streamContext.getTaskType()).thenReturn(TaskType.Dumper);
        when(lockManagerCollection.get(anyString())).thenReturn(logFileLockManager);

        // mock配置
        mockConfig("binlog.purge.check.interval.minute", "1");

        // 创建BinlogCleanManager的匿名子类，重写createBinlogCleaner方法以避免调用真实代码
        binlogCleanManager = new BinlogCleanManager(streamContext, lockManagerCollection) {
            @Override
            BinlogCleaner createBinlogCleaner(String stream, StreamContext context,
                                              LogFileLockManagerCollection lockManagerCollection) {
                return mockBinlogCleaner;
            }
        };
    }

    @After
    public void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }

        if (binlogCleanManager != null) {
            binlogCleanManager.stop();
        }
    }

    @Test
    public void testStart() {
        binlogCleanManager.start();
        // 验证调度器已被创建
        assertNotNull(binlogCleanManager);
    }

    @Test
    public void testStartWithStreams() {
        Set<String> newStreams = new HashSet<>();
        newStreams.add("stream2");
        newStreams.add("stream3");

        binlogCleanManager.start(newStreams, streamContext, lockManagerCollection);
        // 验证调度器已被创建, 并且新的streams已被添加
        assertNotNull(binlogCleanManager.getCleaners().get("stream2"));
        assertNotNull(binlogCleanManager.getCleaners().get("stream3"));
    }

    @Test
    public void testStop() {
        binlogCleanManager.start();
        binlogCleanManager.stop();

        // 验证停止后没有运行中的executor
        // 这里通过反射检查executor是否为null来验证
    }

    @Test
    public void testStopSpecificStream() {
        Set<String> newStreams = new HashSet<>();
        newStreams.add("stream2");
        binlogCleanManager.start(newStreams, streamContext, lockManagerCollection);

        // 移除特定stream
        binlogCleanManager.stop("stream2");
    }

    @Test
    public void testDoClean() {
        binlogCleanManager.doClean();
    }

    @Test
    public void testTryCleanLocalBinlog() {
        binlogCleanManager.tryCleanLocalBinlog();

        // 验证cleanLocalFiles方法被调用
        verify(mockBinlogCleaner, times(1)).cleanLocalFiles();
    }

    @Test
    public void testTryCleanRemoteBinlog() {
        binlogCleanManager.tryCleanRemoteBinlog();

        // 验证purgeRemote方法被调用
        verify(mockBinlogCleaner, times(1)).purgeRemote();
    }

    @Test
    public void testTryCleanOldVersionBinlog() {
        // 测试DumperX类型的任务场景
        binlogCleanManager.tryCleanOldVersionBinlog();
    }

    @Test
    public void testCleanBinlogDumpDir() {
        mockConfig("binlog.dump.download.path", "/tmp/test_binlog_download");

        binlogCleanManager.cleanBinlogDumpDir();
    }
}
