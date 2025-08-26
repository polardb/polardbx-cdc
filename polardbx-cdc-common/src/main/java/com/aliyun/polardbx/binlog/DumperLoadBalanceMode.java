/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

/**
 * @author zm
 */

public enum DumperLoadBalanceMode {
    /**
     * 选链路数最少的节点
     */
    COUNT(0),
    /**
     * 加权选出链路数,CPU,BPS占用较少的节点
     */
    MIXED(1),
    /**
     * 强制指定dumper
     */
    ASSIGNED(2),
    /**
     * 随机
     */
    RANDOM(3);

    private final int value;

    DumperLoadBalanceMode(int value) {
        this.value = value;
    }

    public static DumperLoadBalanceMode typeOf(int value) {
        for (DumperLoadBalanceMode mode : values()) {
            if (mode.value == value) {
                return mode;
            }
        }
        return null;
    }
}
