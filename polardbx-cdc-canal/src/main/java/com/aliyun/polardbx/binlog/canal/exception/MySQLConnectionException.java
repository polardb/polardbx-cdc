/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.exception;

import java.io.IOException;

/**
 * @author chengjin.lyf on 2020/7/14 3:37 下午
 * @since 1.0.25
 */
public class MySQLConnectionException extends IOException {

    public MySQLConnectionException(String message, Throwable cause) {
        super(message, cause);
    }

    public MySQLConnectionException(Throwable cause) {
        super(cause);
    }
}
