/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.exception;

import com.aliyun.polardbx.binlog.error.PolardbxException;

public class BinlogFileUnreachableException extends PolardbxException {
    public BinlogFileUnreachableException() {
    }

    public BinlogFileUnreachableException(String message) {
        super(message);
    }

    public BinlogFileUnreachableException(String message, Throwable cause) {
        super(message, cause);
    }

    public BinlogFileUnreachableException(Throwable cause) {
        super(cause);
    }

    public BinlogFileUnreachableException(String message, Throwable cause, boolean enableSuppression,
                                          boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
