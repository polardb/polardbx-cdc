/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LogFileManagerTest extends BaseTest {

    private LogFileManager logFileManager;
    private String testBinlogRootPath;
    private final String testGroupName = "testGroup";
    private final String testStreamName = "testStream";

    @Before
    public void setUp() {
        logFileManager = new LogFileManager();
        logFileManager.setGroupName(testGroupName);
        logFileManager.setStreamName(testStreamName);

        // 创建临时目录用于测试
        try {
            Path tempDir = Files.createTempDirectory("logFileManagerTest");
            FileUtils.cleanDirectory(tempDir.toFile());

            testBinlogRootPath = tempDir.toString();
            logFileManager.setBinlogRootPath(testBinlogRootPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory for testing", e);
        }
    }

    @Test
    public void testClean() throws IOException {
        // 准备测试数据，在目标路径下创建一些文件
        String fullPath = testBinlogRootPath + "/" + testGroupName + "/" + testStreamName;
        File testDir = new File(fullPath);
        testDir.mkdirs();

        // 创建测试文件
        File testFile1 = new File(testDir, "binlog.000001");
        File testFile2 = new File(testDir, "binlog.000002");
        FileUtils.writeStringToFile(testFile1, "test content 1", "UTF-8");
        FileUtils.writeStringToFile(testFile2, "test content 2", "UTF-8");

        // 确认文件存在
        Assert.assertTrue(testFile1.exists());
        Assert.assertTrue(testFile2.exists());

        // 执行clean操作
        logFileManager.clean();

        // 验证文件已被删除
        Assert.assertFalse("Directory should be deleted after clean", testDir.exists());
    }

    @Test
    public void testCleanWithEmptyDirectory() throws IOException {
        // 准备测试数据，只创建目录不创建文件
        String fullPath = testBinlogRootPath + "/" + testGroupName + "/" + testStreamName;
        File testDir = new File(fullPath);
        testDir.mkdirs();

        // 确认目录存在但为空
        Assert.assertTrue(testDir.exists());
        Assert.assertTrue(testDir.isDirectory());
        Assert.assertEquals(0, testDir.listFiles().length);

        // 执行clean操作
        logFileManager.clean();

        // 验证空目录已被删除
        Assert.assertFalse("Empty directory should be deleted after clean", testDir.exists());
    }

    @Test
    public void testCleanWithNonExistentDirectory() throws IOException {
        // 不创建任何目录，直接尝试清理
        // 执行clean操作不应该抛出异常
        logFileManager.clean();

        // 验证操作完成（无异常抛出即为成功）
        Assert.assertTrue(true);
    }
}
