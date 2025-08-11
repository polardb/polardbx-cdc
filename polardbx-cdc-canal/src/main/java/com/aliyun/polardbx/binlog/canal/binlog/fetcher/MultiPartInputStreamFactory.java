/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.fetcher;

import java.io.IOException;
import java.util.concurrent.ExecutorService;

public class MultiPartInputStreamFactory {
    public static MultiPartInputStream create(String url, long fileSize, String storageInstanceId, String fileName,
                                              ExecutorService executorService)
        throws IOException {
        return new MultiPartInputStream(url, fileSize, storageInstanceId, fileName, executorService);
    }
}
