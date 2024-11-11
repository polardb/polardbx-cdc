/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.domain.BinlogCursor;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import com.aliyun.polardbx.rpc.cdc.BinlogEvent;
import org.junit.Ignore;
import org.junit.Test;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;

@Ignore
public class BinlogEventReaderTest extends BaseTest {
    File dir = new File(System.getProperty("user.dir") + "/binlog/mysql/");

    @Test
    public void nextBinlogEvent() throws IOException {
        LogFileManager logFileManager = new LogFileManager();
        logFileManager.setBinlogRootPath(dir.getPath());
        logFileManager.setLatestFileCursor(new BinlogCursor("binlog.000001", 860L));

        BinlogEventReader binlogFileReader =
            new BinlogEventReader(logFileManager.getBinlogFileByName("binlog.000001"),
                0, 0, -1);

        binlogFileReader.valid();
        binlogFileReader.skipPos();
        binlogFileReader.skipOffset();

        while (binlogFileReader.hasNext()) {
            BinlogEvent binlogEvent = binlogFileReader.nextBinlogEvent();
            System.out.println(binlogEvent);
        }
    }

    @Test
    public void binlogDumper() throws IOException {
        LogFileManager logFileManager = new LogFileManager();
        logFileManager.setBinlogRootPath(dir.getPath());
        BinlogEventReader binlogFileReader =
            new BinlogEventReader(logFileManager.getBinlogFileByName("binlog.000001"), 0, 0, -1);
        BufferedWriter bw = new BufferedWriter(
            new OutputStreamWriter(new FileOutputStream("/Users/yanfenglin/Downloads/tmp/binlog.000004-dmp")));
        binlogFileReader.valid();
        binlogFileReader.skipPos();
        binlogFileReader.skipOffset();
        while (binlogFileReader.hasNext()) {
            BinlogEvent binlogEvent = binlogFileReader.nextBinlogEvent();
            bw.write(
                binlogEvent.getEndLogPos() + "---" + binlogEvent.getServerId() + "---" + binlogEvent.getEventType()
                    + " " + binlogEvent.getInfo() + " size : " + binlogEvent.getSerializedSize() + "\n");
        }
        bw.close();
    }
}
