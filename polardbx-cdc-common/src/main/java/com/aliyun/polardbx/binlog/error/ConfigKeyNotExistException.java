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
public class ConfigKeyNotExistException extends PolardbxException {
    public ConfigKeyNotExistException() {
    }

    public ConfigKeyNotExistException(String message) {
        super(message);
    }

    public ConfigKeyNotExistException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConfigKeyNotExistException(Throwable cause) {
        super(cause);
    }

    public ConfigKeyNotExistException(String message, Throwable cause, boolean enableSuppression,
                                      boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
