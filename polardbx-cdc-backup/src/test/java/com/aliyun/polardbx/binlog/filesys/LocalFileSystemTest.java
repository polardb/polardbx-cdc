/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.binlog.util.BinlogFileUtil;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yudong
 * @since 2022/8/21
 **/
@Slf4j
public class LocalFileSystemTest extends BaseTest {
    private final String group = "group1";
    private final String stream = "stream1";
    private static final String rootPath = "local_file_system_test";
    private final LocalFileSystem fileSystem = new LocalFileSystem(rootPath, group, stream);

    @AfterClass
    @SneakyThrows
    public static void deleteDir() {
        FileUtils.forceDelete(new File(rootPath));
    }

    @After
    @SneakyThrows
    public void cleanDir() {
        fileSystem.cleanDir();
    }

    @Test
    public void sizeTest() throws IOException {
        String fileName = "size-test.txt";
        String content = "size_test";
        long expect = -1;
        long actual = fileSystem.size(fileName);
        Assert.assertEquals(expect, actual);
        File f = fileSystem.newFile(fileName);
        f.createNewFile();
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(content.getBytes());
        expect = content.length();
        actual = fileSystem.size(fileName);
        Assert.assertEquals(expect, actual);
    }

    @Test
    public void deleteTest() throws IOException {
        String fileName = "delete-test.txt";
        boolean actual = fileSystem.exist(fileName);
        Assert.assertFalse(actual);
        File f = fileSystem.newFile(fileName);
        f.createNewFile();
        actual = fileSystem.exist(fileName);
        Assert.assertTrue(actual);
        fileSystem.delete(fileName);
        actual = fileSystem.exist(fileName);
        Assert.assertFalse(actual);
    }

    @Test
    public void listFilesTest() throws IOException {
        List<String> expect = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        int n = 11;
        for (int i = 1; i < n; i++) {
            String fileName = BinlogFileUtil.getBinlogFilePrefix(group, stream) + String.format(".%06d", i);
            expect.add(fileName);
            File f = fileSystem.newFile(fileName);
            f.createNewFile();
        }

        // 测试后缀不匹配场景
        String fileName = String.format("%06d", 10) + ".tmp";
        File f = fileSystem.newFile(fileName);
        f.createNewFile();
        fileName = String.format("%05d", 10);
        f = fileSystem.newFile(fileName);
        f.createNewFile();
        // 测试前缀不匹配场景
        fileName = "random" + String.format("%06d", 10);
        f = fileSystem.newFile(fileName);
        f.createNewFile();

        List<CdcFile> fileList = fileSystem.listFiles();
        for (CdcFile cdcFile : fileList) {
            actual.add(cdcFile.getName());
        }
        boolean res = ListUtils.isEqualList(expect, actual);
        Assert.assertTrue(res);
    }

    @Test
    public void getChannelTest() throws IOException {
        String fileName = "get-channel-test.txt";
        BinlogFileReadChannel channel = fileSystem.getReadChannel(fileName);
        Assert.assertNull(channel);
        String content = "Darkness travels towards light, " +
            "but blindness towards death.";
        File f = fileSystem.newFile(fileName);
        f.createNewFile();
        FileOutputStream fos = new FileOutputStream(f);
        fos.write(content.getBytes());
        channel = fileSystem.getReadChannel(fileName);
        byte[] data = new byte[1024];
        ByteBuffer buffer = ByteBuffer.wrap(data);
        channel.read(buffer);
        buffer.flip();
        String actual = new String(buffer.array(), 0, buffer.limit());
        Assert.assertEquals(content, actual);
    }
}
