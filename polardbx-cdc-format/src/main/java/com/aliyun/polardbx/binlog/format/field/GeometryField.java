/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
