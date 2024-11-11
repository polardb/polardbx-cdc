/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.cluster.topology;

/**
 * 生成recover tso的方式
 *
 * @author yudong
 * @since 2022/11/15 14:55
 **/
public enum RecoverTsoType {
    /**
     * 从binlog_oss_record表中提取recover tso
     */
    BINLOG_RECORD,
    /**
     * 从binlog_system_config表中的latest_cursor字段提取recover tso
     */
    LATEST_CURSOR,
    /**
     * 关闭recover tso功能
     */
    NULL;

    public static RecoverTsoType typeOf(String name) {
        for (RecoverTsoType typeEnum : values()) {
            if (typeEnum.name().equalsIgnoreCase(name)) {
                return typeEnum;
            }
        }
        return NULL;
    }
}
