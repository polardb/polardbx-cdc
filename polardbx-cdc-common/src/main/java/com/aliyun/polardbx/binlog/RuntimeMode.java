/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

/**
 * @author ziyang.lb
 * 运行时模式
 **/
public enum RuntimeMode {
    /**
     * 本地模式
     */
    LOCAL,
    /**
     * 本地单体模式，Daemon、Dumper、Task跑在一个进程
     */
    LOCAL_SINGLE,
    /**
     * 集群模式
     */
    CLUSTER;

    public static boolean isLocalMode(RuntimeMode mode) {
        return mode == LOCAL || mode == LOCAL_SINGLE;
    }
}
