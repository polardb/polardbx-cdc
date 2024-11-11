/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal;

/**
 * @author chengjin.lyf on 2020/7/15 4:39 下午
 * @since 1.0.25
 */
public interface LogEventFilter<T extends HandlerEvent> {

    void handle(T event, HandlerContext context) throws Exception;

    void onStart(HandlerContext context);

    void onStop();

    void onStartConsume(HandlerContext context);
}
