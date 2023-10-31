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

import com.aliyun.polardbx.binlog.testing.BaseTest;
import lombok.SneakyThrows;
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
import java.util.List;

/**
 * @author yudong
 * @since 2022/8/18
 **/
@Ignore
public class CdcFileSystemTest extends BaseTest {
    private final String rootPath = "cdc-file-system-test";
    private final String group = "group_global";
    private final String stream = "stream_global";
    private static final String binlogFilePrefix = "binlog.";
    CdcFileSystem fileSystem = new CdcFileSystem(rootPath, group, stream);

    @SneakyThrows
    @Before
    public void before() {
        prepareFiles();
    }

    @After
    public void after() throws IOException {
        FileUtils.deleteDirectory(new File("cdc-file-system-test"));
    }

    private void prepareFiles() throws IOException {
        String content = "hello, world";
        int n = 15;
        // generate some local files
        File dir = new File(rootPath);
        dir.createNewFile();
        for (int i = 1; i < n; i++) {
            String localName = fileSystem.getLocalFullName(binlogFilePrefix + String.format("%06d", i));
            File f = new File(localName);
            f.createNewFile();
            PrintWriter writer = new PrintWriter(f);
            writer.print(content);
            writer.close();
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
}
