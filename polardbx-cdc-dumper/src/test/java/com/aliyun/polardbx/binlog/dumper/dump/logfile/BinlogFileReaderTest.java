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

import com.aliyun.polardbx.binlog.domain.Cursor;
import org.junit.Test;

import java.io.IOException;
import java.nio.ByteBuffer;

public class BinlogFileReaderTest {

    @Test
    public void test() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        System.out.println("init  " + buffer);
        buffer.put((byte) 1);
        buffer.put((byte) 2);
        buffer.put((byte) 3);
        System.out.println("put   " + buffer);
        buffer.flip();
        System.out.println("flip  " + buffer);
        buffer.get();
        buffer.get();
        System.out.println("get   " + buffer);
        System.out.println("mark  " + buffer);
        buffer.compact();
        System.out.println("comp  " + buffer);
        buffer.put((byte) 4);
        System.out.println("put   " + buffer);
        System.out.println("rset  " + buffer);
    }

    @Test
    public void nextDumpPack() throws IOException {
        LogFileManager logFileManager = new LogFileManager();
        logFileManager.setBinlogFileDirPath("/Users/dengyouyou/dv/mysql3306/");
        logFileManager.setLatestFileCursor(new Cursor("binlog.000006", 860L));

        BinlogDumpReader binlogFileReader = new BinlogDumpReader(logFileManager, "binlog.000005",
            4);

        binlogFileReader.valid();

        binlogFileReader.prepareFakeEvents();
        binlogFileReader.start();
        int count = 0;
        while (binlogFileReader.hasNext()) {
            binlogFileReader.nextDumpPack();
            count++;
        }
        System.out.println(count);
    }

    @Test
    public void testReaders() {
        LogFileManager logFileManager = new LogFileManager();
        logFileManager.setBinlogFileDirPath("/Users/dengyouyou/dv/mysql3306/");
        logFileManager.setLatestFileCursor(new Cursor("binlog.000006", 816L));
    }
}