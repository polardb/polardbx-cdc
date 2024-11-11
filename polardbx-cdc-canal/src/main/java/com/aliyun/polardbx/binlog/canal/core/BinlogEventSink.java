/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core;

import java.util.List;

import com.aliyun.polardbx.binlog.canal.core.model.MySQLDBMSEvent;

/**
 * 数据解析之后的回调机制
 *
 * @author agapple 2017年7月19日 下午2:13:43
 * @since 3.2.5
 */
public interface BinlogEventSink {

    /**
     * 提交数据
     */
    boolean sink(List<MySQLDBMSEvent> events);

    /**
     * 通知异常
     */
    boolean sink(Throwable e);
}
