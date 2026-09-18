/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

/**
 * LogDecoder进行decode的几种模式
 *
 * @author zm
 */
public enum DecodeMode {
    /**
     * 仅在returning的fix delete时使用的decode模式
     * 尽可能减少parse的复杂度
     */
    PART_RETURNING,
    /**
     * 默认模式
     */
    NORMAL
}
