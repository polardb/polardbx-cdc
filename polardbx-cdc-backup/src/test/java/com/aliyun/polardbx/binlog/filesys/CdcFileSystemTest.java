/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapper;
import com.aliyun.polardbx.binlog.dao.BinlogOssRecordMapperExtend;
import com.aliyun.polardbx.binlog.domain.po.BinlogOssRecord;
import com.aliyun.polardbx.binlog.enums.BinlogPurgeStatus;
import com.aliyun.polardbx.binlog.enums.BinlogUploadStatus;
import com.aliyun.polardbx.binlog.service.BinlogOssRecordService;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * @author yudong
 * @since 2022/8/18
 **/
@Slf4j
public class CdcFileSystemTest extends BaseTest {
    private static final String binlogFilePrefix = "binlog.";
    private final String rootPath = "cdc-file-system-test";
    private final String group = "group_global";
    private final String stream = "stream_global";
    CdcFileSystem fileSystem = new CdcFileSystem(rootPath, group, stream);
    BinlogOssRecordService binlogOssRecordService;
    BinlogOssRecordMapper binlogOssRecordMapper;
    private String cluster_id;
    private List<BinlogOssRecord> ossRecords;

    @SneakyThrows
    @Before
    public void before() {
        mockConfig(ConfigKeys.CLUSTER_ID, "cluster_1");
        cluster_id = DynamicApplicationConfig.getString(ConfigKeys.CLUSTER_ID);
        binlogOssRecordService = SpringContextHolder.getObject(BinlogOssRecordService.class);
        binlogOssRecordMapper = SpringContextHolder.getObject(BinlogOssRecordMapper.class);
        prepareFiles();
    }

    @SneakyThrows
    @After
    public void after() {
        log.info("delete directory " + rootPath);
        FileUtils.deleteDirectory(new File("cdc-file-system-test"));
    }

    private void prepareFiles() throws IOException {
        String content = "hello, world";
        int n = 15;
        // generate some local files
        File dir = new File(rootPath);
        ossRecords = new ArrayList<>();
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
                ossRecords.add(record);
            }
        } catch (Exception e) {
            log.warn("failed in prepare files: ", e);
            throw e;
        }
    }

    @Test
    public void listLocalFilesTest() {
        List<CdcFile> localFiles = fileSystem.listLocalFiles(false);
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
        mockConfig(ConfigKeys.CLUSTER_ID, cluster_id);

        try (MockedStatic<SpringContextHolder> springContextHolder = mockStatic(SpringContextHolder.class)) {
            BinlogOssRecordMapperExtend mapper = Mockito.mock(BinlogOssRecordMapperExtend.class);
            springContextHolder.when(() -> SpringContextHolder.getObject(BinlogOssRecordMapperExtend.class))
                .thenReturn(mapper);
            when(mapper.getRecordsInFileRange(group, stream, cluster_id, 1, 14)).thenReturn(ossRecords);

            List<CdcFile> files = fileSystem.listAllFiles(true);
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
            files = fileSystem.listAllFiles(true);
            actual.clear();
            for (CdcFile file : files) {
                actual.add(file.getName());
            }
            expectTrue = ListUtils.isEqualList(expect, actual);
            Assert.assertTrue(expectTrue);
        }
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
