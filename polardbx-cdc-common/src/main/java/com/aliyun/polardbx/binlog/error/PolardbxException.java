/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.error;

/**
 * Created by ziyang.lb
 **/
public class PolardbxException extends RuntimeException {

    public PolardbxException() {
        super();
    }

    public PolardbxException(String message) {
        super(message);
    }

    public PolardbxException(String message, Throwable cause) {
        super(message, cause);
    }

    public PolardbxException(Throwable cause) {
        super(cause);
    }

    protected PolardbxException(String message, Throwable cause, boolean enableSuppression,
                                boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
