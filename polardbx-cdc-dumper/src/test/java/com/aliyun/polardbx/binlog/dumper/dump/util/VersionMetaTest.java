/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.util;

import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.mockStatic;

public class VersionMetaTest extends BaseTest {

    private static final long TEST_VERSION = 100L;
    private static final long TEST_SUB_VERSION = 1L;
    private static final String TEST_ROOT_PATH = "/tmp/test/binlog";
    private static final String VERSION_META_FILE_PATH = TEST_ROOT_PATH + File.separator + "VERSION_META";

    @Before
    public void setUp() {
        // 初始化测试数据
        mockConfig("binlogx.dir.path.prefix", "/tmp/test/binlog");
    }

    @Test
    public void testBuilderAndGetters() {
        Set<String> streamSet = new HashSet<>();
        streamSet.add("stream1");
        streamSet.add("stream2");

        VersionMeta versionMeta = VersionMeta.builder()
            .version(TEST_VERSION)
            .subVersion(TEST_SUB_VERSION)
            .streamSet(streamSet)
            .build();

        Assert.assertEquals(TEST_VERSION, versionMeta.getVersion().longValue());
        Assert.assertEquals(TEST_SUB_VERSION, versionMeta.getSubVersion().longValue());
        Assert.assertEquals(streamSet, versionMeta.getStreamSet());
    }

    @Test
    public void testUpdate() throws IOException {
        try (MockedStatic<BinlogFileUtil> mockedBinlogFileUtil = mockStatic(BinlogFileUtil.class)) {
            mockedBinlogFileUtil.when(() -> BinlogFileUtil.getRootPath(TaskType.DumperX, TEST_VERSION))
                .thenReturn(TEST_ROOT_PATH);

            Set<String> streamSet = new HashSet<>();
            streamSet.add("stream1");
            streamSet.add("stream2");

            VersionMeta versionMeta = VersionMeta.builder()
                .version(TEST_VERSION)
                .subVersion(TEST_SUB_VERSION)
                .streamSet(streamSet)
                .build();

            // 执行更新操作
            versionMeta.update();

            // 验证文件内容
            File versionMetaFile = new File(VERSION_META_FILE_PATH);
            Assert.assertTrue(versionMetaFile.exists());

            String content = FileUtils.readFileToString(versionMetaFile, StandardCharsets.UTF_8);
            Assert.assertTrue(content.contains("\"version\":" + TEST_VERSION));
            Assert.assertTrue(content.contains("\"subVersion\":" + TEST_SUB_VERSION));
            Assert.assertTrue(content.contains("\"streamSet\":["));
            Assert.assertTrue(content.contains("stream1"));
            Assert.assertTrue(content.contains("stream2"));

            // 清理测试文件
            FileUtils.deleteQuietly(versionMetaFile);
        }
    }

    @Test
    public void testQuery() {
        try (MockedStatic<BinlogFileUtil> mockedBinlogFileUtil = mockStatic(BinlogFileUtil.class)) {
            mockedBinlogFileUtil.when(() -> BinlogFileUtil.getRootPath(TaskType.DumperX, TEST_VERSION))
                .thenReturn(TEST_ROOT_PATH);

            // 准备测试文件
            Set<String> streamSet = new HashSet<>();
            streamSet.add("stream1");
            streamSet.add("stream2");

            VersionMeta expectedVersionMeta = VersionMeta.builder()
                .version(TEST_VERSION)
                .subVersion(TEST_SUB_VERSION)
                .streamSet(streamSet)
                .build();

            String jsonString = com.alibaba.fastjson.JSONObject.toJSONString(expectedVersionMeta);

            File versionMetaFile = new File(VERSION_META_FILE_PATH);
            versionMetaFile.getParentFile().mkdirs();
            try {
                FileUtils.writeStringToFile(versionMetaFile, jsonString, StandardCharsets.UTF_8);

                // 执行查询操作
                VersionMeta actualVersionMeta = VersionMeta.query(TEST_VERSION);

                // 验证结果
                Assert.assertNotNull(actualVersionMeta);
                Assert.assertEquals(expectedVersionMeta.getVersion(), actualVersionMeta.getVersion());
                Assert.assertEquals(expectedVersionMeta.getSubVersion(), actualVersionMeta.getSubVersion());
                Assert.assertEquals(expectedVersionMeta.getStreamSet(), actualVersionMeta.getStreamSet());
            } catch (IOException e) {
                Assert.fail("Failed to prepare test file: " + e.getMessage());
            } finally {
                // 清理测试文件
                FileUtils.deleteQuietly(versionMetaFile);
            }
        }
    }

    @Test
    public void testQueryNonExistentFile() {
        try (MockedStatic<BinlogFileUtil> mockedBinlogFileUtil = mockStatic(BinlogFileUtil.class)) {
            mockedBinlogFileUtil.when(() -> BinlogFileUtil.getRootPath(TaskType.DumperX, TEST_VERSION))
                .thenReturn(TEST_ROOT_PATH);

            // 确保文件不存在
            File versionMetaFile = new File(VERSION_META_FILE_PATH);
            FileUtils.deleteQuietly(versionMetaFile);

            // 执行查询操作
            VersionMeta versionMeta = VersionMeta.query(TEST_VERSION);

            // 验证结果
            Assert.assertNull(versionMeta);
        }
    }

    @Test
    public void testQueryEmptyFile() {
        try (MockedStatic<BinlogFileUtil> mockedBinlogFileUtil = mockStatic(BinlogFileUtil.class)) {
            mockedBinlogFileUtil.when(() -> BinlogFileUtil.getRootPath(TaskType.DumperX, TEST_VERSION))
                .thenReturn(TEST_ROOT_PATH);

            // 准备空文件
            File versionMetaFile = new File(VERSION_META_FILE_PATH);
            versionMetaFile.getParentFile().mkdirs();
            try {
                FileUtils.writeStringToFile(versionMetaFile, "", StandardCharsets.UTF_8);

                // 执行查询操作
                VersionMeta versionMeta = VersionMeta.query(TEST_VERSION);

                // 验证结果
                Assert.assertNull(versionMeta);
            } catch (IOException e) {
                Assert.fail("Failed to prepare test file: " + e.getMessage());
            } finally {
                // 清理测试文件
                FileUtils.deleteQuietly(versionMetaFile);
            }
        }
    }
}
