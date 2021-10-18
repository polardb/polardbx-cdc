/*
 *
 * Copyright (c) 2013-2021, Alibaba Group Holding Limited;
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
 *
 */

package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;
import org.apache.commons.lang3.StringUtils;

/**
 * MYSQL_TYPE_NEWDECIMAL
 */
public class DecimalField extends Field {

    public static final int DIG_PER_INT32 = 9;
    public static final int SIZE_OF_INT32 = 4;
    private static final int DIG_PER_DEC1 = 9;
    private static final int DIG_MASK = 100000000;
    private static final int DIG_BASE = 1000000000;
    private static final int DIG_MAX = (DIG_BASE - 1);

    private static final int powers10[] = {
        1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000,
        1000000000};
    private static final int dig2bytes[] = {0, 1, 1, 2, 2, 3, 3, 4, 4, 4};
    private static final int frac_max[] = {
        900000000, 990000000, 999000000, 999900000, 999990000, 999999000,
        999999900, 999999990};

    private int precision = 10;
    private int frac = 0;

    public DecimalField(CreateField createField) {
        super(createField);
        String[] ps = createField.getParameters();
        if (ps != null && ps.length > 0) {
            precision = Integer.valueOf(ps[0]);
            if (ps.length == 2) {
                frac = Integer.valueOf(ps[1]);
            }
        }
    }

    @Override
    public byte[] encode() {
        if (data == null) {
            return EMPTY;
        }

        int mask = data.charAt(0) == '-' ? -1 : 0;
        int intg = precision - frac, intg0 = intg / DIG_PER_DEC1, frac0 = frac / DIG_PER_DEC1, intg0x =
            intg - intg0 * DIG_PER_DEC1, frac0x = frac - frac0 * DIG_PER_DEC1, isize0 =
            intg0 * SIZE_OF_INT32 + dig2bytes[intg0x], fsize0 = frac0 * SIZE_OF_INT32 + dig2bytes[frac0x];

        int intg1, intg1x, intg1x0, frac1, frac1x;
        int dot = data.indexOf(".");
        if (dot > 0) {
            intg1 = dot;
        } else {
            intg1 = data.length() / DIG_PER_DEC1;
        }

        int fromFrac = data.length() - intg1 - (dot > 0 ? 1 : 0);
        frac1 = fromFrac / DIG_PER_DEC1;
        frac1x = fromFrac - frac1 * DIG_PER_DEC1;
        intg1x = intg1 / DIG_PER_DEC1;
        intg1x0 = intg1 - intg1x * DIG_PER_DEC1;
        int csize = isize0;
        int rsize = intg1x + dig2bytes[intg1x0];
        byte[] buffer = new byte[isize0 + fsize0];

        int offset = 0;

        while (csize-- > rsize) {
            buffer[offset++] = (byte) mask;
        }

        int fsize1 = frac1 * SIZE_OF_INT32 + dig2bytes[frac1x];

        if (fsize0 < fsize1) {
            frac1 = frac0;
            frac1x = frac0x;
            //truncate
        } else if (fsize0 > fsize1 && frac1x > 0) {
            if (frac0 == frac1) {
                frac1x = frac0x;
                fsize0 = fsize1;
            } else {
                frac1++;
                frac1x = 0;
            }
        }

        if (intg0x > 0) {
            String ns = data.substring(0, intg0x);
            toBEByte(buffer, Long.valueOf(ns) ^ mask, dig2bytes[intg0x], offset);
            offset += dig2bytes[intg0x];
        }

        if (intg1x < intg0) {
            intg0 = intg1x;
        }
        int from = intg0x;
        for (int i = 0; i < intg0; i++) {
            String ns = data.substring(from, from + DIG_PER_DEC1);
            from += DIG_PER_DEC1;
            toBEByte(buffer, Long.valueOf(ns) ^ mask, 4, offset);
            offset += 4;
        }

        // skip dot
        from++;

        for (int i = 0; i < frac0; i++) {
            String ns = data.substring(from, Math.min(from + DIG_PER_DEC1, data.length()));
            from += DIG_PER_DEC1;
            toBEByte(buffer, Long.valueOf(ns) ^ mask, 4, offset);
            offset += 4;
        }

        if (frac1x > 0) {
            int x;
            int i = dig2bytes[frac1x];
            String ns = StringUtils.rightPad(data.substring(from), frac, "0");
            x = Integer.valueOf(ns) ^ mask;
            toBEByte(buffer, x, i, offset);
            offset += i;
        }
        if (fsize0 > fsize1) {
            while (fsize0-- > fsize1) {
                buffer[offset++] = (byte) mask;
            }
        }
        buffer[0] ^= 0x80;
        /* Check that we have written the whole decimal and nothing more */
        return buffer;
    }

    @Override
    public byte[] doGetTableMeta() {
        return new byte[] {(byte) precision, (byte) frac};
    }

    @Override
    public MySQLType getMysqlType() {
        return MySQLType.MYSQL_TYPE_NEWDECIMAL;
    }
}
