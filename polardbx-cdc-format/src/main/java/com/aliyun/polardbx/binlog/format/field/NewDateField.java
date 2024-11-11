/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.field.domain.MDate;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;

public class NewDateField extends Field {

    public NewDateField(CreateField createField) {
        super(createField);
    }

    @Override
    public byte[] encodeInternal() {
        String value = buildDataStr();
        MDate date = new MDate();
        date.parse(value);
        long tmp = date.getDay() + date.getMonth() * 32L + (long) date.getYear() * 16 * 32;
        return toByte(tmp, 3);
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[0];
    }

    @Override
    public MySQLType getMysqlType() {
        return MySQLType.MYSQL_TYPE_DATE;
    }
}
