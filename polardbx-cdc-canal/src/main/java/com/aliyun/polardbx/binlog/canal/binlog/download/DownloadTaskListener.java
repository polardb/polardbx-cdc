/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.download;

public interface DownloadTaskListener {
    void beginDownload(String storageInstanceId);

    void catchException(Throwable t);

    void endDownload(String storageInstanceId);
}
