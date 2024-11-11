/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import org.apache.commons.lang3.StringUtils;

public enum QueryLogFlags2Enum {

    OPTION_NO_FOREIGN_KEY_CHECKS(1 << 26),
    OPTION_RELAXED_UNIQUE_CHECKS(1 << 27);
    private long value;

    QueryLogFlags2Enum(long value) {
        this.value = value;
    }

    public static long getFlags2Value(String flags2) {
        long flags2Value = 0;
        if (StringUtils.isBlank(flags2)) {
            return flags2Value;
        }
        for (QueryLogFlags2Enum flags2Enum : values()) {
            if (flags2.contains(flags2Enum.name())) {
                flags2Value |= flags2Enum.value;
            }
        }
        return flags2Value;
    }
}
