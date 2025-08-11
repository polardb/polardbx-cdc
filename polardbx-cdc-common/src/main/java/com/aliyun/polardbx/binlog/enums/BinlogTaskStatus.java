/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.enums;

/**
 * @author yudong
 * @since 2022/12/19 17:05
 **/
public enum BinlogTaskStatus {
    /**
     * task正在运行
     */
    RUNNING,
    /**
     * task没有运行
     */
    STOPPED;
}
