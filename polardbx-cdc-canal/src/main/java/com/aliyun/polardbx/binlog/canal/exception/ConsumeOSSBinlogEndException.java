/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.exception;

import com.aliyun.polardbx.binlog.error.PolardbxException;

public class ConsumeOSSBinlogEndException extends PolardbxException {
    public ConsumeOSSBinlogEndException() {
    }

    public ConsumeOSSBinlogEndException(String message) {
        super(message);
    }

    public ConsumeOSSBinlogEndException(String message, Throwable cause) {
        super(message, cause);
    }

    public ConsumeOSSBinlogEndException(Throwable cause) {
        super(cause);
    }
}
