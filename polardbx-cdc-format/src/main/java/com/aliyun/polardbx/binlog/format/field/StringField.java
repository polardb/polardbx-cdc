/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;

/**
 * case MYSQL_TYPE_VAR_STRING:
 * case MYSQL_TYPE_STRING:
 */
public class StringField extends Field {

    public StringField(CreateField createField) {
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
        
        int length_bytes = (fieldLength > 255) ? 2 : 1;
        int length = data.length;
        if (fieldLength < length_bytes) {
            length = 0;
        } else if (length > fieldLength) {
            length = (int) (fieldLength);
        }

        byte[] output = new byte[length + length_bytes];
        /* Length always stored little-endian */
        if (fieldLength >= 1) {
            output[0] = (byte) (length & 0xFF);
            if (length_bytes == 2 && fieldLength >= 2) {
                output[1] = (byte) ((length >> 8) & 0xFF);
            }
        }

        System.arraycopy(data, 0, output, length_bytes, length);
        return output;
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {(byte) (mysqlType.getType() ^ ((fieldLength & 0x300) >> 4)), (byte) (fieldLength & 0xFF)};
    }
}
