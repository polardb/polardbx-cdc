/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.relay;

/**
 * create by ziyang.lb
 **/
public enum DdlRouteMode {
    /**
     * 定向路由给单个binlog stream
     */
    SINGLE,
    /**
     * 广播到所有binlog stream
     */
    BROADCAST;
}
