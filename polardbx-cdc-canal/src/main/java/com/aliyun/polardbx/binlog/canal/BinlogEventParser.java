/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

import com.aliyun.polardbx.binlog.canal.core.BinlogEventSink;
import com.aliyun.polardbx.binlog.canal.core.model.AuthenticationInfo;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;

/**
 * 数据解析工具
 *
 * <pre>
 * 拷贝和重构了canal的部分代码，考虑点:
 * 1. jingwei本身有一套位点管理的系统 + 内部包含基于diamond的主备切换机制，对于canal的需求主要就是一个binlog解析的能力
 * 2. jingwei的数据模型有比较大的用户基础，需要将canal的数据模型进行一次替换，包括以前的订阅表filter条件
 * </pre>
 *
 * @author agapple 2017年7月19日 下午3:26:02
 * @since 3.2.5
 */
public interface BinlogEventParser {

    void addFilter(LogEventFilter logEventFilter);

    /**
     * 指定位点启动
     */
    void start(AuthenticationInfo master, BinlogPosition position);

    void start(AuthenticationInfo master, BinlogPosition position, BinlogEventSink eventSink);

    void stop();
}
