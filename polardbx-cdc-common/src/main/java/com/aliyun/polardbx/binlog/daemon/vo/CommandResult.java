/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.vo;

import lombok.Data;

/**
 * Created by ShuGuang
 */
@Data
public class CommandResult {
    //exit code
    int code;
    //执行结果或者异常
    String msg;

}
