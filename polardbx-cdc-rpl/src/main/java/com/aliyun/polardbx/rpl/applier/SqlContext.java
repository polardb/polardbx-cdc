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
public class SqlContext {

    protected String sql;
    protected String dstSchema;
    protected String dstTable;
    protected String fullTable;
    protected List<Serializable> params;
    protected String sqlMode;
    protected boolean asyncDdl;
    protected String ddlEventSchema;
    protected int ddlParallelSeq = 0;
    protected boolean syncPoint = false;
    protected String fpOverrideNow;
    protected Throwable exception;

    public SqlContext(String sql, String dstSchema, String dstTable, List<Serializable> params) {
        this.sql = sql;
        this.dstSchema = dstSchema;
        this.dstTable = dstTable;
        this.params = params;
        this.fullTable = dstSchema + "." + dstTable;
    }

    public SqlContext(String sql, String dstSchema, String dstTable, List<Serializable> params, String sqlMode) {
        this.sql = sql;
        this.dstSchema = dstSchema;
        this.dstTable = dstTable;
        this.params = params;
        this.fullTable = dstSchema + "." + dstTable;
        this.sqlMode = sqlMode;
    }

    @Override
    public String toString() {
        return "SqlContext{" +
            "sql='" + sql + '\'' +
            ", dstSchema='" + dstSchema + '\'' +
            ", dstTable='" + dstTable + '\'' +
            ", fullTable='" + fullTable + '\'' +
            ", params=" + params +
            ", sqlMode='" + sqlMode + '\'' +
            ", asyncDdl=" + asyncDdl +
            ", ddlEventSchema='" + ddlEventSchema + '\'' +
            ", syncPoint=" + syncPoint + '\'' +
            '}';
    }
}
