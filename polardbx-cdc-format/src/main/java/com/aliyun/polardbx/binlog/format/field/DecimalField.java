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
import com.aliyun.polardbx.binlog.format.utils.MySQLType;
import org.apache.commons.lang3.StringUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

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

    static int mod_by_pow10(int x, int p) {
        // See div_by_pow10 for rationale.
        switch (p) {
        case 1:
            return (x) % 10;
        case 2:
            return (x) % 100;
        case 3:
            return (x) % 1000;
        case 4:
            return (x) % 10000;
        case 5:
            return (x) % 100000;
        case 6:
            return (x) % 1000000;
        case 7:
            return (x) % 10000000;
        case 8:
            return (x) % 100000000;
        default:
            return x % powers10[p];
        }
    }

    static int div_by_pow10(int x, int p) {
  /*
    GCC can optimize division by a constant to a multiplication and some
    shifts, which is faster than dividing by a variable, even taking into
    account the extra cost of the switch. It is also (empirically on a Skylake)
    faster than storing the magic multiplier constants in a table and doing it
    ourselves. However, since the code is much bigger, we only use this in
    a few select places.

    Note the use of unsigned, which is faster for this specific operation.
  */
        switch (p) {
        case 0:
            return (x) / 1;
        case 1:
            return (x) / 10;
        case 2:
            return (x) / 100;
        case 3:
            return (x) / 1000;
        case 4:
            return (x) / 10000;
        case 5:
            return (x) / 100000;
        case 6:
            return (x) / 1000000;
        case 7:
            return (x) / 10000000;
        case 8:
            return (x) / 100000000;
        default:
            return x / powers10[p];
        }
    }

    @Override
    public byte[] encode() {
        if (data == null) {
            return EMPTY;
        }
        byte mask = 0;
        if (data.charAt(0) == '-') {
            mask = -1;
            data = data.substring(1);
        }
        int intg = precision - frac, intg0 = intg / DIG_PER_DEC1, frac0 = frac / DIG_PER_DEC1, intg0x =
            intg - intg0 * DIG_PER_DEC1, frac0x = frac - frac0 * DIG_PER_DEC1, isize0 =
            intg0 * SIZE_OF_INT32 + dig2bytes[intg0x], fsize0 = frac0 * SIZE_OF_INT32 + dig2bytes[frac0x];

        int intg1, intg1x, intg1x0, frac1, frac1x;
        int dot = data.indexOf(".");
        int fromIntg, fromFrac;
        if (dot > 0) {
            fromIntg = dot;
            fromFrac = data.length() - dot - 1;
        } else {
            fromIntg = data.length();
            fromFrac = 0;
        }
        intg1 = fromIntg / DIG_PER_DEC1;
        intg1x = fromIntg - intg1 * DIG_PER_DEC1;
        frac1 = fromFrac / DIG_PER_DEC1;
        frac1x = fromFrac - frac1 * DIG_PER_DEC1;
        int isize1 = intg1 * SIZE_OF_INT32 + dig2bytes[intg1x];
        int fsize1 = frac1 * SIZE_OF_INT32 + dig2bytes[frac1x];

        List<String> decList = new ArrayList<>();
        decList.add(data.substring(0, intg1x));
        for (int i = intg1x; i < dot; i += DIG_PER_DEC1) {
            decList.add(data.substring(i, i + DIG_PER_DEC1));
        }
        for (int i = 0; i < frac1; i++) {
            int idx = i * DIG_PER_DEC1 + dot + 1;
            decList.add(data.substring(idx, idx + DIG_PER_DEC1));
        }
        decList.add(data.substring(frac1 * DIG_PER_DEC1 + dot + 1));

        ByteBuffer buffer = ByteBuffer.allocate(isize0 + fsize0);

        if (fsize0 > fsize1 && frac1x > 0) {
            if (frac0 == frac1) {
                frac1x = frac0x;
                fsize0 = fsize1;
            } else {
                frac1++;
                frac1x = 0;
            }
        }

        if (intg < fromIntg) {
            throw new UnsupportedOperationException();
        } else if (isize0 > isize1) {
            while (isize0-- > isize1) {
                buffer.put(mask);
            }
        }

        for (int i = 0; i < decList.size(); i++) {
            String str = decList.get(i);
            if (str.length() < DIG_PER_DEC1) {
                if (i == 0) {
                    toBEByte(buffer, Integer.parseInt(str) ^ mask, dig2bytes[intg1x]);
                } else {
                    int j = dig2bytes[frac1x];
                    while (frac1x < frac0x && dig2bytes[frac1x] == j) {
                        frac1x++;
                    }
                    int x = div_by_pow10(Integer.parseInt(StringUtils.rightPad(str, DIG_PER_DEC1, "0")),
                        DIG_PER_DEC1 - frac1x) ^ mask;
                    toBEByte(buffer, x,
                        dig2bytes[frac1x]);
                }
            } else {
                toBEByte(buffer, Integer.parseInt(str) ^ mask, SIZE_OF_INT32);
            }
        }

        if (fsize0 >= fsize1) {
            while (fsize0-- > fsize1 && buffer.hasRemaining()) {
                buffer.put(mask);
            }
        } else {
            throw new UnsupportedOperationException();
        }
        byte flag = buffer.get(0);
        buffer.put(0, flag ^= 0x80);
        /* Check that we have written the whole decimal and nothing more */
        return buffer.array();
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
