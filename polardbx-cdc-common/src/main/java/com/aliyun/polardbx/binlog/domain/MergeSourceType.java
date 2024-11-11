/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

/**
 * Created by ziyang.lb
 **/
public enum MergeSourceType {
    /**
     * 数据库二进制文件
     */
    BINLOG,
    /**
     * rpc服务
     */
    RPC,
    /**
     * 模拟器
     */
    MOCK
}
