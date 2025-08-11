/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.enums;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;

import java.util.concurrent.ThreadLocalRandom;

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
    LINDORM,
    /**
     * 使用S3作为远端备份存储
     */
    S3;

    public static BinlogBackupType typeOf(String name) {
        for (BinlogBackupType typeEnum : values()) {
            if (typeEnum.name().equalsIgnoreCase(name)) {
                if (DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV)) {
                    // 实验室用
                    if (typeEnum == OSS) {
                        return ThreadLocalRandom.current().nextBoolean() ? OSS : S3;
                    }
                }
                return typeEnum;
            }
        }
        return NULL;
    }
}
