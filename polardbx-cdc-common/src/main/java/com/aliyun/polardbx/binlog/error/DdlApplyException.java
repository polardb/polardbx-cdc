/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.error;

public class DdlApplyException extends RuntimeException {
    public DdlApplyException() {
    }

    public DdlApplyException(String message) {
        super(message);
    }

    public DdlApplyException(String message, Throwable cause) {
        super(message, cause);
    }

    public DdlApplyException(Throwable cause) {
        super(cause);
    }

    public DdlApplyException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
