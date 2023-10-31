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
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import org.junit.Ignore;
import org.junit.Test;

import java.io.IOException;

@Ignore
public class CdcFileReaderTest {
    @Test
    public void nextDumpPack() throws IOException {
        LogFileManager logFileManager = new LogFileManager();
        logFileManager.setBinlogRootPath("/Users/dengyouyou/dv/mysql3306/");
        logFileManager.setLatestFileCursor(new BinlogCursor("binlog.000006", 860L));

        BinlogDumpReader binlogFileReader = new BinlogDumpReader(logFileManager, "binlog.000005",
            4, 2 * 1024 * 1024, 32 * 1024 * 1024);

        binlogFileReader.valid();

        binlogFileReader.fakeRotateEvent();
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
        logFileManager.setBinlogRootPath("/Users/dengyouyou/dv/mysql3306/");
        logFileManager.setLatestFileCursor(new BinlogCursor("binlog.000006", 816L));
    }
}