/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.field.domain.MDate;

public class Time2Field extends Field {

    private static final long TIMEF_OFS = 0x800000000000L;
    private static final long TIMEF_INT_OFS = 0x800000L;
    private static final int MAX_TIME_WIDTH = 10;                /* -838:59:59 */

    private final int dec;

    public Time2Field(CreateField createField) {
        super(createField);
        dec = createField.getCodepoint();
    }

    private static long my_packed_time_get_int_part(long i) {
        return (i >> 24);
    }

    private static long my_packed_time_get_frac_part(long i) {
        return (i % (1L << 24));
    }

    @Override
    public byte[] encodeInternal() {
        String data = buildDataStr();
        MDate date = new MDate();
        date.parse(data);
        long nr = date.TIME_to_longlong_time_packed();
        switch (dec) {
        case 0:
        default:
            return toBEByte(TIMEF_INT_OFS + my_packed_time_get_int_part(nr), 3);
        case 1:
        case 2:
            byte[] b3 = toBEByte(TIMEF_INT_OFS + my_packed_time_get_int_part(nr), 3);
            return new byte[] {b3[0], b3[1], b3[2], (byte) (my_packed_time_get_frac_part(nr) / 10000)};

        case 4:
        case 3:
            byte[] output = new byte[5];
            System.arraycopy(toBEByte(TIMEF_INT_OFS + my_packed_time_get_int_part(nr), 3), 0, output, 0, 3);
            System.arraycopy(toBEByte(my_packed_time_get_frac_part(nr) / 100, 2), 0, output, 3, 2);
            return output;
        case 5:
        case 6:
            return toBEByte(nr + TIMEF_OFS, 6);
        }
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {(byte) dec};
    }
}
