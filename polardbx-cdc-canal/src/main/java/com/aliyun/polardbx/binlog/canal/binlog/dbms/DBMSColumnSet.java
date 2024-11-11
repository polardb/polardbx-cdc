/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * This class defines a set of SQL column information like
 * {@link java.sql.ResultSetMetaData}. <br />
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public abstract class DBMSColumnSet implements Serializable {
    private static final long serialVersionUID = -2626614478269989000L;

    protected transient Map<String, DBMSColumn> columnDict;
    protected transient List<DBMSColumn> primaryKey;
    protected transient List<DBMSColumn> uniqueKey;

    /**
     * Init column indexes.
     */
    protected void initColumns(List<? extends DBMSColumn> columns) {
        for (int index = 0; index < columns.size(); index++) {
            DBMSColumn column = columns.get(index);
            column.setColumnIndex(index + 1);
        }
    }

    /**
     * Build column information as needed.
     */
    public void buildColumns(List<? extends DBMSColumn> columns) {
        HashMap<String, DBMSColumn> columnDict = new HashMap<String, DBMSColumn>(
            columns.size(), 1.0f); // load factor 1.0 for fixed hash map
        LinkedList<DBMSColumn> primaryKey = null;
        LinkedList<DBMSColumn> uniqueKey = null;
        for (int index = 0; index < columns.size(); index++) {
            DBMSColumn column = columns.get(index);
            column.setColumnIndex(index + 1);
            if (column.isPrimaryKey()) {
                if (primaryKey == null) {
                    primaryKey = new LinkedList<DBMSColumn>();
                }
                primaryKey.add(column);
            }
            if (column.isUniqueKey()) {
                if (uniqueKey == null) {
                    uniqueKey = new LinkedList<DBMSColumn>();
                }
                uniqueKey.add(column);
            }
            columnDict.put(column.getName().toLowerCase(), column);
        }
        this.columnDict = columnDict;
        this.primaryKey = primaryKey;
        this.uniqueKey = uniqueKey;
    }

    /**
     * Return all columns in object.
     */
    public abstract List<? extends DBMSColumn> getColumns();

    /**
     * Return the primary key columns if it exists, return <code>null</code> in
     * the otherwise.
     */
    public List<DBMSColumn> getPrimaryKey() {
        if (columnDict == null) {
            buildColumns(getColumns());
        }
        if (primaryKey != null) {
            return primaryKey;
        }
        return Collections.emptyList();
    }

    public void setPrimaryKey(List<DBMSColumn> primaryKey) {
        this.primaryKey = primaryKey;
    }

    /**
     * Return the unique key columns if it exists, return <code>null</code> in
     * the otherwise.
     */
    public List<DBMSColumn> getUniqueKey() {
        if (columnDict == null) {
            buildColumns(getColumns());
        }
        if (uniqueKey != null) {
            return uniqueKey;
        }
        return Collections.emptyList();
    }

    public void setUniqueKey(List<DBMSColumn> uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    /**
     * Returns the number of columns in this <code>DBMSColumns</code>
     */
    public int getColumnSize() {
        return getColumns().size();
    }

    /**
     * Return the column by index. The first column index is 1, the second is 2,
     * ...
     */
    public DBMSColumn getColumn(int columnIndex) {
        return getColumns().get(columnIndex - 1);
    }

    /**
     * Return the column by name.
     */
    public DBMSColumn findColumn(String columnName) {
        if (columnDict == null) {
            buildColumns(getColumns());
        }
        return columnDict.get(columnName.toLowerCase());
    }

    /**
     * {@inheritDoc}
     *
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getColumns().hashCode();
    }

    /**
     * {@inheritDoc}
     *
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other instanceof DBMSColumnSet) {
            return this.equals((DBMSColumnSet) other);
        }
        return false;
    }

    /**
     * Return true if the column is equals other.
     */
    public boolean equals(DBMSColumnSet other) {
        if (other == null) {
            return false;
        }
        List<? extends DBMSColumn> columns = getColumns();
        List<? extends DBMSColumn> otherColumns = other.getColumns();
        if (columns.size() != otherColumns.size()) {
            return false;
        }
        for (int index = 0; index < columns.size(); index++) {
            if (!columns.get(index).equals(otherColumns.get(index))) {
                return false;
            }
        }
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @see Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        for (DBMSColumn column : getColumns()) {
            builder.append(column);
            builder.append('\n');
        }
        return builder.toString();
    }
}