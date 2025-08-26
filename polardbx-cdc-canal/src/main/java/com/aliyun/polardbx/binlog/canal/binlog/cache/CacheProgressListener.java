/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

public interface CacheProgressListener {
    public void onStart();

    public void onAllocateBuffer();

    public void onProgress(long bytesRead);

    public void onFinish();
}
