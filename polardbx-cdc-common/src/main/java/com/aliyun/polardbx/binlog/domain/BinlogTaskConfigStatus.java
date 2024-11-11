/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

/**
 * Created by ShuGuang
 */
public interface BinlogTaskConfigStatus {
    int DISABLE_AUTO_SCHEDULE = 0;
    int ENABLE_AUTO_SCHEDULE = 1;
}
