/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
