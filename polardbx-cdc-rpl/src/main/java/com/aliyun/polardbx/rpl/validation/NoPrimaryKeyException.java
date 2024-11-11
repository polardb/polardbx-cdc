/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.validation;

public class NoPrimaryKeyException extends RuntimeException {

    public NoPrimaryKeyException(String message) {
        super(message);
    }
}

