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
