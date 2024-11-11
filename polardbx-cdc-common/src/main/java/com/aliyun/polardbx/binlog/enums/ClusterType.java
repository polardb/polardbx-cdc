/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.enums;

/**
 * @author chengjin
 */
public enum ClusterType {

    /* note that '-' should not exist in type name*/
    BINLOG(1), IMPORT(2), BINLOG_X(3), REPLICA(4), FLASHBACK(5), COLUMNAR(6);
    private int value;

    ClusterType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
