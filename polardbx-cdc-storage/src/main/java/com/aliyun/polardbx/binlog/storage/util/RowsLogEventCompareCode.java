/*
 * *
 *  * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 *  * All rights reserved.
 *  * <p>
 *  * Licensed under the Server Side Public License v1 (SSPLv1).
 *
 */

package com.aliyun.polardbx.binlog.storage.util;

public enum RowsLogEventCompareCode {
    /**
     * 两事件修改行完全不同
     */
    TOTALLY_NOT_EQUAL,
    /**
     * 右事件修改行完全包含左事件修改行
     */
    RIGHT_CONTAINS,
    /**
     * 左事件修改行完全包含右事件修改行
     */
    LEFT_CONTAINS,
    /**
     * 两事件修改行部分相同
     */
    PARTLY_EQUAL,
    /**
     * 两事件修改行完全相同
     */
    TOTALLY_EQUAL,
}
