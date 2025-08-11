/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.relay;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;

import static com.aliyun.polardbx.binlog.ConfigKeys.BINLOGX_TRANSMIT_HASH_LEVEL;

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
    RECORD,
    /**
     * datanode level, one stream per datanode
     */
    DATANODE;

    public static HashLevel from(String value) {
        return HashLevel.valueOf(value.toUpperCase());
    }

    public static HashLevel getCurrentHashLevel() {
        return HashLevel.from(DynamicApplicationConfig.getString(BINLOGX_TRANSMIT_HASH_LEVEL));
    }
}
