/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import com.aliyun.polardbx.binlog.format.utils.MySQLType;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
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

    public DecimalField(CreateField createField) throws InvalidInputDataException {
        super(createField);
        String[] ps = createField.getParameters();
        if (ps != null && ps.length > 0) {
            precision = Integer.parseInt(ps[0]);
            if (ps.length == 2) {
                frac = Integer.parseInt(ps[1]);
            }
        }
        check();
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

    public String processForNumber(String input) {
        if (isOctOrHexOrBin(input)) {
            return parseLong(input) + "";
        }
        return input;
    }

    private void check() throws InvalidInputDataException {
        if (!isNull()) {
            String str = buildDataStr();
            try {
                str = processForNumber(str) + "";
                NumberUtils.createBigDecimal(str);
            } catch (NumberFormatException e) {
                throw new InvalidInputDataException("invalid input data : " + str, e);
            }
        }
    }

    @Override
    public byte[] encodeInternal() {
        String data = buildDataStr();
        data = processForNumber(data);
        BigDecimal decimal = NumberUtils.createBigDecimal(data);
        data = decimal.toPlainString();

        byte mask = 0;
        if (data.charAt(0) == '-') {
            mask = -1;
            data = data.substring(1);
        }
        // 定义整数位数
        int intg = precision - frac;
        // 定义整数9的倍数
        int intg0 = intg / DIG_PER_DEC1;
        // 定义小数9的倍数
        int frac0 = frac / DIG_PER_DEC1;
        //  定义整数除9的的倍数后的余数
        int intg0x = intg - intg0 * DIG_PER_DEC1;
        //  定义整数除9的的倍数后的余数
        int frac0x = frac - frac0 * DIG_PER_DEC1;
        // 字节数
        int isize0 = intg0 * SIZE_OF_INT32 + dig2bytes[intg0x];
        int fsize0 = frac0 * SIZE_OF_INT32 + dig2bytes[frac0x];

        int intg1, intg1x, intg1x0, frac1, frac1x;
        int dot = data.indexOf(".");
        int fromIntg, fromFrac;
        String intValue = "";
        String fracValue = "";
        if (dot > 0) {
            fromIntg = dot;
            fromFrac = data.length() - dot - 1;
            intValue = data.substring(0, dot);
            fracValue = data.substring(dot + 1);
        } else {
            fromIntg = data.length();
            intValue = data;
            fromFrac = 0;
        }

        if (fromIntg > intg) {
            intValue = intValue.substring(fromIntg - intg);
            fromIntg = intg;
        }

        if (fromFrac > frac) {
            fracValue = fracValue.substring(0, frac);
            fromFrac = frac;
        }

        // 带1的都是实际计算的数值
        intg1 = fromIntg / DIG_PER_DEC1;
        intg1x = fromIntg - intg1 * DIG_PER_DEC1;
        frac1 = fromFrac / DIG_PER_DEC1;
        frac1x = fromFrac - frac1 * DIG_PER_DEC1;
        // 字节数
        int isize1 = intg1 * SIZE_OF_INT32 + dig2bytes[intg1x];
        int fsize1 = frac1 * SIZE_OF_INT32 + dig2bytes[frac1x];

        List<String> intList = new ArrayList<>();
        List<String> fracList = new ArrayList<>();
        if (intg1x > 0) {
            intList.add(intValue.substring(0, intg1x));
        }
        if (dot == -1) {
            for (int i = intg1x; i < intValue.length(); i += DIG_PER_DEC1) {
                intList.add(intValue.substring(i, i + DIG_PER_DEC1));
            }
        } else {
            for (int i = intg1x; i < intValue.length(); i += DIG_PER_DEC1) {
                intList.add(intValue.substring(i, i + DIG_PER_DEC1));
            }
        }
        for (int i = 0; i < frac1; i++) {
            int idx = i * DIG_PER_DEC1;
            fracList.add(fracValue.substring(idx, idx + DIG_PER_DEC1));
        }
        int lastFrac = frac1 * DIG_PER_DEC1;
        if (fracValue.length() > lastFrac) {
            fracList.add(fracValue.substring(lastFrac));
        }

        ByteBuffer buffer = ByteBuffer.allocate(isize0 + fsize0);
        if (fsize0 < fsize1) {
            frac1 = frac0;
            frac1x = frac0x;
            // truncated
        } else if (fsize0 > fsize1 && frac1x > 0) {
            if (frac0 == frac1) {
                frac1x = frac0x;
                fsize0 = fsize1;
            } else {
                frac1++;
                frac1x = 0;
            }
        }

        if (intg < fromIntg) {
            intg1 = intg0;
            intg1x = intg0x;
        } else if (isize0 > isize1) {
            while (isize0-- > isize1) {
                buffer.put(mask);
            }
        }

        if (intg1x > 0) {
            String str = intList.remove(0);
            toBEByte(buffer, mod_by_pow10(Integer.parseInt(str), intg1x) ^ mask, dig2bytes[intg1x]);
        }

        for (int i = 0; i < frac1 + intg1; i++) {
            String str;
            if (!intList.isEmpty()) {
                str = intList.remove(0);
            } else {
                str = fracList.remove(0);
            }
            int seg = Integer.parseInt(str) * powers10[DIG_PER_DEC1 - str.length()];
            toBEByte(buffer, seg ^ mask, SIZE_OF_INT32);
        }

        if (frac1x > 0) {
            int j = dig2bytes[frac1x];
            int lim = (frac1 < frac0 ? DIG_PER_DEC1 : frac0x);
            while (frac1x < lim && dig2bytes[frac1x] == j) {
                frac1x++;
            }
            String str = fracList.remove(0);
            int x = div_by_pow10(Integer.parseInt(StringUtils.rightPad(str, DIG_PER_DEC1, "0")),
                DIG_PER_DEC1 - frac1x) ^ mask;
            toBEByte(buffer, x,
                dig2bytes[frac1x]);
        }

        if (fsize0 >= fsize1) {
            while (fsize0-- > fsize1 && buffer.hasRemaining()) {
                buffer.put(mask);
            }
        } else {
            throw new ArrayIndexOutOfBoundsException();
        }
        byte flag = buffer.get(0);

        flag ^= 0x80;
        buffer.put(0, flag);
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
