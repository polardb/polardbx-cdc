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

import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * This class defines a set of one or more row changes.
 *
 * @author Changyuan.lh
 * @version 1.0
 */
@Slf4j
public abstract class DBMSRowChange extends DBMSEvent {

    private static final long serialVersionUID = -5725119493653542602L;

    protected DBMSColumnSet columnSet;

    /**
     * Return the table name.
     */
    public abstract String getTable();

    /**
     * Change the table name.
     */
    public abstract void setTable(String table);

    /**
     * Return the column set in table.
     */
    public DBMSColumnSet getColumnSet() {
        return columnSet;
    }

    public void setColumnSet(DBMSColumnSet columnSet) {
        this.columnSet = columnSet;
    }

    /**
     * Return the primary key columns if it exists, return <code>null</code> in the otherwise.
     */
    public List<DBMSColumn> getPrimaryKey() {
        return columnSet.getPrimaryKey();
    }

    /**
     * Return the unique key columns if it exists, return <code>null</code> in the otherwise.
     */
    public List<DBMSColumn> getUniqueKey() {
        return columnSet.getUniqueKey();
    }

    /**
     * Returns the number of columns in table.
     */
    public int getColumnSize() {
        return columnSet.getColumnSize();
    }

    /**
     * Return all columns in table.
     */
    public List<? extends DBMSColumn> getColumns() {
        return columnSet.getColumns();
    }

    /**
     * Return the column by index. The first column index is 1, the second is 2, ...
     */
    public DBMSColumn getColumn(int columnIndex) {
        return columnSet.getColumn(columnIndex);
    }

    /**
     * Return the column by name.
     */
    public DBMSColumn findColumn(String columnName) {
        return columnSet.findColumn(columnName);
    }

    /**
     * Return the count of row data.
     */
    public abstract int getRowSize();

    /**
     * Return origin row-data by row number.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @return The origin row-data.
     */
    public abstract DBMSRowData getRowData(int rownum);

    /**
     * Return row-data by row number and column index.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     * @return The column value of origin row data.
     */
    public Serializable getRowValue(int rownum, int columnIndex) {
        return getRowData(rownum).getRowValue(columnIndex);
    }

    /**
     * Set row-data by row number and column index.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     * @param value The new value of designated column.
     */
    public void setRowValue(int rownum, int columnIndex, Serializable value) {
        getRowData(rownum).setRowValue(columnIndex, value);
    }

    /**
     * Return row-data by row number and column name.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param columnName The designated column.
     * @return The column value of origin row data.
     */
    public Serializable getRowValue(int rownum, String columnName) {
        DBMSColumn column = columnSet.findColumn(columnName);
        if (column != null) {
            return getRowValue(rownum, column.getColumnIndex());
        }
        return null;
    }

    public int getColumnIndex(String columnName) {
        DBMSColumn column = columnSet.findColumn(columnName);
        if (column != null) {
            return column.getColumnIndex();
        }
        log.error("columnName: {} not found, columnSet: {}.", columnName, columnSet);
        return -1;
    }

    /**
     * Set row-data by row number and column name.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param columnName The designated column.
     * @param value The new value of designated column.
     */
    public void setRowValue(int rownum, String columnName, Serializable value) {
        DBMSColumn column = columnSet.findColumn(columnName);
        if (column != null) {
            setRowValue(rownum, column.getColumnIndex(), value);
        }
    }

    /**
     * Return row-data by row number and column.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param column The designated column.
     * @return The column value of origin row data.
     */
    public Serializable getRowValue(int rownum, DBMSColumn column) {
        return getRowValue(rownum, column.getColumnIndex());
    }

    /**
     * Set row-data by row number and column.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param column The designated column.
     * @param value The new value of designated column.
     */
    public void setRowValue(int rownum, DBMSColumn column, Serializable value) {
        setRowValue(rownum, column.getColumnIndex(), value);
    }

    /**
     * Return column index changed flags in table.
     */
    public abstract BitSet getChangeIndexes();

    /**
     * Check the row value of the designated column is currently changed.
     *
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     */
    public abstract boolean hasChangeColumn(int columnIndex);

