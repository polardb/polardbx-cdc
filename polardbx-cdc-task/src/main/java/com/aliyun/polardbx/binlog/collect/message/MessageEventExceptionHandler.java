/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.collect.message;

import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.lmax.disruptor.ExceptionHandler;

/**
 *
 **/
public class MessageEventExceptionHandler implements ExceptionHandler<Object> {

    @Override
    public void handleEventException(final Throwable ex, final long sequence, final Object event) {
        // 异常上抛，否则processEvents的逻辑会默认会mark为成功执行，有丢数据风险
        throw new PolardbxException(ex);
    }

    @Override
    public void handleOnStartException(final Throwable ex) {
    }

    @Override
    public void handleOnShutdownException(final Throwable ex) {
    }
}
