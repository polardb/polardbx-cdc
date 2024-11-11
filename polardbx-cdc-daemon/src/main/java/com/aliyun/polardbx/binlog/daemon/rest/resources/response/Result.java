/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class Result<T> {
    private int code;
    private String msg;
    private T data;
}
