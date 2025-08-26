/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.backup.StreamContext;
import com.aliyun.polardbx.binlog.clean.BinlogCleaner;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.domain.TaskType;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.lock.LogFileLockManager;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
public class BinlogCleanerTest extends BaseTest {
    private final String group = "group_global";
    private final String stream = "stream_global";
    private final String cluster = "cluster_1";
    private String rootPath = "binlog_cleaner_test";
    private static final String binlogFilePrefix = "binlog.";
    private final int cleanFileNum = 10;
    private final int totalFileNum = 15;
    private final int version = 85271;
    private LogFileLockManager logFileLockManager;

    CdcFileSystem fileSystem;
    StreamContext streamContext;
    BinlogCleaner binlogCleaner;

    @SneakyThrows
    @Before
    public void before() {
        log.info("init binlogCleanerTest ...");
        List<String> streamList = new ArrayList<>();
        streamList.add(stream);

        log.info("init streamContext...");
        streamContext = new StreamContext(group, streamList, cluster, "task_global",
            TaskType.Dumper, version);

        log.info("init binlogCleaner...");
        mockConfig(ConfigKeys.BINLOG_PURGE_IGNORE_DUMPING_FILES_MODE, "ONE");
        mockConfig(ConfigKeys.DISK_SIZE, "251800");
        mockConfig(ConfigKeys.BINLOG_PURGE_DISK_USE_RATIO, "0.0");
        mockConfig(ConfigKeys.BINLOG_DISK_SPACE_MAX_SIZE_MB, "524288000");
        mockConfig(ConfigKeys.CLUSTER_ID, cluster);
        log.info(DynamicApplicationConfig.getString(ConfigKeys.DISK_SIZE));
        logFileLockManager = new LogFileLockManager(stream, streamContext);
        binlogCleaner = new BinlogCleaner(stream, streamContext, logFileLockManager);
        mockConfig(ConfigKeys.IS_LAB_ENV, "true");

        log.info("create fileSystem...");
        fileSystem = new CdcFileSystem(rootPath, group, stream);
        binlogCleaner.setFileSystem(fileSystem);
        logFileLockManager.setLocalFileSystem(fileSystem.getLocalFileSystem());

        log.info("create files for binlogCleanerTest ...");
        prepareFiles();
        logFileLockManager.init();
    }

    @After
    public void after() throws IOException {
        FileUtils.deleteDirectory(new File(rootPath));
    }

    private void prepareFiles() throws IOException {
        String content = "hello, world";
        // generate some local files
        File dir = new File(rootPath);
        dir.createNewFile();
        for (int i = 1; i <= totalFileNum; i++) {
            String localName = fileSystem.getLocalFullName(binlogFilePrefix + String.format("%06d", i));
            File f = new File(localName);
            f.createNewFile();
            PrintWriter writer = new PrintWriter(f);
            writer.print(content);
            writer.close();
        }
    }

    @Test
    public void cleanRemoteFilesTest() {
        Date expireTime = new Date(2024, 1, 1);
        // mock 远端过期文件
        BinlogOssRecordMapperExtend ossRecordMapperExtend = mock(BinlogOssRecordMapperExtend.class);
        BinlogOssRecordMapper ossRecordMapper = mock(BinlogOssRecordMapper.class);
        List<BinlogOssRecord> filesToClean = new ArrayList<>();
        for (int i = 1; i <= cleanFileNum; i++) {
            BinlogOssRecord record = new BinlogOssRecord();
            record.setBinlogFile(binlogFilePrefix + String.format("%06d", i));
            record.setId(i);
            filesToClean.add(record);
        }
        when(
            ossRecordMapperExtend.getRecordsForPurge(group, stream, cluster, expireTime)
        ).thenReturn(filesToClean);

        binlogCleaner.cleanRemoteFiles(expireTime, false, ossRecordMapperExtend, ossRecordMapper);
        // 此时本地文件不应删除，且本地文件存在的远程文件也不应被删除
        Set<CdcFile> remainFiles = fileSystem.getLocalFilesSet();
        Assert.assertEquals(totalFileNum, remainFiles.size());

        binlogCleaner.cleanRemoteFiles(expireTime, true, ossRecordMapperExtend, ossRecordMapper);
        Set<String> remainFileNames = fileSystem.getLocalFileNamesSet();
        Set<String> expectRemainFileNames = new HashSet<>();
        // 设置本地文件按时间清理后，本地文件应该也被删除
        Assert.assertEquals(totalFileNum - cleanFileNum, remainFileNames.size());
        for (int i = cleanFileNum + 1; i <= totalFileNum; i++) {
            expectRemainFileNames.add(binlogFilePrefix + String.format("%06d", i));
        }
        Assert.assertTrue(remainFileNames.equals(expectRemainFileNames));
    }

    @Test
    public void cleanLocalFilesTest() {
        logFileLockManager.readLock("binlog.000001");
        Set<String> remainFileNames = fileSystem.getLocalFileNamesSet();
        log.info("files before clean: {}", remainFileNames);
        binlogCleaner.cleanLocalFiles();
        remainFileNames = fileSystem.getLocalFileNamesSet();
        Assert.assertTrue(remainFileNames.contains("binlog.000001"));
        log.info("remaining files: {}", remainFileNames);
        logFileLockManager.unLockRead("binlog.000001");
    }
}
