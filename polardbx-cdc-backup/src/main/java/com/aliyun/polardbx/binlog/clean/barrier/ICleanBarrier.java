/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.clean.barrier;

/**
 * @author yudong
 */
public interface ICleanBarrier {

    /**
     * binlog文件是否可以被清除
     *
     * @param binlogName file name
     * @return whether this file can be deleted
     */
    boolean canClean(String binlogName);
}
