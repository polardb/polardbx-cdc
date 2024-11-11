/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Objects;

public class SimpleField extends Field {

    private final byte[] data;
    private int fieldType;
    private final int meta;

    public SimpleField(byte[] data, int fieldType, int meta) {
        super(null);
        this.data = data;
        this.fieldType = fieldType;
        this.meta = meta;
    }

    @Override
    public byte[] encodeInternal() {
        return data;
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[0];
    }

    @Override
    public boolean isNullable() {
        return data == null;
    }

    @Override
    public boolean isNull() {
        return data == null;
    }

    @Override
    public MySQLType getMysqlType() {
        return MySQLType.typeOf(fieldType);
    }

    public int getFieldType() {
        return fieldType;
    }

    public void setFieldType(int fieldType) {
        this.fieldType = fieldType;
    }

    public int getMeta() {
        return meta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        SimpleField that = (SimpleField) o;
        return fieldType == that.fieldType &&
            meta == that.meta &&
            Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(fieldType, meta);
        result = 31 * result + Arrays.hashCode(data);
        return result;
    }

    public Serializable decode() {
        RowsLogBuffer rowsLogBuffer = new RowsLogBuffer(null, 0, "utf8");
        return rowsLogBuffer.fetchValue(fieldType, meta, false, data, "utf8");
    }

    public byte[] getData() {
        return data;
    }
}
