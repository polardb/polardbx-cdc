/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import java.io.Serializable;

/**
 * This class defines a SQL column information. <br />
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public abstract class DBMSColumn implements Serializable {
    private static final long serialVersionUID = 3756103775996253511L;

    protected transient int columnIndex;

    /**
     * Return the column name.
     */
    public abstract String getName();

    /**
     * Return the current column index.
     */
    public int getColumnIndex() {
        return columnIndex;
    }

    /**
     * Change the current column index.
     */
    public void setColumnIndex(int columnIndex) {
        this.columnIndex = columnIndex;
    }

    /**
     * Return the ordinal column index.
     */
    public abstract int getOrdinalIndex();

    /**
     * Return the column SQL type. See {@link java.sql.Types} for type details.
     *
     * @see java.sql.Types
     */
    public abstract int getSqlType();

    /**
     * Return true if the column is singned.
     */
    public abstract boolean isSigned();

    /**
     * Return true if the column is <code>NULL</code> column.
     */
    public abstract boolean isNullable();

    /**
     * Return true if the column is a part of primary key.
     */
    public abstract boolean isPrimaryKey();

    /**
     * Return true if the column is a part of unique key.
     */
    public abstract boolean isUniqueKey();

    public abstract boolean isGenerated();

    public abstract boolean isRdsImplicitPk();

    public abstract boolean isOnUpdate();

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
        if (other instanceof DBMSColumn) {
            return this.equals((DBMSColumn) other);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        return getName().hashCode() ^ getOrdinalIndex();
    }

    /**
     * Return true if the column is equals other.
     */
    public boolean equals(DBMSColumn other) {
        if (other == null) {
            return false;
        }
        if (!this.getName().equals(other.getName())) {
            return false;
        }
        if (this.getOrdinalIndex() != other.getOrdinalIndex()) {
            return false;
        }
        if (this.getSqlType() != other.getSqlType()) {
            return false;
        }
        if (this.isSigned() != other.isSigned()) {
            return false;
        }
        if (this.isNullable() != other.isNullable()) {
            return false;
        }
        if (this.isPrimaryKey() != other.isPrimaryKey()) {
            return false;
        }
        if (this.isUniqueKey() != other.isUniqueKey()) {
            return false;
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
        StringBuilder builder = new StringBuilder( // NL
            getClass().getName());
        builder.append('(');
        builder.append("name: ");
        builder.append(this.getName());
        builder.append(", ordinalIndex: ");
        builder.append(this.getOrdinalIndex());
        builder.append(", sqlType: ");
        builder.append(this.getSqlType());
        builder.append(",\n    signed: ");
        builder.append(this.isSigned());
        builder.append(", nullable: ");
        builder.append(this.isNullable());
        builder.append(", primaryKey: ");
        builder.append(this.isPrimaryKey());
        builder.append(", uniqueKey: ");
        builder.append(this.isUniqueKey());
        builder.append(", generated: ");
        builder.append(this.isGenerated());
        builder.append(", isOnUpdate: ");
        builder.append(this.isOnUpdate());
        builder.append(')');
        return builder.toString();
    }
}
