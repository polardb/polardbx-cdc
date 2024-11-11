/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * created by ziyang.lb
 */
@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DDLRecord {
    private Long id;
    private Long jobId;
    private String sqlKind;
    private String schemaName;
    private String tableName;
    private String ddlSql;
    private String metaInfo;
    private int visibility;
    private DDLExtInfo extInfo;
}
