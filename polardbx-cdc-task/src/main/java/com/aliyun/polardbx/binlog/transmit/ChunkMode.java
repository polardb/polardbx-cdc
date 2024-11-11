/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit;

public enum ChunkMode {
    /**
     * 对象数量
     */
    ITEMSIZE,

    /**
     * 内存大小
     */
    MEMSIZE;

    public boolean isItemSize() {
        return this == ChunkMode.ITEMSIZE;
    }

    public boolean isMemSize() {
        return this == ChunkMode.MEMSIZE;
    }
}
