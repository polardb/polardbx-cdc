/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.taskmeta;

import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import lombok.Data;

import static com.aliyun.polardbx.binlog.ConfigKeys.RPL_PERSIST_ENABLED;

/**
 * created by ziyang.lb
 **/
@Data
public class PersistConfig {
    //持久化相关
    private boolean forcePersist = false;
    private boolean supportPersist = DynamicApplicationConfig.getBoolean(RPL_PERSIST_ENABLED);
    private long transPersistCheckIntervalMs = 1000;
    private double transPersistMemoryThreshold = 0.85;
    private long transPersistRangeMaxItemSize = 10000;
    private long transPersistRangeMaxByteSize = 10 * 1024 * 1024;
    private long eventPersistThreshold = 1024 * 1024;
}
