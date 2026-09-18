/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.restore;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.LabEventManager;
import com.aliyun.polardbx.binlog.remote.RemoteBinlogProxy;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import com.aliyun.polardbx.binlog.util.LabEventType;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author zimian
 * @since 2025/8/22 18:00
 **/
@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class BinlogDownloaderRecoverTest extends BaseTest {
    private List<String> downloadFiles = new ArrayList<>();
    private BinlogDownloader binlogDownloader;

    @Before
    public void setUp() {
        downloadFiles = new ArrayList<>();
        binlogDownloader = new BinlogDownloader("test_group", "test_stream", "/tmp/binlogs", downloadFiles);
    }

    @Test
    public void testCheckDownload() {
        RemoteBinlogProxy remoteBinlogProxy = Mockito.mock(RemoteBinlogProxy.class);
        try (MockedStatic<LabEventManager> labEventManagerMockedStatic = Mockito.mockStatic(LabEventManager.class);
            MockedStatic<RemoteBinlogProxy> remoteBinlogProxyStatic = Mockito.mockStatic(RemoteBinlogProxy.class)) {
            remoteBinlogProxyStatic.when(RemoteBinlogProxy::getInstance).thenReturn(remoteBinlogProxy);
            Mockito.when(remoteBinlogProxy.isObjectsExistForPrefix(Mockito.anyString())).thenReturn(true);
            mockConfig(ConfigKeys.IS_LAB_ENV, "true");
            BinlogDownloader binlogDownloader = Mockito.mock(BinlogDownloader.class, InvocationOnMock::callRealMethod);
            downloadFiles.add("binlog.zm.0002311");
            binlogDownloader.setDownloadFiles(downloadFiles);
            binlogDownloader.setBinlogFullPath("/tmp");
            binlogDownloader.setGroup("group_global");
            binlogDownloader.setStream("stream_global");
            binlogDownloader.checkDownload();
            labEventManagerMockedStatic.verify(
                () -> LabEventManager.logEvent(LabEventType.FORCE_DOWNLOAD_BINLOG_CHECK, "binlog.zm.0002311"),
                Mockito.times(1));
        }
    }

    @Test
    public void testNeedForceDownloadWhenFlagIsFalseAndNoLocalFiles() {
        // 准备测试数据
        downloadFiles.add("binlog.000001");
        downloadFiles.add("binlog.000002");

        // 模拟配置返回值
        mockConfig(ConfigKeys.BINLOG_RESTORE_FORCE_DOWN_IF_LOCAL_FILE_ABSENT, "false");

        // 模拟BinlogFileUtil.listLocalBinlogFiles返回空列表
        try (MockedStatic<BinlogFileUtil> mockedBinlogFileUtil = Mockito.mockStatic(BinlogFileUtil.class)) {
            mockedBinlogFileUtil.when(
                    () -> BinlogFileUtil.listLocalBinlogFiles("/tmp/binlogs", "test_group", "test_stream"))
                .thenReturn(new ArrayList<>());

            // 执行测试
            boolean result = binlogDownloader.needForceDownload();

            // 验证结果
            Assert.assertFalse(result);
        }
    }

    @Test
    public void testNeedForceDownloadWhenFlagIsTrueAndNoLocalFiles() {
        // 准备测试数据
        downloadFiles.add("binlog.000001");
        downloadFiles.add("binlog.000002");

        // 模拟配置返回值
        mockConfig(ConfigKeys.BINLOG_RESTORE_FORCE_DOWN_IF_LOCAL_FILE_ABSENT, "true");

        // 模拟BinlogFileUtil.listLocalBinlogFiles返回空列表
        try (MockedStatic<BinlogFileUtil> mockedBinlogFileUtil = Mockito.mockStatic(BinlogFileUtil.class)) {
            List<File> localFiles = new ArrayList<>();
            mockedBinlogFileUtil.when(
                    () -> BinlogFileUtil.listLocalBinlogFiles("/tmp/binlogs", "test_group", "test_stream"))
                .thenReturn(localFiles);

            // 执行测试
            boolean result = binlogDownloader.needForceDownload();

            // 验证结果
            Assert.assertTrue(result);
        }
    }

    @Test
    public void testNeedForceDownloadWhenFlagIsTrueAndHasSomeLocalFiles() {
        // 准备测试数据
        downloadFiles.add("binlog.000001");
        downloadFiles.add("binlog.000002");
        downloadFiles.add("binlog.000003");

        // 模拟配置返回值
        mockConfig(ConfigKeys.BINLOG_RESTORE_FORCE_DOWN_IF_LOCAL_FILE_ABSENT, "true");

        // 模拟BinlogFileUtil.listLocalBinlogFiles返回部分文件
        try (MockedStatic<BinlogFileUtil> mockedBinlogFileUtil = Mockito.mockStatic(BinlogFileUtil.class)) {
            List<File> localFiles = new ArrayList<>();
            File file1 = Mockito.mock(File.class);
            Mockito.when(file1.getName()).thenReturn("binlog.000001");
            localFiles.add(file1);

            mockedBinlogFileUtil.when(
                    () -> BinlogFileUtil.listLocalBinlogFiles("/tmp/binlogs", "test_group", "test_stream"))
                .thenReturn(localFiles);

            // 执行测试
            boolean result = binlogDownloader.needForceDownload();

            // 验证结果
            Assert.assertFalse(result);
        }
    }

    @Test
    public void testNeedForceDownloadWhenFlagIsTrueAndHasAllLocalFiles() {
        // 准备测试数据
        downloadFiles.add("binlog.000001");
        downloadFiles.add("binlog.000002");

        // 模拟配置返回值
        mockConfig(ConfigKeys.BINLOG_RESTORE_FORCE_DOWN_IF_LOCAL_FILE_ABSENT, "true");

        // 模拟BinlogFileUtil.listLocalBinlogFiles返回所有文件
        try (MockedStatic<BinlogFileUtil> mockedBinlogFileUtil = Mockito.mockStatic(BinlogFileUtil.class)) {
            List<File> localFiles = new ArrayList<>();
            File file1 = Mockito.mock(File.class);
            File file2 = Mockito.mock(File.class);
            Mockito.when(file1.getName()).thenReturn("binlog.000001");
            Mockito.when(file2.getName()).thenReturn("binlog.000002");
            localFiles.add(file1);
            localFiles.add(file2);

            mockedBinlogFileUtil.when(
                    () -> BinlogFileUtil.listLocalBinlogFiles("/tmp/binlogs", "test_group", "test_stream"))
                .thenReturn(localFiles);

            // 执行测试
            boolean result = binlogDownloader.needForceDownload();

            // 验证结果
            Assert.assertFalse(result);
        }
    }

    @Test
    public void testNeedForceDownloadWhenFlagIsFalseAndHasLocalFiles() {
        // 准备测试数据
        downloadFiles.add("binlog.000001");
        downloadFiles.add("binlog.000002");

        // 模拟配置返回值
        mockConfig(ConfigKeys.BINLOG_RESTORE_FORCE_DOWN_IF_LOCAL_FILE_ABSENT, "false");

        // 模拟BinlogFileUtil.listLocalBinlogFiles返回所有文件
        try (MockedStatic<BinlogFileUtil> mockedBinlogFileUtil = Mockito.mockStatic(BinlogFileUtil.class)) {
            List<File> localFiles = new ArrayList<>();
            File file1 = Mockito.mock(File.class);
            File file2 = Mockito.mock(File.class);
            Mockito.when(file1.getName()).thenReturn("binlog.000001");
            Mockito.when(file2.getName()).thenReturn("binlog.000002");
            localFiles.add(file1);
            localFiles.add(file2);

            mockedBinlogFileUtil.when(
                    () -> BinlogFileUtil.listLocalBinlogFiles("/tmp/binlogs", "test_group", "test_stream"))
                .thenReturn(localFiles);

            // 执行测试
            boolean result = binlogDownloader.needForceDownload();

            // 验证结果
            Assert.assertFalse(result);
        }
    }

    @Test
    public void testNeedForceDownloadWithEmptyDownloadFiles() {
        // 准备测试数据
        // downloadFiles为空

        // 模拟配置返回值
        mockConfig(ConfigKeys.BINLOG_RESTORE_FORCE_DOWN_IF_LOCAL_FILE_ABSENT, "true");

        // 模拟BinlogFileUtil.listLocalBinlogFiles返回空列表
        try (MockedStatic<BinlogFileUtil> mockedBinlogFileUtil = Mockito.mockStatic(BinlogFileUtil.class)) {
            mockedBinlogFileUtil.when(
                    () -> BinlogFileUtil.listLocalBinlogFiles("/tmp/binlogs", "test_group", "test_stream"))
                .thenReturn(new ArrayList<>());

            // 执行测试
            boolean result = binlogDownloader.needForceDownload();

            // 验证结果
            Assert.assertFalse(result);
        }
    }
}