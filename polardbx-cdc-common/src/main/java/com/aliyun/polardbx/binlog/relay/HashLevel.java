/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.relay;

/**
 * created by ziyang.lb
 **/
public enum HashLevel {
    /**
     * database level
     */
    DATABASE,
    /**
     * table level
     */
    TABLE,
    /**
     * record level
     */
    RECORD;
}
