/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;

public class DownloadTaskFactory {
    public static DownloadTask createDownloadTask(String storageInstanceId, BinlogFile binlogFile, String localFilePath) {
        return new DownloadTask(storageInstanceId, binlogFile, localFilePath);
    }
}
