/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.enums;

/**
 * @author chengjin, yudong
 */
public enum BinlogBackupType {
    /**
     * 未开启远端备份
     */
    NULL,
    /**
     * 使用OSS作为远端备份存储
     */
    OSS,
    /**
     * 使用Lindorm作为远端备份存储
     */
    LINDORM;

    public static BinlogBackupType typeOf(String name) {
        for (BinlogBackupType typeEnum : values()) {
            if (typeEnum.name().equalsIgnoreCase(name)) {
                return typeEnum;
            }
        }
        return NULL;
    }
}
