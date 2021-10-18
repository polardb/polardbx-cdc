/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
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
    public byte[] encode() {
        if (data == null) {
            return EMPTY;
        }
        byte[] data = this.data.getBytes(charset);
        int length_bytes = (fieldLength > 255) ? 2 : 1;
        int length = data.length;
        if (fieldLength < length_bytes) {
            length = 0;
        } else if (length > fieldLength - length_bytes) {
            length = (int) (fieldLength - length_bytes);
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
