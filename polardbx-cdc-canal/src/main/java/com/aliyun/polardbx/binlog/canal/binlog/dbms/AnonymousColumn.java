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
public class AnonymousColumn extends DBMSColumn {

    private static final long serialVersionUID = -5681302414718582726L;

    // Type assignment from java.sql.Types
    protected int ordinalIndex;
    protected int sqlType;
    protected int meta;

    public AnonymousColumn(int ordinalIndex, int sqlType, int meta) {
        this.ordinalIndex = ordinalIndex;
        this.sqlType = sqlType;
        this.meta = meta;
    }

    @Override
    public String getName() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getOrdinalIndex() {
        return ordinalIndex;
    }

    @Override
    public int getSqlType() {
        return sqlType;
    }

    public int getMeta() {
        return meta;
    }

    @Override
    public boolean isSigned() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public boolean isPrimaryKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isUniqueKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isGenerated() {
        return false;
    }

    @Override
    public boolean isRdsImplicitPk() {
        return false;
    }

    @Override
    public boolean isOnUpdate() {
        return false;
    }
}
