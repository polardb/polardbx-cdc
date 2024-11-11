/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;

/**
 * case MYSQL_TYPE_VARCHAR:
 */
public class VarCharField extends Field {

    public VarCharField(CreateField createField) {
        super(createField);
    }

    @Override
    public byte[] encodeInternal() {
        byte[] data;
        if (this.data instanceof byte[]) {
            data = (byte[]) this.data;
        } else {
            data = String.valueOf(this.data).getBytes(charset);
        }
        int length = data.length;
        if (length > fieldLength) {
            length = (int) fieldLength;
        }
        int length_bytes = ((fieldLength) < 256 ? 1 : 2);
        byte[] output = new byte[length_bytes + length];
        /* Length always stored little-endian */
        toByte(output, length, length_bytes, 0);
        /* Store bytes of string */
        System.arraycopy(data, 0, output, length_bytes, length);
        return output;
    }

    @Override
    public byte[] doGetTableMeta() {
        return toByte(fieldLength, 2);
    }
}
