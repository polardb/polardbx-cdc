/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

public class RocksDBUtilTest extends BaseTest {

    @Test
    public void testClearTempLibFiles_DirectoryExistsButEmpty() throws IOException {
        // 创建临时目录
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test_empty_" + UUID.randomUUID().toString());
        tempDir.mkdirs();

        try {
            // 执行测试
            RocksDBUtil.clearTempLibFiles();

            // 验证目录存在但没有文件被删除
            Assert.assertTrue(tempDir.exists());
        } finally {
            // 清理测试目录
            FileUtils.deleteQuietly(tempDir);
        }
    }

    @Test
    public void testClearTempLibFiles_DirectoryWithMatchingFiles() throws IOException {
        // 创建临时目录
        String path = System.getProperty("java.io.tmpdir");
        File tempDir = new File(path);

        // 创建匹配的文件
        File matchingFile1 = new File(tempDir, "librocksdbjni123.so");
        File matchingFile2 = new File(tempDir, "librocksdbjni456.so");
        matchingFile1.createNewFile();
        matchingFile2.createNewFile();

        // 创建不匹配的文件
        File nonMatchingFile1 = new File(tempDir, "librocksdbjni123.txt");
        File nonMatchingFile2 = new File(tempDir, "otherfile.so");
        nonMatchingFile1.createNewFile();
        nonMatchingFile2.createNewFile();

        try (MockedStatic<FileUtils> fileUtilsMockedStatic = mockStatic(FileUtils.class)) {
            // 执行测试
            RocksDBUtil.clearTempLibFiles();

            // 验证FileUtils.deleteQuietly被调用了2次（匹配的文件数量）
            fileUtilsMockedStatic.verify(() -> FileUtils.deleteQuietly(matchingFile1), times(1));
            fileUtilsMockedStatic.verify(() -> FileUtils.deleteQuietly(matchingFile2), times(1));
            fileUtilsMockedStatic.verify(() -> FileUtils.deleteQuietly(nonMatchingFile1), times(0));
            fileUtilsMockedStatic.verify(() -> FileUtils.deleteQuietly(nonMatchingFile2), times(0));

        } finally {
            // 清理测试文件和目录
            FileUtils.deleteQuietly(matchingFile1);
            FileUtils.deleteQuietly(matchingFile2);
            FileUtils.deleteQuietly(nonMatchingFile1);
            FileUtils.deleteQuietly(nonMatchingFile2);
        }
    }

    @Test
    public void testClearTempLibFiles_DirectoryWithNoMatchingFiles() throws IOException {
        // 创建临时目录
        String path = System.getProperty("java.io.tmpdir");
        File tempDir = new File(path);

        // 创建不匹配的文件
        File nonMatchingFile1 = new File(tempDir, "librocksdbjni123.txt");
        File nonMatchingFile2 = new File(tempDir, "otherfile.so");
        nonMatchingFile1.createNewFile();
        nonMatchingFile2.createNewFile();

        try (MockedStatic<FileUtils> fileUtilsMockedStatic = mockStatic(FileUtils.class)) {
            // 执行测试
            RocksDBUtil.clearTempLibFiles();

            // 验证FileUtils.deleteQuietly没有被调用
            fileUtilsMockedStatic.verify(() -> FileUtils.deleteQuietly(any(File.class)), times(0));

        } finally {
            // 清理测试文件和目录
            FileUtils.deleteQuietly(nonMatchingFile1);
            FileUtils.deleteQuietly(nonMatchingFile2);
        }
    }

    @Test
    public void testROCKSDB_LIB_PATH() {
        // 验证ROCKSDB_LIB_PATH常量被正确初始化
        Assert.assertEquals(System.getProperty("java.io.tmpdir"), RocksDBUtil.ROCKSDB_LIB_PATH);
    }
}