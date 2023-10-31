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
import com.aliyun.polardbx.binlog.format.utils.BitMap;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;
import com.google.common.collect.Sets;

import java.util.Set;

/**
 * MYSQL_TYPE_SET
 */
public class SetField extends Field {

    private final int pkgLength;

    public SetField(CreateField createField) {
        super(createField);
        pkgLength = get_set_pack_length(count);
    }

    private int get_set_pack_length(int elements) {
        int len = (elements + 7) / 8;
        return len > 4 ? 8 : len;
    }

    @Override
    public byte[] encodeInternal() {
        String data = buildDataStr();
        final int nbits = (pkgLength & 0xFF) * 8;
        BitMap bitMap = new BitMap(nbits);
        Set<String> valueSet = Sets.newHashSet(data.split(","));
        for (int i = 0; i < typeNames.length; i++) {
            if (valueSet.contains(typeNames[i].replaceAll("'", ""))) {
                bitMap.set(i, true);
            }
        }
        return bitMap.getData();
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {(byte) mysqlType.getType(), (byte) pkgLength};
    }

    @Override
    public MySQLType getMysqlType() {
        return MySQLType.MYSQL_TYPE_STRING;
    }
}
