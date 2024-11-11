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
public class CollectException extends PolardbxException {

    public CollectException() {
    }

    public CollectException(String message) {
        super(message);
    }

    public CollectException(String message, Throwable cause) {
        super(message, cause);
    }

    public CollectException(Throwable cause) {
        super(cause);
    }

    public CollectException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
