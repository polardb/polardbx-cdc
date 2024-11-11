/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.exception;

import com.aliyun.polardbx.binlog.error.PolardbxException;

/**
 * @author chengjin.lyf on 2020/7/14 3:39 下午
 * @since 1.0.25
 */
public class CanalParseException extends PolardbxException {

    public CanalParseException(String message) {
        super(message);
    }

    public CanalParseException(String message, Throwable cause) {
        super(message, cause);
    }

    public CanalParseException(Throwable cause) {
        super(cause);
    }

    public CanalParseException() {
        super();
    }
}
