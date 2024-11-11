/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;
import org.apache.commons.lang3.math.NumberUtils;

/**
 * support MYSQL_TYPE_ENUM
 *
 * @author yanfenglin
 */
public class EnumField extends Field {

    public EnumField(CreateField createField) {
        super(createField);
        this.packageLength = count < 256 ? 1 : 2;
    }

    @Override
    public byte[] encodeInternal() {
        String strData = buildDataStr();
        int v = 0;
        for (int i = 0; i < typeNames.length; i++) {
            if (typeNames[i].replaceAll("'", "").equalsIgnoreCase(strData)) {
                v = i + 1;
                break;
            }
        }
        if (v == 0 && NumberUtils.isCreatable(strData)) {
            int idx = NumberUtils.createInteger(strData);
            if (idx > 0 && idx <= typeNames.length) {
                v = idx;
            }
        }
        return toByte(v, packageLength);
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {(byte) mysqlType.getType(), (byte) packageLength};
    }

    @Override
    public MySQLType getMysqlType() {
        return MySQLType.MYSQL_TYPE_STRING;
    }
}
