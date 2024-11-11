/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.field.domain.MDate;

/**
 * MYSQL_TYPE_TIME
 */
@Deprecated
public class TimeField extends Field {

    public TimeField(CreateField createField) {
        super(createField);
    }

    @Override
    public byte[] encodeInternal() {
        String data = buildDataStr();
        MDate date = new MDate();
        date.parse(data);
        long tmp = ((date.getMonth() > 0 ? 0 : date.getDay() * 24L) + date.getHours()) * 10000L +
            (date.getMinutes() * 100 + date.getSeconds());
        if (date.isNeg()) {
            tmp = -tmp;
        }
        return toByte(tmp, 3);
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {};
    }
}
