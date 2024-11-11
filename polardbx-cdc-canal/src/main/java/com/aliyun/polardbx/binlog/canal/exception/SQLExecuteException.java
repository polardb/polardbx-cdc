/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.exception;

/**
 * @author chengjin.lyf on 2020/7/14 3:42 下午
 * @since 1.0.25
 */
public class SQLExecuteException extends RuntimeException {

    public SQLExecuteException(String message) {
        super(message);
    }

    public SQLExecuteException(String message, Throwable cause) {
        super(message, cause);
    }

    public SQLExecuteException(Throwable cause) {
        super(cause);
    }
}
