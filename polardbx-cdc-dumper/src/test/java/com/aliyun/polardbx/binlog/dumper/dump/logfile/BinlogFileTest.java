/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 *
 **/
public class BinlogFileTest {

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
        File file = new File("/Users/lubiao/Downloads/binlog (1).000001");
        BinlogFile binlogFile = new BinlogFile(file, "r", 1024, 256);
        BinlogFile.SeekResult seekResult = binlogFile.seekLastTso();
        System.out.println(seekResult);
    }
}
