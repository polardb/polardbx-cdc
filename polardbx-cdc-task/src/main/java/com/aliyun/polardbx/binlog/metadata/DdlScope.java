/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.metadata;

/**
 * description:
 * author: ziyang.lb
 * create: 2023-09-19 12:00
 **/
public enum DdlScope {

    Schema(0),

    Instance(1);

    int value;

    DdlScope(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
