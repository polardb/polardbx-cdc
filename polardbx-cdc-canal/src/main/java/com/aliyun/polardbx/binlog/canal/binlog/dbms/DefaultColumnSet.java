/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.util.List;

/**
 * This class creates a default SQL column set implementation. <br />
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DefaultColumnSet extends DBMSColumnSet {
    private static final long serialVersionUID = -3429762238191668175L;

    protected List<? extends DBMSColumn> columns;

    public DefaultColumnSet() {
    }

    /**
     * Create a new <code>DefaultColumnSet</code> object.
     */
    public DefaultColumnSet(List<? extends DBMSColumn> columns) {
        this.columns = columns;
        initColumns(columns);
    }

    /**
     * Return all columns in object.
     */
    public List<? extends DBMSColumn> getColumns() {
        return columns;
    }

    /**
     *
     */
    public void setColumns(List<? extends DBMSColumn> columns) {
        this.columns = columns;
    }
}