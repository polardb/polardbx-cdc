/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * Created by jiyue on 21-9-7.
 */
@Data
@Builder
@AllArgsConstructor
public class ResultCode<T> {

    private int code;
    private String msg;
    private T data;

    public ResultCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

}
