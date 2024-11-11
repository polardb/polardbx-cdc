/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.io.FileUtils;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

@Ignore
public class BinlogFileTest extends BaseTest {

    @Test
    @Ignore
    public void testSeekLast() throws FileNotFoundException {
        File file = new File("/Users/lubiao/Downloads/binlog.000273");
        BinlogFile binlogFile = new BinlogFile(file, "r", 1024, 256, true, null);
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
        BinlogFile binlogFile = new BinlogFile(file, "rw", 1024, 256, true, null);
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
