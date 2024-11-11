/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;

/**
 * This class defines a set of DBMS row data, including the metadata. <br />
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public abstract class DBMSRowData implements Serializable {
    private static final long serialVersionUID = 2947113516104181365L;

    /**
     * Retrieves the value of the designated column in the current row data.
     *
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     * @return The column value of current row data.
     */
    public abstract Serializable getRowValue(int columnIndex);

    /**
     * Set the value of the designated column in the current row data.
     *
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     * @param value The column value of current row data.
     */
    public abstract void setRowValue(int columnIndex, Serializable value);

    /**
     * Retrieves the value of the designated column in the current row data.
     *
     * @param column The designated column.
     * @return The column value of current row data.
     */
    public Serializable getRowValue(DBMSColumn column) {
        return getValues()[column.getColumnIndex() - 1];
    }

    /**
     * Set the value of the designated column in the current row data.
     *
     * @param column The designated column.
     * @param value The column value of current row data.
     */
    public void setRowValue(DBMSColumn column, Serializable value) {
        setRowValue(column.getColumnIndex(), value);
    }

    /**
     * Retrieves the values in the current row data.
     */
    public abstract Serializable[] getValues();

    /**
     * Shicai.xsc Need setter for JSON parse
     */
    public abstract void setValues(Serializable[] values);

    /**
     * {@inheritDoc}
     *
     * @see Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder(getClass().getName());
        Serializable[] values = getValues();
        builder.append(": ");
        for (int index = 0; index < values.length; index++) {
            if (index != 0) {
                builder.append(", ");
            }
            builder.append(values[index]);
        }
        return builder.toString();
    }
}
