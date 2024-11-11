/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @author shicai.xsc 2020/12/1 22:06
 * @since 5.0.0.0
 */
@Data
public class SqlContextV2 {

    protected String sql;
    protected String dstSchema;
    protected String dstTable;
    protected String fullTable;
    protected Boolean succeed = false;
    protected List<List<Serializable>> paramsList;

    public SqlContextV2(String sql, String dstSchema, String dstTable, List<List<Serializable>> paramsList) {
        this.sql = sql;
        this.dstSchema = dstSchema;
        this.dstTable = dstTable;
        this.paramsList = paramsList;
        this.fullTable = dstSchema + "." + dstTable;
    }
}
