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
public class TableDetail {
    String tableName;
    String createSql;
    List<ColumnDetail> columnDetailList;
}
