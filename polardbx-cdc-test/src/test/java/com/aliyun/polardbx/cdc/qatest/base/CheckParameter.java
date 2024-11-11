/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.base;

import com.aliyun.polardbx.binlog.relay.HashLevel;
import lombok.Builder;
import lombok.Data;

import java.util.function.Supplier;

/**
 * Created by ziyang.lb
 **/
@Data
@Builder
public class CheckParameter {
    private String dbName;
    private String tbName;
    private boolean directCompareDetail;
    private HashLevel expectHashLevel;
    private boolean compareDetailOneByOne;
    private long loopWaitTimeoutMs = -1L;
    private Supplier<String> contextInfoSupplier;
}
