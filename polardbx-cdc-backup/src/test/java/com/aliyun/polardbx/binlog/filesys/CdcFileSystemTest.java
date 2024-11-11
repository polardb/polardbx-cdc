/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import com.aliyun.polardbx.binlog.enums.BinlogUploadStatus;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author yudong
 * @since 2022/8/18
 **/
@Slf4j
public class CdcFileSystemTest extends BaseTestWithGmsTables {
    private final String rootPath = "cdc-file-system-test";
    private final String group = "group_global";
    private final String stream = "stream_global";
    private String cluster_id;
    private static final String binlogFilePrefix = "binlog.";
    CdcFileSystem fileSystem = new CdcFileSystem(rootPath, group, stream);
    BinlogOssRecordService binlogOssRecordService;
    BinlogOssRecordMapper binlogOssRecordMapper;

    @SneakyThrows
    @Before
    public void before() {
        setConfig(ConfigKeys.CLUSTER_ID, "cluster_1");
        cluster_id = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        binlogOssRecordService = SpringContextHolder.getObject(BinlogOssRecordService.class);
        binlogOssRecordMapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        prepareFiles();
    }

    @After
    public void after() throws IOException {
        log.info("delete directory " + rootPath);
        FileUtils.deleteDirectory(new File("cdc-file-system-test"));
    }

    private void prepareFiles() throws IOException {
        String content = "hello, world";
        int n = 15;
        // generate some local files
        File dir = new File(rootPath);
        try {
            dir.createNewFile();
            for (int i = 1; i < n; i++) {
                String localName = fileSystem.getLocalFullName(binlogFilePrefix + String.format("%06d", i));
                File f = new File(localName);
                f.createNewFile();
                PrintWriter writer = new PrintWriter(f);
                writer.print(content);
                writer.close();
                BinlogOssRecord record = buildRecord(f.getName());
                binlogOssRecordMapper.insertSelective(record);
            }
        } catch (Exception e) {
            log.warn("failed in prepare files: ", e);
            throw e;
        }
    }

    @Test
    public void listLocalFilesTest() {
        List<CdcFile> localFiles = fileSystem.listLocalFiles();
        List<String> actualFileList = new ArrayList<>();
        for (CdcFile file : localFiles) {
            actualFileList.add(file.getName());

        }
        List<String> expectFileList = new ArrayList<>();
        int n = 15;
        for (int i = 1; i < n; i++) {
            expectFileList.add(binlogFilePrefix + String.format("%06d", i));
        }
        boolean expectTrue = ListUtils.isEqualList(expectFileList, actualFileList);
        Assert.assertTrue(expectTrue);
    }

    @Test
    public void listFilesTest() {
        List<CdcFile> files = fileSystem.listAllFiles();
        List<String> actual = new ArrayList<>();
        for (CdcFile file : files) {
            actual.add(file.getName());
            // 列出的文件应该都有扩展信息
            Assert.assertNotNull(file.getRecord());
        }
        List<String> expect = new ArrayList<>();
        int n = 15;
        for (int i = 1; i < n; i++) {
            expect.add(binlogFilePrefix + String.format("%06d", i));
        }
        boolean expectTrue = ListUtils.isEqualList(expect, actual);
        Assert.assertTrue(expectTrue);
        files = fileSystem.listAllFiles();
        actual.clear();
        for (CdcFile file : files) {
            actual.add(file.getName());
        }
        expectTrue = ListUtils.isEqualList(expect, actual);
        Assert.assertTrue(expectTrue);
    }

    public BinlogOssRecord buildRecord(String name) {
        BinlogOssRecord record = new BinlogOssRecord();
        record.setBinlogFile(name);
        record.setUploadStatus(BinlogUploadStatus.CREATE.getValue());
        record.setPurgeStatus(BinlogPurgeStatus.UN_COMPLETE.getValue());
        record.setLogBegin(new Date());
        record.setLogSize(0L);
        record.setGroupId(group);
        record.setStreamId(stream);
        record.setClusterId(cluster_id);
        return record;
    }
}
