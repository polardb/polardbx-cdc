/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

/**
 * Created by ziyang.lb
 **/
public enum NodeRole {
    MASTER("M"),
    SLAVE("S");
    String name;

    NodeRole(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
