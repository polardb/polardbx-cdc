/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;

/**
 * This class implements a set of DBMS row data, including the metadata. <br />
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DefaultRowData extends DBMSRowData {

    private static final long serialVersionUID = 8852088558251208884L;

    protected Serializable[] values;

    public DefaultRowData() {
    }

    /**
     * Create a empty <code>DefaultRowData</code> object.
     */
    public DefaultRowData(int size) {
        this.values = new Serializable[size];
    }

    /**
     * Create a fullfill <code>DefaultRowData</code> object.
     */
    public DefaultRowData(Serializable[] values) {
        this.values = values;
    }

    /**
     * Retrieves the value of the designated column in the current row data.
     *
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     * @return The column value of current row data.
     */
    public Serializable getRowValue(int columnIndex) {
        return values[columnIndex - 1];
    }

    /**
     * Set the value of the designated column in the current row data.
     *
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     * @param value The column value of current row data.
     */
    public void setRowValue(int columnIndex, Serializable value) {
        Serializable[] values = this.values;
        final int valueIndex = columnIndex - 1;
        if (valueIndex < 0 || valueIndex >= values.length) {
            throw new IllegalArgumentException("Column index out of range: " + columnIndex + ", current columns: "
                + values.length);
        }
        values[valueIndex] = value;
    }

    /**
     * Retrieves all values in the current row data.
     */
    public Serializable[] getValues() {
        return values;
    }

    /**
     * Shicai.xsc Need setter for JSON parse
     */
    @Override
    public void setValues(Serializable[] values) {
        this.values = values;
    }
}
