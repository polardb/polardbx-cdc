/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.rest.resources.request;

import lombok.Data;

/**
 * @author fanfei
 */
@Data
public class FlashBackTaskParameter {
    /**
     * [必填] 逻辑库名
     */
    private String logicDbName;
    /**
     * [必填] 开始时间
     */
    private long startTimestamp;
    /**
     * [必填] 结束时间
     */
    private long endTimestamp;
    /**
     * [必填] 原始SQL or 回滚SQL
     */
    private boolean isMirror;
    /**
     * [选填] traceID
     */
    private String traceId;
    /**
     * [选填] 逻辑表名
     */
    private String logicTableName;
    /**
     * [选填] 误操作SQL类型
     */
    private String sqlType;
    /**
     * [选填] 是否注入故障
     */
    private boolean injectTrouble;
}
