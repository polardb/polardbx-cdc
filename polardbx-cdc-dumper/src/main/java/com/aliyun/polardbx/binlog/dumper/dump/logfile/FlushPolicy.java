/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

import com.aliyun.polardbx.binlog.error.PolardbxException;

public enum FlushPolicy {
    /**
     * 每个事务flush一次
     */
    FlushPerTxn(0),
    /**
     * 定时flush
     */
    FlushAtInterval(1);

    private final int value;

    FlushPolicy(int value) {// 编写枚举的构造函数，这里的构造方法只能是私有的
        this.value = value;
    }

    public static FlushPolicy parseFrom(int value) { // 将数值转换成枚举值
        switch (value) {
        case 0:
            return FlushPerTxn;
        case 1:
            return FlushAtInterval;
        default:
            throw new PolardbxException("invalid flush policy.");
        }
    }

    public int getValue() { // 将枚举值转换成数值
        return this.value;
    }
}
