/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.cache;

import java.io.IOException;
import java.io.InputStream;

public interface Cache {
    public void fetchData() throws IOException;

    public void resetStream(InputStream in);

    public int skip(int n);

    public int read(byte[] data, int offset, int size) throws IOException;

    public void close() throws IOException;

}
