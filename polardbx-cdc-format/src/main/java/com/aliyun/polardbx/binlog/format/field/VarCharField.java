/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
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
    public byte[] encode() {
        if (this.data == null) {
            return EMPTY;
        }
        byte data[] = this.data.getBytes(charset);
        int length = data.length;
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
