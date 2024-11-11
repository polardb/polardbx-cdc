/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

/**
 * This class creates a default SQL column implementation. <br />
 *
 * @author Changyuan.lh
 * @version 1.0
 */
public class DefaultColumn extends DBMSColumn {

    private static final long serialVersionUID = -5681302414718582726L;

    protected String name;
    // The ordinal index of column.
    protected int ordinalIndex;
    // Type assignment from java.sql.Types
    protected int sqlType;
    protected boolean signed;
    // Is the column a NULL column
    protected boolean nullable;
    protected boolean primaryKey;
    protected boolean uniqueKey;
    protected boolean generated;
    protected boolean rdsImplicitPk;
    protected boolean onUpdate;

    /**
     * Create a new <code>SimpleColumn</code> object.
     */
    public DefaultColumn(String name, int ordinalIndex, int sqlType) {
        this.name = name;
        this.ordinalIndex = ordinalIndex;
        this.sqlType = sqlType;
    }

    /**
     * Create a new <code>DefaultColumn</code> object.
     */
    public DefaultColumn(String name, int ordinalIndex, int sqlType, boolean signed, boolean nullable,
                         boolean primaryKey) {
        this.name = name;
        this.ordinalIndex = ordinalIndex;
        this.sqlType = sqlType;
        this.signed = signed;
        this.nullable = nullable;
        this.primaryKey = primaryKey;
    }

    /**
     * Create a new <code>DefaultColumn</code> object.
     */
    public DefaultColumn(String name, int ordinalIndex, int sqlType, boolean signed, boolean nullable,
                         boolean primaryKey, boolean uniqueKey) {
        this(name, ordinalIndex, sqlType, signed, nullable, primaryKey);
        this.uniqueKey = uniqueKey;
    }

    public DefaultColumn(String name, int ordinalIndex, int sqlType, boolean signed, boolean nullable,
                         boolean primaryKey, boolean uniqueKey, boolean generated, boolean rdsImplicitPk,
                         boolean onUpdate) {
        this(name, ordinalIndex, sqlType, signed, nullable, primaryKey);
        this.uniqueKey = uniqueKey;
        this.generated = generated;
        this.rdsImplicitPk = rdsImplicitPk;
        this.onUpdate = onUpdate;
    }

    /**
     * Return the column name.
     */
    public String getName() {
        return name;
    }

    /**
     * Return the ordinal column index.
     */
    public int getOrdinalIndex() {
        return ordinalIndex;
    }

    public void setOrdinalIndex(int ordinalIndex) {
        this.ordinalIndex = ordinalIndex;
    }

    /**
     * Return the column SQL type.
     */
    public int getSqlType() {
        return sqlType;
    }

    /**
     * Return true if the column is singned.
     */
    public boolean isSigned() {
        return signed;
    }

    /**
     * Return true if the column is <code>NULL</code> column.
     */
    public boolean isNullable() {
        return nullable;
    }

    /**
     * Return true if the column is a part of primary key.
     */
    public boolean isPrimaryKey() {
        return primaryKey;
    }

    /**
     * Change the column name.
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Change the column SQL type.
     */
    public void setSqlType(int sqlType) {
        this.sqlType = sqlType;
    }

    /**
     * Change the column is singned/unsigned.
     *
     * @param signed - The column is singned/unsigned.
     */
    public void setSigned(boolean signed) {
        this.signed = signed;
    }

    /**
     * Change the column is null or not.
     *
     * @param nullable - The column is null or not.
     */
    public void setNullable(boolean nullable) {
        this.nullable = nullable;
    }

    /**
     * Change the column is a part of primary key or not.
     *
     * @param primaryKey - The column is a part of primary key or not.
     */
    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

    public void setIsOnUpdate(boolean onUpdate) {
        this.onUpdate = onUpdate;
    }

    @Override
    public boolean isUniqueKey() {
        return uniqueKey;
    }

    public void setUniqueKey(boolean uniqueKey) {
        this.uniqueKey = uniqueKey;
    }

    @Override
    public boolean isGenerated() {
        return generated;
    }

    @Override
    public boolean isRdsImplicitPk() {
        return rdsImplicitPk;
    }

    @Override
    public boolean isOnUpdate() {
        return onUpdate;
    }
}