    /**
     * Mark the row value of the designated column is changed or not.
     *
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     * @param changed The designated column is changed.
     */
    protected abstract void setChangeColumn(int columnIndex, boolean changed);

    /**
     * Return all changed columns in table.
     */
    public List<? extends DBMSColumn> getChangeColumns() {
        List<DBMSColumn> changeColumns = new ArrayList<DBMSColumn>( // NL
            columnSet.getColumnSize());
        for (DBMSColumn column : columnSet.getColumns()) {
            if (hasChangeColumn(column.getColumnIndex())) {
                changeColumns.add(column);
            }
        }
        return changeColumns;
    }

    /**
     * Return changed row-data by row number.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @return The changed row-data.
     */
    public abstract DBMSRowData getChangeData(int rownum);

    /**
     * Return changed row-data by row number and column index.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     * @return The column value of changed row data.
     */
    public Serializable getChangeValue(int rownum, int columnIndex) {
        if (hasChangeColumn(columnIndex)) {
            return getChangeData(rownum).getRowValue(columnIndex);
        }
        return getRowData(rownum).getRowValue(columnIndex);
    }

    /**
     * Set changed row-data by row number and column index.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param columnIndex The first oridinal index is 1, the second is 2, ...
     * @param value The new value of designated column.
     */
    public void setChangeValue(int rownum, int columnIndex, Serializable value) {
        getChangeData(rownum).setRowValue(columnIndex, value);
        setChangeColumn(columnIndex, true);
    }

    /**
     * Return changed row-data by row number and column name.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param columnName The designated column.
     * @return The column value of changed row data.
     */
    public Serializable getChangeValue(int rownum, String columnName) {
        DBMSColumn column = columnSet.findColumn(columnName);
        if (column != null) {
            return getChangeValue(rownum, column);
        }
        return null;
    }

    /**
     * Set changed row-data by row number and column name.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param columnName The designated column.
     * @param value The new value of designated column.
     */
    public void setChangeValue(int rownum, String columnName, Serializable value) {
        DBMSColumn column = columnSet.findColumn(columnName);
        if (column != null) {
            setChangeValue(rownum, column.getColumnIndex(), value);
        }
    }

    /**
     * Return changed row-data by row number and column.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param column The designated column.
     * @return The column value of changed row data.
     */
    public Serializable getChangeValue(int rownum, DBMSColumn column) {
        return getChangeValue(rownum, column.getColumnIndex());
    }

    /**
     * Set changed row-data by row number and column.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     * @param column The designated column.
     * @param value The new value of designated column.
     */
    public void setChangeValue(int rownum, DBMSColumn column, Serializable value) {
        setChangeValue(rownum, column.getColumnIndex(), value);
    }

    /**
     * Remove the row-data by row number. If changed row-data exist, it will be removed also.
     *
     * @param rownum The first row is 1, the second row is 2, ...
     */
    public abstract void removeRowData(int rownum);

    /**
     * {@inheritDoc}
     *
     * @see Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder( // NL
            getClass().getName());
        builder.append('(');
        builder.append("action: ");
        builder.append(this.getAction());
        builder.append(", schema: ");
        builder.append(this.getSchema());
        builder.append(",\n    table: ");
        builder.append(this.getTable());
        builder.append(",\n    column: ");
        for (DBMSColumn column : getColumns()) {
            builder.append("\n        ");
            builder.append(column);
        }
        builder.append("\n    values: ");
        for (int rownum = 1; rownum <= getRowSize(); rownum++) {
            DBMSRowData origin = getRowData(rownum);
            DBMSRowData change = getChangeData(rownum);
            if (change != null) {
                builder.append("\n        ");
                builder.append(rownum);
                builder.append(" origin: ");
                builder.append(origin);
                builder.append("\n        ");
                builder.append(rownum);
                builder.append(" change: ");
                builder.append(change);
            } else {
                builder.append("\n        ");
                builder.append(rownum);
                builder.append(": ");
                builder.append(origin);
            }
        }
        for (DBMSOption option : this.getOptions()) {
            builder.append("\n    option: ");
            builder.append(option);
        }
        builder.append(')');
        return builder.toString();
    }
}
