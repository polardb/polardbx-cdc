/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

public abstract class DecompressCmd {
    protected final String srcFile;
    protected final String dstFile;

    public DecompressCmd(String srcFile, String dstFile) {
        this.srcFile = srcFile;
        this.dstFile = dstFile;
    }

    public abstract void execute() throws Exception;
}
