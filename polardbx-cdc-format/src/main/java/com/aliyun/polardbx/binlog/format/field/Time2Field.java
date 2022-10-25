/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.field.domain.MDate;

public class Time2Field extends Field {

    private static final long TIMEF_OFS = 0x800000000000L;
    private static final long TIMEF_INT_OFS = 0x800000L;

    private static final int MAX_TIME_WIDTH = 10;                /* -838:59:59 */

    private int dec;

    public Time2Field(CreateField createField) {
        super(createField);
        dec = (fieldLength > MAX_TIME_WIDTH) ? (int) (fieldLength - 1 - MAX_TIME_WIDTH) : 0;
    }

    private static long my_packed_time_get_int_part(long i) {
        return (i >> 24);
    }

    private static long my_packed_time_get_frac_part(long i) {
        return (i % (1L << 24));
    }

    @Override
    public byte[] encode() {
        if (data == null) {
            return EMPTY;
        }
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
        return new byte[0];
    }
}
