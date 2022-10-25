/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 *
 **/
public class BinlogFileTest {

    @Before
    public void before() {
        SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @Test
    public void testSeek() throws IOException {
        File file = new File("/Users/lubiao/Downloads/Docker.dmg");
        RandomAccessFile raf = new RandomAccessFile(file, "rw");
        long length = raf.length();
        long startTime = System.currentTimeMillis();
        while (raf.getFilePointer() < length) {
            raf.readByte();
            raf.readInt();
            raf.readInt();
            raf.seek(raf.getFilePointer() + 10000);
        }
        long endTime = System.currentTimeMillis();
        System.out.println("cost time :" + (endTime - startTime));
    }

    @Test
    public void testSeekLast() throws FileNotFoundException {
        File file = new File("/Users/lubiao/Downloads/binlog.000273");
        BinlogFile binlogFile = new BinlogFile(file, "r", 1024, 256, true);
        BinlogFile.SeekResult seekResult = binlogFile.seekLastTso();
        System.out.println(seekResult);
    }

    @Test
    public void testTruncate() throws IOException {
        String dataStr = "xxxxxxxxxx";
        byte[] dataBytes = dataStr.getBytes();

        String basePath = System.getProperty("user.home");
        File file = new File(basePath + "/truncate_test.txt");
        FileUtils.deleteQuietly(file);
        file.createNewFile();
        BinlogFile binlogFile = new BinlogFile(file, "rw", 1024, 256, true);
        for (int i = 0; i < 10; i++) {
            binlogFile.writeData(dataBytes, 0, dataBytes.length);
        }
        binlogFile.flush();

        Assert.assertEquals(binlogFile.fileSize(), 100);
        Assert.assertEquals(binlogFile.filePointer(), 100);

        binlogFile.truncate(50);
        Assert.assertEquals(binlogFile.fileSize(), 50);
        Assert.assertEquals(binlogFile.fileSize(), 50);
    }
}
