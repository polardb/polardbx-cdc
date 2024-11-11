/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.field.domain.MDate;
import org.apache.commons.lang3.StringUtils;

import java.util.Calendar;

/**
 * MYSQL_TYPE_TIMESTAMP
 */
@Deprecated
public class TimestampField extends Field {

    public TimestampField(CreateField createField) {
        super(createField);
    }

    @Override
    public boolean isNull() {
        return super.isNull() || StringUtils.equalsIgnoreCase(buildDataStr(), "CURRENT_TIMESTAMP");
    }

    @Override
    public byte[] encodeInternal() {
        String data = buildDataStr();
        MDate date = new MDate();
        date.parse(data);
        Calendar calendar = Calendar.getInstance();
        calendar.set(date.getYear(),
            date.getMonth() - 1,
            date.getDay(),
            date.getHours(),
            date.getMinutes(),
            date.getSeconds());
        calendar.set(Calendar.MILLISECOND, date.getMillsecond());
        long sec = calendar.getTimeInMillis() / 1000;
        return toByte(sec, 4);
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[0];
    }
}
