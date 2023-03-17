/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.filesys;

import com.aliyun.polardbx.binlog.BinlogFileUtil;
import com.aliyun.polardbx.binlog.channel.BinlogFileReadChannel;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.ListUtils;
import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
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
public class LocalFileSystemTest {
    private final String group = "group1";
    private final String stream = "stream1";
    private final String path = BinlogFileUtil.getBinlogFileFullPath("binlog/V1/", group, stream);
    private final LocalFileSystem fileSystem = new LocalFileSystem(path, group, stream);

    @Before
    public void before() throws IOException {
        FileUtils.forceDelete(new File(path));
        FileUtils.forceMkdir(new File(path));
    }

    @After
    public void after() throws IOException {
        FileUtils.forceDelete(new File(path));
    }

    private void cleanPath() throws IOException {
        FileUtils.cleanDirectory(new File(path));
    }

    @Test
    public void sizeTest() throws IOException {
        String fileName = "size-test.txt";
        String content = "Clouds come floating into my life, " +
            "no longer to carry rain or usher storm, " +
            "but to add color to my sunset sky.";
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
        cleanPath();
        String fileNamePrefix = BinlogFileUtil.getBinlogFilePrefix(group, stream);
        List<String> expect = new ArrayList<>();
        List<String> actual = new ArrayList<>();
        int n = 11;
        for (int i = 0; i < n; i++) {
            String fileName = fileNamePrefix + String.format("%06d", i);
            expect.add(fileName);
            File f = fileSystem.newFile(fileName);
            f.createNewFile();
        }

        // 测试后缀不匹配场景
        String fileName = fileNamePrefix + String.format("%06d", 10) + ".tmp";
        File f = fileSystem.newFile(fileName);
        f.createNewFile();
        fileName = fileNamePrefix + String.format("%05d", 10);
        f = fileSystem.newFile(fileName);
        f.createNewFile();
        // 测试前缀不匹配场景
        fileName = "random" + fileNamePrefix + String.format("%06d", 10);
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
