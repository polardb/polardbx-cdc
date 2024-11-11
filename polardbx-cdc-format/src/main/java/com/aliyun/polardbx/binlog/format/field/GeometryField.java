/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;

import java.math.BigInteger;

/**
 * MYSQL_TYPE_GEOMETRY
 * example:
 * Component	Size	Value
 * Byte order	1 byte	01
 * WKB type	4 bytes	01000000
 * X coordinate	8 bytes	000000000000F03F
 * Y coordinate	8 bytes	000000000000F0BF
 */
public class GeometryField extends BlobField {

    public GeometryField(CreateField createField) {
        super(createField);
    }

    @Override
    public MySQLType getMysqlType() {
        return MySQLType.MYSQL_TYPE_GEOMETRY;
    }

    @Override
    public byte[] encodeInternal() {
        if (contents == null) {
            if (data instanceof byte[]) {
                contents = (byte[]) data;
            } else {
                String value = String.valueOf(this.data);
                String prefix = value.substring(0, 2).toLowerCase();
                BigInteger bigInteger;
                if (prefix.equals("0x")) {
                    bigInteger = new BigInteger(value.substring(3), 16);
                } else if (prefix.equals("0b") || prefix.startsWith("b")) {
                    bigInteger = new BigInteger(value.substring(3), 2);
                } else if (prefix.equals("01")) {
                    bigInteger = new BigInteger(value.substring(3), 8);
                } else {
                    bigInteger = new BigInteger(value.substring(3), 10);
                }

                contents = bigInteger.toByteArray();
            }
        }
        return super.encodeInternal();
    }
}
