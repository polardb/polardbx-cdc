/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.CommonConstants;
import com.aliyun.polardbx.binlog.format.field.datatype.CreateField;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.ArrayUtils;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Arrays;

/**
 * support integer, byte , long, Float.floatToIntBits()
 */
public class NumberField extends Field {

    private static final int MIN_TINYINT = -128;
    private static final int MAX_TINYINT = 127;
    private static final int MIN_SMALLINT = -32768;
    private static final int MAX_SMALLINT = 32767;
    private static final int MAX_MEDIUMINT = 8388607;
    private static final int MIN_MEDIUMINT = -8388608;
    private static final long MIN_INT = -2147483648;
    private static final long MAX_INT = 2147483647;

    private static final int MAX_UNSIGNED_TINYINT = 255;
    private static final int MAX_UNSIGNED_SMALLINT = 65535;
    private static final int MAX_UNSIGNED_MEDIUMINT = 16777215;
    private static final long MAX_UNSIGNED_INT = 4294967295L;
    private final boolean unsigned;

    public NumberField(CreateField createField) throws InvalidInputDataException {
        super(createField);
        this.unsigned = createField.isUnsigned();
        check();
    }

    private void check() throws InvalidInputDataException {
        if (!isNull()) {
            String str = buildDataStr();
            try {
                str = processData4Boolean(str);
                str = processForNumber(str);
                NumberUtils.createBigDecimal(str);
            } catch (NumberFormatException e) {
                throw new InvalidInputDataException("invalid input data : " + str, e);
            }
        }
    }

    private String processData4Boolean(String data) {
        if (CommonConstants.TRUE.equalsIgnoreCase(data)) {
            return "1";
        } else if (CommonConstants.FALSE.equalsIgnoreCase(data)) {
            return "0";
        } else {
            return data;
        }
    }

    private long checkRange(long v, long min, long max, long unsignedMax) {
        if (unsigned) {
            if (v < 0) {
                v = 0;
            }
            if (v > unsignedMax) {
                return unsignedMax;
            }
            return v;
        }
        if (v < min) {
            return min;
        }
        if (v > max) {
            return max;
        }
        return v;
    }

    public String processForNumber(String input) {
        if (isOctOrHexOrBin(input)) {
            return parseLong(input) + "";
        }
        return input;
    }

    @Override
    public byte[] encodeInternal() {
        String data = buildDataStr();
        data = processData4Boolean(data);
        data = processForNumber(data);
        long v;
        BigDecimal decimal = NumberUtils.createBigDecimal(data);
        if (typeNames != null && typeNames.length == 2 && NumberUtils.isNumber(typeNames[0]) && NumberUtils.isNumber(
            typeNames[1])) {
            int intSize = (decimal.intValue() + "").length();
            int defineTotal = NumberUtils.createInteger(typeNames[0]);
            int defineAfter = NumberUtils.createInteger(typeNames[1]);
            int defineIntSize = defineTotal - defineAfter;
            if (defineIntSize < intSize) {
                StringBuilder newValueBuilder = getNewValueBuilder(decimal, defineIntSize, defineAfter);
                decimal = NumberUtils.createBigDecimal(newValueBuilder.toString());
            }
            decimal = decimal.round(new MathContext(intSize + defineAfter, RoundingMode.HALF_UP));
        }
        switch (mysqlType) {
        case MYSQL_TYPE_LONG: {
            v = decimal.longValue();
            v = checkRange(v, MIN_INT, MAX_INT, MAX_UNSIGNED_INT);
            return toByte(v, 4);
        }
        case MYSQL_TYPE_FLOAT: {
            v = Float.floatToIntBits(decimal.floatValue());
            return toByte(v, 4);
        }
        case MYSQL_TYPE_TINY: {
            v = decimal.longValue();
            v = checkRange(v, MIN_TINYINT, MAX_TINYINT, MAX_UNSIGNED_TINYINT);
            return toByte(v, 1);
        }
        case MYSQL_TYPE_SHORT: {
            v = decimal.longValue();
            v = checkRange(v, MIN_SMALLINT, MAX_SMALLINT, MAX_UNSIGNED_SMALLINT);
            return toByte(v, 2);
        }
        case MYSQL_TYPE_INT24: {
            v = decimal.longValue();
            v = checkRange(v, MIN_MEDIUMINT, MAX_MEDIUMINT, MAX_UNSIGNED_MEDIUMINT);
            return toByte(v, 3);
        }
        case MYSQL_TYPE_LONGLONG: {
            BigInteger bigInteger = decimal.toBigInteger();
            byte[] bdata = bigInteger.toByteArray();
            ArrayUtils.reverse(bdata);
            byte[] dst = Arrays.copyOf(bdata, 8);
            //如果是负数
            if (bdata.length < 8) {
                if (bigInteger.signum() == -1) {
                    Arrays.fill(dst, bdata.length, 8, (byte) 0xFF);
                }
            }
            return dst;
        }
        case MYSQL_TYPE_DOUBLE: {
            v = Double.doubleToLongBits(decimal.doubleValue());
            return toByte(v, 8);
        }
        case MYSQL_TYPE_DECIMAL: {
            // should not be here
            v = Double.doubleToLongBits(decimal.doubleValue());
            return toByte(v, 2);
        }
        default:
            throw new UnsupportedOperationException("unsupport type " + mysqlType + " , value : " + data);
        }
    }

    private static StringBuilder getNewValueBuilder(BigDecimal decimal, int defineIntSize, int defineAfter) {
        StringBuilder newValueBuilder = new StringBuilder();
        if (decimal.intValue() < 0) {
            newValueBuilder.append("-");
        }
        for (int i = 0; i < defineIntSize; i++) {
            newValueBuilder.append("9");
        }
        if (defineAfter > 0) {
            newValueBuilder.append(".");
        }
        for (int i = 0; i < defineAfter; i++) {
            newValueBuilder.append("9");
        }
        return newValueBuilder;
    }

    @Override
    public byte[] doGetTableMeta() {
        switch (mysqlType) {
        case MYSQL_TYPE_FLOAT:
            byte[] metas = new byte[1];
            metas[0] = 4;
            return metas;
        case MYSQL_TYPE_DOUBLE:
            metas = new byte[1];
            metas[0] = 8;
            return metas;
        case MYSQL_TYPE_TINY:
        case MYSQL_TYPE_SHORT:
        case MYSQL_TYPE_INT24:
        case MYSQL_TYPE_LONG:
        case MYSQL_TYPE_LONGLONG:
        }
        return EMPTY;
    }
}
