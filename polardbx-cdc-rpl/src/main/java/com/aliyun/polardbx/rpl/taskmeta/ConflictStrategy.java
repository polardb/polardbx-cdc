/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

public enum ConflictStrategy {
    INTERRUPT,

    IGNORE,

    OVERWRITE,

    DIRECT_OVERWRITE
}
