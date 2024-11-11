/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

public class StorageUnit {
    public static long bToM(long b) {
        return b / 1024 / 1024;
    }
}
