/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;

import java.nio.ByteBuffer;

public class BitField extends Field {

    public BitField(CreateField createField) {
        super(createField);
        m_max_display_width_in_codepoints = Math.max(1, m_max_display_width_in_codepoints);
        if (fieldLength == 0) {
            fieldLength = max_display_width_in_bytes(charset, mysqlType);
        }
    }

    @Override
    public byte[] encodeInternal() {
        if (mysqlType != MySQLType.MYSQL_TYPE_BIT) {
            throw new UnsupportedOperationException();
        }

        byte[] bytes;
        if (data instanceof byte[]) {
            bytes = (byte[]) data;
        } else {
            String dataStr = String.valueOf(data);
            long longNum = parseLong(dataStr);

            ByteBuffer bb = ByteBuffer.allocate(8);
            bb.putLong(longNum);
            bytes = bb.array();
        }

        int length = (m_max_display_width_in_codepoints + 7) / 8;
        byte[] result = new byte[length];
        if (bytes.length >= length) {
            System.arraycopy(bytes, bytes.length - length, result, 0, length);
        } else {
            System.arraycopy(bytes, 0, result, length - bytes.length, bytes.length);
        }

        return result;
    }

    @Override
    public byte[] doGetTableMeta() {
        byte[] metDatas = new byte[2];
        long len = this.fieldLength;
        metDatas[0] = (byte) (len % 8);
        metDatas[1] = (byte) (len / 8);
        return metDatas;
    }
}
