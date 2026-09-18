/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.github.luben.zstd.ZstdInputStream;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;

public class MultiPartInputStreamFactory {
    public static InputStream create(String url, long fileSize, String storageInstanceId, String fileName,
                                     ExecutorService executorService)
        throws IOException {
        boolean autoDecompress =
            DynamicApplicationConfig.getBoolean(ConfigKeys.TASK_DUMP_OFFLINE_BINLOG_RDS_BINLOG_AUTO_DECOMPRESS);
        if (url.contains(fileName + ".zst") && autoDecompress) {
            return new ZstdInputStream(
                new MultiPartInputStream(url, fileSize, storageInstanceId, fileName, executorService));
        }
        return new MultiPartInputStream(url, fileSize, storageInstanceId, fileName, executorService);
    }
}
