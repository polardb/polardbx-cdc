/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.topology;

public enum TableType {
    SINGLE(0), SHARDING(1), BROADCAST(2), GSI(3);

    private final int value;

    TableType(int value) {
        this.value = value;
    }

    public static TableType from(int value) {
        switch (value) {
        case 0:
            return SINGLE;
        case 1:
            return SHARDING;
        case 2:
            return BROADCAST;
        case 3:
            return GSI;
        default:
            return null;
        }
    }

    public int getValue() {
        return value;
    }

    public boolean isPrimary() {
        return GSI != this;
    }
}
