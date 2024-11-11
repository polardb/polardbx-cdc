/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.exception;

/**
 * @author chengjin.lyf on 2020/7/14 3:58 下午
 * @since 1.0.25
 */
public class TableIdNotFoundException extends Exception {

    public TableIdNotFoundException() {
        super();
    }

    public TableIdNotFoundException(String message) {
        super(message);
    }

    public TableIdNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    public TableIdNotFoundException(Throwable cause) {
        super(cause);
    }

    protected TableIdNotFoundException(String message, Throwable cause, boolean enableSuppression,
                                       boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
