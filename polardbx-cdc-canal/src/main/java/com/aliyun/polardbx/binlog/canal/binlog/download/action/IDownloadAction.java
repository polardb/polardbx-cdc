/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download.action;

import com.aliyun.polardbx.binlog.api.rds.BinlogFile;

public interface IDownloadAction {
    void exec(String storageInstanceId, String localPath, BinlogFile binlogFile) throws Exception;
}
