/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

public class DecompressCmdFactory {
    public static DecompressCmd create(String srcFile, String dstFile) {
        if (srcFile == null || dstFile == null) {
            throw new IllegalArgumentException("srcFile and dstFile must not be null");
        }
        return new ZstdCmd(srcFile, dstFile);
    }
}
