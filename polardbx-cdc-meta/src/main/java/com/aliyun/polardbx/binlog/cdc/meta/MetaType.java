/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

/**
 * created by ziyang.lb
 **/
public enum MetaType {
    /**
     * Snapshot
     */
    SNAPSHOT((byte) 1),
    /**
     * DDL SQL
     */
    DDL((byte) 2);

    private final byte value;

    MetaType(byte value) {
        this.value = value;
    }

    public byte getValue() {
        return value;
    }
}
