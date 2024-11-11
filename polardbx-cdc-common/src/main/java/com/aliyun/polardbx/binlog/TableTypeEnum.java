/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

public enum TableTypeEnum {
    SINGLE(0), SHARDING(1), BROADCAST(2), GSI(3);

    private int value;

    TableTypeEnum(int value) {
        this.value = value;
    }

    public static TableTypeEnum typeOf(int value) {
        for (TableTypeEnum typeEnum : values()) {
            if (typeEnum.getValue() == value) {
                return typeEnum;
            }
        }
        return null;
    }

    public int getValue() {
        return value;
    }
}
