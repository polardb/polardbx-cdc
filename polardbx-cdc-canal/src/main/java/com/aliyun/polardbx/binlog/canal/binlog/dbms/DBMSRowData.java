/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
