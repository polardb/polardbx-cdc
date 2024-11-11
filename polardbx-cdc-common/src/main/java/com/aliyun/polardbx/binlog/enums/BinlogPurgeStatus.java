/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.enums;

/**
 * @author chengjin, yudong
 */
public enum BinlogPurgeStatus {
    /**
     * binlog文件还存储在远端存储上
     */
    UN_COMPLETE(0),
    /**
     * binlog文件已经被从远端存储上删除
     */
    COMPLETE(1);

    private final int value;

    BinlogPurgeStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
