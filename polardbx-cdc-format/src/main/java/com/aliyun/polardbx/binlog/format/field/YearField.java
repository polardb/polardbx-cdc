/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import org.apache.commons.lang3.StringUtils;

public class YearField extends Field {

    private static final int MIN_YEAR = 1901;
    private static final int MAX_YEAR = 2155;
    private static final int YY_PART_YEAR = 70;

    public YearField(CreateField createField) throws InvalidInputDataException {
        super(createField);
        check();
    }

    private void check() throws InvalidInputDataException {
        if (!isNull()) {
            String str = buildDataStr();
            try {
                Long.parseLong(str);
            } catch (NumberFormatException e) {
                throw new InvalidInputDataException("invalid input data : " + str, e);
            }
        }
    }

    @Override
    public boolean isNull() {
        return super.isNull() || StringUtils.equalsIgnoreCase(buildDataStr(), "CURRENT_TIMESTAMP");
    }

    @Override
    public byte[] encodeInternal() {
        String data = buildDataStr();
        long nr = Long.parseLong(data);
        if (nr < 0 || (nr >= 100 && nr < MIN_YEAR) || nr > MAX_YEAR) {
            throw new RuntimeException("out of range");
        }
        if (nr != 0)  // 0000 -> 0
        {
            if (nr < YY_PART_YEAR) {
                nr += 100;  // 2000 - 2069
            } else if (nr > 1900) {
                nr -= 1900;
            }
        }
        return new byte[] {(byte) nr};
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[0];
    }
}
