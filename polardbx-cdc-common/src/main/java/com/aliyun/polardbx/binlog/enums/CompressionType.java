/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.enums;

/**
 * @author zm
 */
public enum CompressionType {
    /**
     * 不压缩
     */
    NONE(255),
    /**
     * 使用 ZSTD 算法对事务进行压缩
     */
    ZSTD(0);

    private final int value;

    CompressionType(int value) {
        this.value = value;
    }

    public static CompressionType fromValue(long value) {
        for (CompressionType type : values()) {
            if (type.value == value) {
                return type;
            }
        }
        throw new IllegalArgumentException("unexpected compression type value:" + value);
    }

    public int getValue() {
        return value;
    }
}
