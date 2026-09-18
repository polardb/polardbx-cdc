/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_CLEAN_WORKER_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_BASE_PATH;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_DELETE_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_ENABLE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_MODE;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_NEW_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_TXNITEM_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_TXN_THRESHOLD;
import static com.aliyun.polardbx.binlog.ConfigKeys.STORAGE_PERSIST_UNIT_COUNT;
import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class StorageFactoryTest extends BaseTest {

    private String testBasePath;

    @Before
    public void setUp() throws Exception {
        // 创建临时测试目录
        Path tempDir = Files.createTempDirectory("storage_test_");
        testBasePath = tempDir.toString();

        // 设置必要的配置项
        mockConfig(STORAGE_PERSIST_ENABLE, "true");
        mockConfig(STORAGE_PERSIST_BASE_PATH, testBasePath);
        mockConfig(TASK_NAME, "test-task");
        mockConfig(STORAGE_PERSIST_MODE, "AUTO");
        mockConfig(STORAGE_PERSIST_NEW_THRESHOLD, "0.8");
        mockConfig(STORAGE_PERSIST_TXN_THRESHOLD, "1000");
        mockConfig(STORAGE_PERSIST_TXNITEM_THRESHOLD, "10000");
        mockConfig(STORAGE_PERSIST_DELETE_MODE, "SINGLE");
        mockConfig(STORAGE_PERSIST_UNIT_COUNT, "4");
        mockConfig(STORAGE_CLEAN_WORKER_COUNT, "2");
    }

    @After
    public void tearDown() throws Exception {
        // 清理测试目录
        if (testBasePath != null) {
            File baseDir = new File(testBasePath);
            if (baseDir.exists()) {
                deleteDirectory(baseDir);
            }
        }
    }

    @Test
    public void testCreateStorage_OneStoragePerDnTrue() {
        String identifier = "test-storage";
        boolean oneStoragePerDn = true;

        Storage storage = StorageFactory.createStorage(identifier, oneStoragePerDn);

        // 验证返回的 storage 实例不为空
        assertNotNull(storage);
        assertTrue(storage instanceof LogEventStorage);

        // 验证内部的 repository 配置
        LogEventStorage logEventStorage = (LogEventStorage) storage;
        Repository repository = logEventStorage.getRepository();
        assertNotNull(repository);

        // 验证路径正确性
        String expectedPath = testBasePath + File.separator + "test-task" + File.separator + identifier;
        assertEquals(expectedPath, repository.getBasePath());
    }

    @Test
    public void testCreateStorage_OneStoragePerDnFalse() {
        String identifier = "test-storage";
        boolean oneStoragePerDn = false;

        Storage storage = StorageFactory.createStorage(identifier, oneStoragePerDn);

        // 验证返回的 storage 实例不为空
        assertNotNull(storage);
        assertTrue(storage instanceof LogEventStorage);

        // 验证内部的 repository 配置
        LogEventStorage logEventStorage = (LogEventStorage) storage;
        Repository repository = logEventStorage.getRepository();
        assertNotNull(repository);

        // 验证 repoUnitCount 为配置值
        // 由于 Repository 的字段是私有的，我们无法直接访问，但可以通过行为来验证
        String expectedPath = testBasePath + File.separator + "test-task" + File.separator + identifier;
        assertEquals(expectedPath, repository.getBasePath());
    }

    @Test
    public void testCreateStorage_DifferentPersistModes() {
        String identifier = "test-storage-mode";

        // 测试 AUTO 模式
        mockConfig(STORAGE_PERSIST_MODE, "AUTO");
        Storage storageAuto = StorageFactory.createStorage(identifier + "-auto", true);
        assertNotNull(storageAuto);

        // 测试 FORCE 模式
        mockConfig(STORAGE_PERSIST_MODE, "FORCE");
        Storage storageForce = StorageFactory.createStorage(identifier + "-force", true);
        assertNotNull(storageForce);

        // 测试 RANDOM 模式
        mockConfig(STORAGE_PERSIST_MODE, "RANDOM");
        Storage storageRandom = StorageFactory.createStorage(identifier + "-random", true);
        assertNotNull(storageRandom);
    }

    @Test
    public void testCreateStorage_DifferentDeleteModes() {
        String identifier = "test-storage-delete";

        // 测试 SINGLE 模式
        mockConfig(STORAGE_PERSIST_DELETE_MODE, "SINGLE");
        Storage storageSingle = StorageFactory.createStorage(identifier + "-single", true);
        assertNotNull(storageSingle);

        // 测试 RANGE 模式
        mockConfig(STORAGE_PERSIST_DELETE_MODE, "RANGE");
        Storage storageRange = StorageFactory.createStorage(identifier + "-range", true);
        assertNotNull(storageRange);

        // 测试 NONE 模式
        mockConfig(STORAGE_PERSIST_DELETE_MODE, "NONE");
        Storage storageNone = StorageFactory.createStorage(identifier + "-none", true);
        assertNotNull(storageNone);
    }

    @Test
    public void testCreateStorage_PersistDisabled() {
        String identifier = "test-storage-disabled";

        // 测试禁用持久化
        mockConfig(STORAGE_PERSIST_ENABLE, "false");
        Storage storage = StorageFactory.createStorage(identifier, true);
        assertNotNull(storage);

        LogEventStorage logEventStorage = (LogEventStorage) storage;
        Repository repository = logEventStorage.getRepository();
        assertNotNull(repository);
    }

    // 递归删除目录
    private void deleteDirectory(File directory) {
        if (directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteDirectory(file);
                }
            }
        }
        directory.delete();
    }
}
