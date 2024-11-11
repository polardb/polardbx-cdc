/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.enums;

/**
 * @author yudong
 * @since 2023/5/4 11:08
 **/
public enum NodeStatus {
    /**
     * 节点可用
     */
    AVAILABLE(0);

    private final int value;

    NodeStatus(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
