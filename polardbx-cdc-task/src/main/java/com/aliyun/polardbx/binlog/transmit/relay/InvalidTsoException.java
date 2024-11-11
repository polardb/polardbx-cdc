/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

/**
 * created by ziyang.lb
 **/
public class InvalidTsoException extends Exception {
    public InvalidTsoException() {
    }

    public InvalidTsoException(String message) {
        super(message);
    }

    public InvalidTsoException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidTsoException(Throwable cause) {
        super(cause);
    }

    public InvalidTsoException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
