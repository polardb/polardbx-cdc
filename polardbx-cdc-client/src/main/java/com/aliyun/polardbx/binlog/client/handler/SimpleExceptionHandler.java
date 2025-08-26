/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.handler;

import com.aliyun.polardbx.binlog.client.ClientHealthChecker;
import com.aliyun.polardbx.binlog.client.LogEventWrapper;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.lmax.disruptor.ExceptionHandler;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SimpleExceptionHandler implements ExceptionHandler<LogEventWrapper>, ClientHealthChecker {

    private Throwable t;

    @Override
    public void handleEventException(Throwable ex, long sequence, LogEventWrapper event) {
        this.t = ex;
        log.error("handle exception with {}", event, ex);
    }

    @Override
    public void handleOnStartException(Throwable ex) {
        this.t = ex;
        log.error("start exception ", ex);
    }

    @Override
    public void handleOnShutdownException(Throwable ex) {
        this.t = ex;
        log.error("shutdown exception ", ex);
    }

    @Override
    public void check() {
        if (t != null) {
            throw new PolardbxException(t);
        }
    }
}
