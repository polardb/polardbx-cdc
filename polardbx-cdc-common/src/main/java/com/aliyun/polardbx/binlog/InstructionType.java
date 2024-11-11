/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

public enum InstructionType {
    /**
     * Cdc初始化
     */
    CdcStart,
    /**
     * 存储实例发生了变更
     */
    StorageInstChange,
    /**
     * 元数据镜像
     */
    MetaSnapshot,
    /**
     * CDC 配置参数变更
     */
    CdcEnvConfigChange,
    /**
     * 滚动binlog
     */
    FlushLogs;
}
