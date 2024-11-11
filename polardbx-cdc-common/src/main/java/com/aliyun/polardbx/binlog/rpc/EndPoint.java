/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.rpc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * created by ziyang.lb
 **/
@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class EndPoint {
    private String host;
    private int port;
}
