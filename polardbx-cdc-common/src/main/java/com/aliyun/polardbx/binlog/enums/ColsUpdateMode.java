/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.enums;

/**
 * 控制Replica 执行UPDATE时更新列的模式
 *
 * @author zimian
 */
public enum ColsUpdateMode {
    /**
     * 所有列都将被更新
     */
    ALL,
    /**
     * 所有变更前后值不一致的列以及类型是timestamp的列将被更新
     */
    TIMESTAMP,
    /**
     * 所有变更前后不一致的列以及列附加有ON UPDATE属性的列将被更新
     */
    ONUPDATE
}
