/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

import java.io.IOException;
import java.io.InputStream;

public interface Cache {
    public void fetchData(InputStream is) throws Exception;

    void interrupt();

    public int skip(int n);

    public int read(byte[] data, int offset, int size) throws IOException;

    public void close() throws IOException;

    public void setProgressListener(CacheProgressListener listener);

}
