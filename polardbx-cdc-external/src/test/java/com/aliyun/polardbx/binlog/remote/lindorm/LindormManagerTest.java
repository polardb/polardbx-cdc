/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.remote.lindorm;

import com.aliyun.oss.common.utils.CRC64;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.remote.Appender;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getInt;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getString;

/**
 * @author yudong
 * @since 2022/10/18
 **/
@Slf4j
@Ignore
public class LindormManagerTest extends BaseTest {
    private static LindormManager manager;

    @BeforeClass
    public static void config() {
        LindormConfig config = new LindormConfig();
        config.setAccessKey(getString(ConfigKeys.LINDORM_ACCESSKEY_ID));
        config.setAccessSecret(getString(ConfigKeys.LINDORM_ACCESSKEY_ID_SECRET));
        config.setBucket(getString(ConfigKeys.LINDORM_BUCKET).toLowerCase());
        config.setIp(getString(ConfigKeys.LINDORM_ENDPOINT));
        config.setThriftPort(getInt(ConfigKeys.LINDORM_THRIFT_PORT));
        config.setS3Port(getInt(ConfigKeys.LINDORM_S3_PORT));
        config.setPolardbxInstance("qatest_instance");

        manager = new LindormManager(config);
    }

    @Test
    public void listBucketTest() {
        List<String> buckets = manager.listBuckets();
        boolean expectTrue = buckets.contains(manager.getBucket());
        Assert.assertTrue(expectTrue);
    }

    @Test
    public void deleteTest() {
        String fileName = "delete-test.txt";
        String txt = "hello, world";
        Appender appender = manager.providerAppender(fileName);
        appender.begin();
        appender.append(txt.getBytes(), txt.getBytes().length);
        appender.end();
        boolean expectTrue = manager.isObjectsExistForPrefix(fileName);
        Assert.assertTrue(expectTrue);
        manager.delete(fileName);
        boolean expectFalse = manager.isObjectsExistForPrefix(fileName);
        Assert.assertFalse(expectFalse);
    }

    @Test
    public void listFilesTest() {
        String testPath = "list-test/";
        manager.deleteAll(testPath);
        String baseFileName = testPath + "list-files-test.";
        String txt = "hello, world";
        int n = 10;
        List<String> expectFileList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String fileName = baseFileName + i;
            expectFileList.add(fileName.substring(fileName.lastIndexOf('/') + 1));
            Appender appender = manager.providerAppender(fileName);
            appender.begin();
            appender.append(txt.getBytes(), txt.getBytes().length);
            appender.end();
        }
        List<String> actualFileList = manager.listFiles(testPath);
        actualFileList.sort(String::compareTo);
        boolean expectTrue = ListUtils.isEqualList(expectFileList, actualFileList);
        Assert.assertTrue(expectTrue);

        manager.deleteAll(testPath);
        List<String> expectEmptyList = manager.listFiles(testPath);
        Assert.assertTrue(expectEmptyList.isEmpty());
    }

    @Test
    public void prepareDownloadLinkTest() {
        // todo @yudong 如何校验一个url是否是合法的?
    }

    @Test
    public void s3DownloadThriftUploadedFilesTest() throws IOException {
        String fileName = "download-test1.txt";
        String expectContent = "hello, world";
        String destFile = "download-test1.txt";
        manager.delete(fileName);
        Appender appender = manager.providerAppender(fileName);
        appender.begin();
        appender.append(expectContent.getBytes(), expectContent.getBytes().length);
        appender.end();
        manager.download(fileName, ".");
        String actualContent = new String(Files.readAllBytes(Paths.get(destFile)));
        Assert.assertEquals(expectContent, actualContent);
        new File(destFile).deleteOnExit();
    }

    @Test
    public void s3DownloadS3UploadedFilesTest() throws IOException {

    }

    @Test
    public void downloadBigFileTest() {
        // todo @yudong
    }

    @Test
    public void getObjectDataTest() {
        String fileName = "get-obj-data-test.txt";
        manager.delete(fileName);
        Appender putAppender = manager.providerAppender(fileName);
        byte[] content = new byte[1024];
        new Random().nextBytes(content);
        CRC64 expectCrc64 = new CRC64();
        expectCrc64.update(content, content.length);
        putAppender.begin();
        putAppender.append(content, content.length);
        putAppender.end();
        byte[] actualData = manager.getObjectData(fileName);
        CRC64 actualCrc64 = new CRC64();
        actualCrc64.update(actualData, actualData.length);
        Assert.assertEquals(expectCrc64.getValue(), actualCrc64.getValue());
    }

    @Test
    public void getBigObjectDataTest() {
        // todo @yudong
    }

    @Test
    public void appendSmallFileTest() {
        // append 100个小文件
        int n = 100;
        String format = "append-small-file.%06d";
        for (int i = 0; i < n; i++) {
            String fileName = String.format(format, i);
            manager.delete(fileName);
            CRC64 expectCrc64 = new CRC64();
            Appender appender = manager.providerAppender(fileName);
            appender.begin();
            byte[] content = new byte[1024];
            new Random().nextBytes(content);
            expectCrc64.update(content, content.length);
            appender.append(content, content.length);
            new Random().nextBytes(content);
            expectCrc64.update(content, content.length);
            appender.append(content, content.length);
            appender.end();
            long fileSize = manager.getSize(fileName);
            Assert.assertEquals(content.length << 1, fileSize);
            String actualCrc64 = manager.getMd5(fileName);
            Assert.assertEquals(expectCrc64.getValue(), Long.valueOf(actualCrc64).longValue());
        }
    }

    @Test
    public void appendBigFileTest() {
        // append 1个大文件
        String fileName = "append-big-file-test.txt";
        manager.delete(fileName);
        CRC64 expectCrc64 = new CRC64();
        Appender appender = manager.providerAppender(fileName);
        appender.begin();
        // 每次最多append 5M
        byte[] content = new byte[5 << 20];
        int n = 1000;
        for (int i = 0; i < n; i++) {
            new Random().nextBytes(content);
            expectCrc64.update(content, content.length);
            appender.append(content, content.length);
            log.info("#{}append", i);
        }
        appender.end();
        String actualCrc64 = manager.getMd5(fileName);
        Assert.assertEquals(expectCrc64.getValue(), Long.valueOf(actualCrc64).longValue());
        manager.delete(fileName);
    }
}
