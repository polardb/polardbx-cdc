/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

/**
 * Created by ShuGuang
 **/
public enum DumperType {
    MASTER("M"),
    SLAVE("S"),
    XSTREAM("X");
    String name;

    DumperType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
