/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;

public class StorageDownloaderTest extends BaseTest {

    @Test(timeout = 10000)
    public void test() throws Exception {
        StorageDownloader downloader = new StorageDownloader("test-storage",
            StorageDownloaderTest.class.getResource("/").getPath() + "/download-test-bin");
        CountDownLatch countDownLatch = new CountDownLatch(2);

        BinlogFile binlogFile1 = new BinlogFile();
        binlogFile1.setDownloadLink("test-storage-file1");
        downloader.addTask(new DownloadTask("test-storage", binlogFile1, "test-storage-file1") {
            @Override
            public void exec() throws Exception {
                countDownLatch.countDown();
            }
        });
        BinlogFile binlogFile2 = new BinlogFile();
        binlogFile2.setDownloadLink("test-storage-file2");
        downloader.addTask(new DownloadTask("test-storage", binlogFile2, "test-storage-file2") {
            @Override
            public void exec() throws Exception {
                countDownLatch.countDown();
            }
        });
        downloader.start();
        countDownLatch.await();
        downloader.stop();
    }
}
