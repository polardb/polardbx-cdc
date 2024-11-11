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

@Deprecated
public class DatetimeField extends Field {

    private final int dec;

    public DatetimeField(CreateField createField) {
        super(createField);
        dec = createField.getCodepoint();
    }

    @Override
    public boolean isNull() {
        return super.isNull() || StringUtils.equalsIgnoreCase(buildDataStr(), "CURRENT_TIMESTAMP");
    }

    @Override
    public byte[] encodeInternal() {
        String value = buildDataStr();
        MDate date = new MDate();
        date.parse(value);
        long tmp = date.TIME_to_ulonglong_datetime();
        return toByte(tmp, 8);
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {(byte) dec};
    }

}
