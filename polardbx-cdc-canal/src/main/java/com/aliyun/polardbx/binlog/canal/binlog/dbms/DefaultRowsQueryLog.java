/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines a SQL statement from query-log that must be replicated. Default implementation of {@link DefaultQueryLog
 * DefaultQueryLog}
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DefaultRowsQueryLog extends DBMSEvent {

    private static final long serialVersionUID = -3393475008857141186L;
    protected String rowsQuery;
    private DBMSAction action;

    public DefaultRowsQueryLog(String rowsQuery, DBMSAction action) {
        this.rowsQuery = rowsQuery;
        this.action = action;
    }

    public String getRowsQuery() {
        return rowsQuery;
    }

    @Override
    public DBMSAction getAction() {
        return action;
    }

    @Override
    public String getSchema() {
        return null;
    }

    @Override
    public void setSchema(String schema) {

    }

    @Override
    public List<? extends DBMSOption> getOptions() {
        return new ArrayList<>();
    }

    @Override
    public void setOptionValue(String name, Serializable value) {
    }
}
