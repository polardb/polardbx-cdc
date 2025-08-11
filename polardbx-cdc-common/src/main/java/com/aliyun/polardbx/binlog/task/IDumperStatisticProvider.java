/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.task;

import com.aliyun.polardbx.binlog.domain.BinlogCursor;

/**
 * @author ziyang.lb
 **/
public interface IDumperStatisticProvider {

    /**
     * 获取最新的binlog更新位置
     */
    BinlogCursor getLatestFileCursor();

    /**
     * 获取Dumper收到xid event时解析出的时间戳与System.currentTimeMillis()的差值
     */
    long getDumperDelay();
}
