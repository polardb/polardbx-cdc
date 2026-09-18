/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.restore;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.extern.slf4j.Slf4j;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BinlogRestoreManager的单元测试类
 */
@Slf4j
public class BinlogRestoreManagerTest extends BaseTest {

    private RemoteBinlogProxy remoteBinlogProxy;

    private MockedStatic<RemoteBinlogProxy> remoteBinlogProxyStatic;

    private static final String groupName = "testGroup";
    private static final String streamName = "testStream";
    private static final String clusterId = "testCluster";

    @Before
    public void setUp() {
        remoteBinlogProxy = mock(RemoteBinlogProxy.class);
        remoteBinlogProxyStatic = mockStatic(RemoteBinlogProxy.class);
        remoteBinlogProxyStatic.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);

        mockConfig(ConfigKeys.CLUSTER_ID, clusterId);
        mockConfig(ConfigKeys.BINLOG_BACKUP_DOWNLOAD_LAST_FILE_COUNT, "3");
    }

    @After
    public void after() {
        if (remoteBinlogProxyStatic != null) {
            remoteBinlogProxyStatic.close();
        }
    }

    @Test
    public void testTryRestore_FirstCall_Success() {
        // 测试第一次调用tryRestore方法，应该执行restore逻辑
        when(remoteBinlogProxy.isBackupOn()).thenReturn(false);

        BinlogRestoreManager manager = new BinlogRestoreManager(groupName, streamName, "/test/path");

        // 第一次调用
        manager.tryRestore();

        // 验证restore方法被调用了一次
        verify(remoteBinlogProxy, times(1)).isBackupOn();
    }

    @Test
    public void testTryRestore_MultipleCalls_OnlyExecuteOnce() {
        // 测试多次调用tryRestore方法，但只执行一次restore逻辑
        when(remoteBinlogProxy.isBackupOn()).thenReturn(false);

        BinlogRestoreManager manager = new BinlogRestoreManager(groupName, streamName, "/test/path");

        // 多次调用
        manager.tryRestore();
        manager.tryRestore();
        manager.tryRestore();

        // 验证restore方法只被调用了一次
        verify(remoteBinlogProxy, times(1)).isBackupOn();
    }

    @Test(expected = RuntimeException.class)
    public void testTryRestore_ExceptionOccurs_ResetFlag() {
        // 测试当restore过程中抛出异常时，重置restoreFlag标志位
        when(remoteBinlogProxy.isBackupOn()).thenThrow(new RuntimeException("Test exception"));

        BinlogRestoreManager manager = new BinlogRestoreManager(groupName, streamName, "/test/path");

        try {
            manager.tryRestore();
        } finally {
            // 验证即使发生异常，后续仍可以再次尝试restore
            manager.tryRestore(); // 这次应该能再次执行

            // 验证总共调用了两次isBackupOn方法
            verify(remoteBinlogProxy, times(2)).isBackupOn();
        }
    }
}
