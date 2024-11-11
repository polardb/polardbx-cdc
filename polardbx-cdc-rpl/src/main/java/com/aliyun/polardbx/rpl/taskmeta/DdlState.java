/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

/**
 * @author shicai.xsc 2021/4/6 16:26
 * @since 5.0.0.0
 */
public enum DdlState {
    NOT_START,

    RUNNING,

    SUCCEED,

    FAILED;

    public boolean isFinished() {
        return this == SUCCEED;
    }
}
