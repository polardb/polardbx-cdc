/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dumper.dump.logfile;

/**
 * @author yudong
 * @since 2023/1/16 19:54
 **/
public enum DumperSlaveStartMode {
    /**
     * 从远端存储上下载binlog，然后拷贝master的binlog
     */
    DOWNLOAD,
    /**
     * 不从远端存储上下载binlog，直接拷贝master的binlog
     */
    SYNC,
    /**
     * 以上两种的随机值，用于测试
     */
    RANDOM;

    public static DumperSlaveStartMode typeOf(String name) {
        for (DumperSlaveStartMode mode : values()) {
            if (mode.name().equals(name)) {
                return mode;
            }
        }
        return null;
    }
}
