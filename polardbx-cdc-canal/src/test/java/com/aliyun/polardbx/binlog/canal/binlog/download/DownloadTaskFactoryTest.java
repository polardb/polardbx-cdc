/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;
import org.junit.Assert;
import org.junit.Test;

public class DownloadTaskFactoryTest {
    @Test
    public void testCreateNewTask() {
        BinlogFile binlogFile = new BinlogFile();
        binlogFile.setIntranetDownloadLink("http://127.0.0.1:8080/test.txt");
        DownloadTask downloadTask =
            DownloadTaskFactory.createDownloadTask("polardbx-0", binlogFile, "/tmp/test.txt");
        Assert.assertNotNull(downloadTask);
        Assert.assertEquals("/tmp/test.txt", downloadTask.getLocalFilePath());
    }
}
