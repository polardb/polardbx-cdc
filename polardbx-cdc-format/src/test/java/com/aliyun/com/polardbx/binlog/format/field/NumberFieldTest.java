/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.com.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class NumberFieldTest {
    private static final String defaultCharset = "utf8";

    private List<NumberType> numberTypeList = new ArrayList<>();

    private List<FloatType> floatTypeList = new ArrayList<>();

    @Before
    public void before() {
        numberTypeList.add(new NumberType("TINYINT", -128, 127, new byte[] {-128}, new byte[] {127}));
        numberTypeList.add(new NumberType("SMALLINT", -32768, 32767, new byte[] {0, -128}, new byte[] {-1, 127}));
        numberTypeList.add(
            new NumberType("MEDIUMINT", -8388608, 8388607, new byte[] {0, 0, -128}, new byte[] {-1, -1, 127}));
        numberTypeList.add(
            new NumberType("INT", -2147483648, 2147483647, new byte[] {0, 0, 0, -128}, new byte[] {-1, -1, -1, 127}));
        numberTypeList.add(
            new NumberType("BIGINT", Long.MIN_VALUE, Long.MAX_VALUE, new byte[] {0, 0, 0, 0, 0, 0, 0, -128},
                new byte[] {-1, -1, -1, -1, -1, -1, -1, 127}));

        // unsigned
        numberTypeList.add(new NumberType("TINYINT", 0, 255, new byte[] {0}, new byte[] {-1}, true));
        numberTypeList.add(new NumberType("SMALLINT", 0, 65535, new byte[] {0, 0}, new byte[] {-1, -1}, true));
        numberTypeList.add(
            new NumberType("MEDIUMINT", 0, 16777215, new byte[] {0, 0, 0}, new byte[] {-1, -1, -1}, true));
        numberTypeList.add(
            new NumberType("INT", 0, 4294967295L, new byte[] {0, 0, 0, 0}, new byte[] {-1, -1, -1, -1},
                true));
        numberTypeList.add(
            new NumberType("BIGINT", 0, -1L, new byte[] {0, 0, 0, 0, 0, 0, 0, 0},
                new byte[] {-1, -1, -1, -1, -1, -1, -1, -1}, true));

        floatTypeList.add(new FloatType("float", "3.141592", new byte[] {4}, new byte[] {-40, 15, 73, 64}));
        floatTypeList.add(new FloatType("float(7,4)", "3.141592", new byte[] {4}, new byte[] {-7, 15, 73, 64}));
        floatTypeList.add(
            new FloatType("double", "3.141592", new byte[] {8}, new byte[] {122, 0, -117, -4, -6, 33, 9, 64}));
    }

    @Test
    public void testNumber() {
        for (NumberType numberType : numberTypeList) {
            Field fieldMin =
                MakeFieldFactory.makeField(numberType.type, numberType.min, defaultCharset, false, numberType.unsigned);
            Assert.assertArrayEquals(new byte[] {}, fieldMin.doGetTableMeta());
            Assert.assertArrayEquals(numberType.dataMin, fieldMin.encode());
            Field fieldMax =
                MakeFieldFactory.makeField(numberType.type, numberType.max, defaultCharset, false, numberType.unsigned);
            Assert.assertArrayEquals(new byte[] {}, fieldMax.doGetTableMeta());
            Assert.assertArrayEquals(numberType.dataMax, fieldMax.encode());
        }

    }

    @Test
    public void testOutOfRangeTruncation() {
        Field fieldMax =
            MakeFieldFactory.makeField("float(10,3)", "2147483647", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {4}, fieldMax.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-128, -106, 24, 75}, fieldMax.encode());

        Field fieldMin =
            MakeFieldFactory.makeField("float(10,3)", "-2147483647", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {4}, fieldMin.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-128, -106, 24, -53}, fieldMin.encode());
    }

    @Test
    public void testFloat() {
        for (FloatType floatType : floatTypeList) {
            Field fieldMin =
                MakeFieldFactory.makeField(floatType.type, floatType.value, defaultCharset, false, false);
            Assert.assertArrayEquals(floatType.meta, fieldMin.doGetTableMeta());
            Assert.assertArrayEquals(floatType.data, fieldMin.encode());
        }
    }

    public static class FloatType {
        private String type;
        private String value;
        private byte[] meta;
        private byte[] data;

        public FloatType(String type, String value, byte[] meta, byte[] data) {
            this.type = type;
            this.value = value;
            this.meta = meta;
            this.data = data;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            FloatType floatType = (FloatType) o;
            return Objects.equals(type, floatType.type) && Objects.equals(value, floatType.value)
                && Arrays.equals(meta, floatType.meta) && Arrays.equals(data, floatType.data);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(type, value);
            result = 31 * result + Arrays.hashCode(meta);
            result = 31 * result + Arrays.hashCode(data);
            return result;
        }

        @Override
        public String toString() {
            return "FloatType{" +
                "type='" + type + '\'' +
                ", value='" + value + '\'' +
                ", meta=" + Arrays.toString(meta) +
                ", data=" + Arrays.toString(data) +
                '}';
        }
    }

    public static class NumberType {
        private String type;
        private long max;
        private long min;

        private byte[] meta;
        private byte[] dataMin;
        private byte[] dataMax;

        private boolean unsigned;

        public NumberType(String type, long min, long max, byte[] dataMin, byte[] dataMax) {
            this(type, min, max, dataMin, dataMax, false);
        }

        public NumberType(String type, long min, long max, byte[] dataMin, byte[] dataMax, boolean unsigned) {
            this.type = type;
            this.max = max;
            this.min = min;
            this.dataMin = dataMin;
            this.dataMax = dataMax;
            this.unsigned = unsigned;
        }

        @Override
        public String toString() {
            return "NumberType{" +
                "type='" + type + '\'' +
                ", max=" + max +
                ", min=" + min +
                ", meta=" + Arrays.toString(meta) +
                ", dataMin=" + Arrays.toString(dataMin) +
                ", dataMax=" + Arrays.toString(dataMax) +
                ", unsigned=" + unsigned +
                '}';
        }
    }
}
