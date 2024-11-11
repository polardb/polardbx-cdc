/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.storage;

/**
 * created by ziyang.lb
 * 持久化模式
 **/
public enum PersistMode {
    /**
     * 自动模式，根据内存使用情况和事务大小等，动态判断
     */
    AUTO,
    /**
     * 强制模式，所有数据强制持久化
     */
    FORCE,
    /**
     * 随机模式，随机选择AUTO或者FORCE，一般测试场景使用
     */
    RANDOM
}
