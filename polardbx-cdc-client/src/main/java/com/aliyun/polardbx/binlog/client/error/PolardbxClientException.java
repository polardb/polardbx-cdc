/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.error;

public class PolardbxClientException extends RuntimeException {
    public PolardbxClientException(String message) {
        super(message);
    }

    public PolardbxClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
