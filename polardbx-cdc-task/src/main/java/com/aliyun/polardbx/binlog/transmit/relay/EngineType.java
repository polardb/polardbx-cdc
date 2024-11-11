/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.transmit.relay;

/**
 * create by ziyang.lb
 **/
public enum EngineType {
    /**
     * 普通文件，顺序读写性能最好
     */
    FILE,
    /**
     * rocksdb，操作简便快捷，写速度快，读性能稍差
     */
    ROCKSDB,
    /**
     * 随机模式，实验室测试使用
     */
    RANDOM
}
