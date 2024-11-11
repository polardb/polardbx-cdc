/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.metadata;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * created by ziyang.lb
 */
@Data
@Builder
public class ColumnDetail {
    String columnName, columnType;
    boolean dropped;//drop标志，新建的时候默认false
    List defaultValues;
}
