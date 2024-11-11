/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

public enum MarkType {
    /**
     * commit timestamp
     */
    CTS,
    /**
     * polardbx 私有ddl
     */
    PRIVATE_DDL
}
