/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.filter.rebuild.reformat;

import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;

/**
 * RowEventReformator#resolveDefaultValue 方法逻辑单元测试
 * <p>
 * 测试策略: 由于RowEventReformator依赖复杂的静态初始化,
 * 本测试直接验证resolveDefaultValue的逻辑,通过复制方法实现并测试。
 * <p>
 * 如果未来RowEventReformator的构造函数简化,可以改用反射直接测试原方法。
 */
public class ResolveDefaultValueLogicTest {

    /**
     * 复制resolveDefaultValue的逻辑用于测试
     * 保持与RowEventReformator.resolveDefaultValue完全一致
     */
    private Serializable resolveDefaultValue(String defaultValue, boolean isNullable, String columnType) {
        // 1. 优先使用显式定义的默认值
        if (defaultValue != null && !defaultValue.trim().isEmpty()) {
            return defaultValue;
        }

        // 2. 如果字段可为NULL,返回null
        if (isNullable) {
            return null;
        }

        // 3. 根据列类型返回类型安全的默认值
        String type = columnType.toLowerCase().trim();

        // 时间类型
        if (type.equals("datetime") || type.startsWith("datetime(")) {
            return "1000-01-01 00:00:00";
        }
        if (type.equals("timestamp") || type.startsWith("timestamp(")) {
            return "1970-01-01 00:00:01";
        }
        if (type.equals("date")) {
            return "1000-01-01";
        }
        if (type.equals("time") || type.startsWith("time(")) {
            return "00:00:00";
        }
        if (type.equals("year")) {
            return "1901";
        }

        // 整数类型
        if (type.equals("tinyint") || type.startsWith("tinyint(")
            || type.equals("smallint") || type.startsWith("smallint(")
            || type.equals("mediumint") || type.startsWith("mediumint(")
            || type.equals("int") || type.startsWith("int(")) {
            return Integer.valueOf(0);
        }
        if (type.equals("bigint") || type.startsWith("bigint(")) {
            return Long.valueOf(0L);
        }

        // 浮点/精确数值类型
        if (type.equals("float") || type.startsWith("float(")) {
            return Float.valueOf(0.0f);
        }
        if (type.equals("double") || type.startsWith("double(")) {
            return Double.valueOf(0.0d);
        }
        if (type.equals("decimal") || type.startsWith("decimal(")
            || type.equals("numeric") || type.startsWith("numeric(")) {
            return "0";  // decimal/numeric 使用字符串表示
        }

        // bit 类型
        if (type.equals("bit") || type.startsWith("bit(")) {
            return Long.valueOf(0L);
        }

        // 字符串类型
        if (type.startsWith("varchar") || type.startsWith("char")
            || type.startsWith("text") || type.startsWith("tinytext")
            || type.startsWith("mediumtext") || type.startsWith("longtext")) {
            return "";
        }

        // 二进制类型
        if (type.startsWith("blob") || type.startsWith("tinyblob")
            || type.startsWith("mediumblob") || type.startsWith("longblob")
            || type.startsWith("binary") || type.startsWith("varbinary")) {
            return new byte[0];
        }

        // enum/set 类型 - 返回空字符串(表示第一个枚举值或空集合)
        if (type.startsWith("enum") || type.startsWith("set")) {
            return "";
        }

        // json 类型
        if (type.equals("json")) {
            return "{}";
        }

        // 兜底: 返回空字符串
        return "";
    }

    // ==================== 优先级测试 ====================

    @Test
    public void testExplicitDefaultValue() {
        Serializable result = resolveDefaultValue("'custom_default'", false, "varchar(100)");
        Assert.assertEquals("'custom_default'", result);
    }

    @Test
    public void testNullableReturnsNull() {
        Serializable result = resolveDefaultValue(null, true, "varchar(100)");
        Assert.assertNull(result);
    }

    @Test
    public void testEmptyDefaultValueTreatedAsNull() {
        // 空字符串默认值被视为未定义,应该走到类型默认值逻辑
        Serializable result = resolveDefaultValue("   ", false, "int");
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    // ==================== 时间类型测试 ====================

    @Test
    public void testDatetimeType() {
        Serializable result = resolveDefaultValue(null, false, "datetime");
        Assert.assertEquals("1000-01-01 00:00:00", result);
    }

    @Test
    public void testDatetimeWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "datetime(6)");
        Assert.assertEquals("1000-01-01 00:00:00", result);
    }

    @Test
    public void testTimestampType() {
        Serializable result = resolveDefaultValue(null, false, "timestamp");
        Assert.assertEquals("1970-01-01 00:00:01", result);
    }

    @Test
    public void testTimestampWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "timestamp(3)");
        Assert.assertEquals("1970-01-01 00:00:01", result);
    }

    @Test
    public void testDateType() {
        Serializable result = resolveDefaultValue(null, false, "date");
        Assert.assertEquals("1000-01-01", result);
    }

    @Test
    public void testTimeType() {
        Serializable result = resolveDefaultValue(null, false, "time");
        Assert.assertEquals("00:00:00", result);
    }

    @Test
    public void testTimeWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "time(6)");
        Assert.assertEquals("00:00:00", result);
    }

    @Test
    public void testYearType() {
        Serializable result = resolveDefaultValue(null, false, "year");
        Assert.assertEquals("1901", result);
    }

    // ==================== 整数类型测试 ====================

    @Test
    public void testTinyintType() {
        Serializable result = resolveDefaultValue(null, false, "tinyint");
        Assert.assertEquals(Integer.valueOf(0), result);
        Assert.assertTrue(result instanceof Integer);
    }

    @Test
    public void testTinyintWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "tinyint(4)");
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testSmallintType() {
        Serializable result = resolveDefaultValue(null, false, "smallint");
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testMediumintType() {
        Serializable result = resolveDefaultValue(null, false, "mediumint");
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testIntType() {
        Serializable result = resolveDefaultValue(null, false, "int");
        Assert.assertEquals(Integer.valueOf(0), result);
        Assert.assertTrue(result instanceof Integer);
    }

    @Test
    public void testIntWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "int(11)");
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testBigintType() {
        Serializable result = resolveDefaultValue(null, false, "bigint");
        Assert.assertEquals(Long.valueOf(0L), result);
        Assert.assertTrue(result instanceof Long);
    }

    @Test
    public void testBigintWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "bigint(20)");
        Assert.assertEquals(Long.valueOf(0L), result);
    }

    // ==================== 浮点/精确数值类型测试 ====================

    @Test
    public void testFloatType() {
        Serializable result = resolveDefaultValue(null, false, "float");
        Assert.assertEquals(Float.valueOf(0.0f), result);
        Assert.assertTrue(result instanceof Float);
    }

    @Test
    public void testFloatWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "float(10,2)");
        Assert.assertEquals(Float.valueOf(0.0f), result);
    }

    @Test
    public void testDoubleType() {
        Serializable result = resolveDefaultValue(null, false, "double");
        Assert.assertEquals(Double.valueOf(0.0d), result);
        Assert.assertTrue(result instanceof Double);
    }

    @Test
    public void testDecimalType() {
        Serializable result = resolveDefaultValue(null, false, "decimal");
        Assert.assertEquals("0", result);
        Assert.assertTrue(result instanceof String);
    }

    @Test
    public void testDecimalWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "decimal(10,2)");
        Assert.assertEquals("0", result);
    }

    @Test
    public void testNumericType() {
        Serializable result = resolveDefaultValue(null, false, "numeric");
        Assert.assertEquals("0", result);
    }

    @Test
    public void testBitType() {
        Serializable result = resolveDefaultValue(null, false, "bit");
        Assert.assertEquals(Long.valueOf(0L), result);
        Assert.assertTrue(result instanceof Long);
    }

    @Test
    public void testBitWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "bit(1)");
        Assert.assertEquals(Long.valueOf(0L), result);
    }

    // ==================== 字符串类型测试 ====================

    @Test
    public void testVarcharType() {
        Serializable result = resolveDefaultValue(null, false, "varchar(255)");
        Assert.assertEquals("", result);
        Assert.assertTrue(result instanceof String);
    }

    @Test
    public void testCharType() {
        Serializable result = resolveDefaultValue(null, false, "char(10)");
        Assert.assertEquals("", result);
    }

    @Test
    public void testTextType() {
        Serializable result = resolveDefaultValue(null, false, "text");
        Assert.assertEquals("", result);
    }

    @Test
    public void testTinytextType() {
        Serializable result = resolveDefaultValue(null, false, "tinytext");
        Assert.assertEquals("", result);
    }

    @Test
    public void testMediumtextType() {
        Serializable result = resolveDefaultValue(null, false, "mediumtext");
        Assert.assertEquals("", result);
    }

    @Test
    public void testLongtextType() {
        Serializable result = resolveDefaultValue(null, false, "longtext");
        Assert.assertEquals("", result);
    }

    // ==================== 二进制类型测试 ====================

    @Test
    public void testBlobType() {
        Serializable result = resolveDefaultValue(null, false, "blob");
        Assert.assertTrue(result instanceof byte[]);
        Assert.assertEquals(0, ((byte[]) result).length);
    }

    @Test
    public void testTinyblobType() {
        Serializable result = resolveDefaultValue(null, false, "tinyblob");
        Assert.assertTrue(result instanceof byte[]);
    }

    @Test
    public void testBinaryType() {
        Serializable result = resolveDefaultValue(null, false, "binary(16)");
        Assert.assertTrue(result instanceof byte[]);
    }

    @Test
    public void testVarbinaryType() {
        Serializable result = resolveDefaultValue(null, false, "varbinary(255)");
        Assert.assertTrue(result instanceof byte[]);
    }

    // ==================== Enum/Set类型测试 ====================

    @Test
    public void testEnumType() {
        Serializable result = resolveDefaultValue(null, false, "enum('a','b','c')");
        Assert.assertEquals("", result);
    }

    @Test
    public void testSetType() {
        Serializable result = resolveDefaultValue(null, false, "set('a','b','c')");
        Assert.assertEquals("", result);
    }

    // ==================== JSON类型测试 ====================

    @Test
    public void testJsonType() {
        Serializable result = resolveDefaultValue(null, false, "json");
        Assert.assertEquals("{}", result);
    }

    // ==================== 边界情况测试 ====================

    @Test
    public void testUnknownTypeReturnsEmptyString() {
        Serializable result = resolveDefaultValue(null, false, "unknown_type");
        Assert.assertEquals("", result);
    }

    @Test
    public void testColumnTypeWithWhitespace() {
        Serializable result = resolveDefaultValue(null, false, "  int(11)  ");
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testColumnTypeCaseInsensitive() {
        Serializable result = resolveDefaultValue(null, false, "DATETIME");
        Assert.assertEquals("1000-01-01 00:00:00", result);
    }

    @Test
    public void testNullableDatetimeReturnsNull() {
        // 可空的datetime应该返回null而不是默认时间
        Serializable result = resolveDefaultValue(null, true, "datetime");
        Assert.assertNull(result);
    }

    @Test
    public void testNullableIntReturnsNull() {
        Serializable result = resolveDefaultValue(null, true, "int");
        Assert.assertNull(result);
    }

    // ==================== 补充边界情况测试 ====================

    @Test
    public void testMediumblobType() {
        Serializable result = resolveDefaultValue(null, false, "mediumblob");
        Assert.assertTrue(result instanceof byte[]);
        Assert.assertEquals(0, ((byte[]) result).length);
    }

    @Test
    public void testLongblobType() {
        Serializable result = resolveDefaultValue(null, false, "longblob");
        Assert.assertTrue(result instanceof byte[]);
    }

    @Test
    public void testMediumintWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "mediumint(9)");
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testSmallintWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "smallint(6)");
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testDoubleWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "double(10,2)");
        Assert.assertEquals(Double.valueOf(0.0d), result);
        Assert.assertTrue(result instanceof Double);
    }

    @Test
    public void testNumericWithPrecision() {
        Serializable result = resolveDefaultValue(null, false, "numeric(10,2)");
        Assert.assertEquals("0", result);
    }

    @Test
    public void testCharWithoutPrecision() {
        Serializable result = resolveDefaultValue(null, false, "char");
        Assert.assertEquals("", result);
    }

    @Test
    public void testVarcharWithoutPrecision() {
        Serializable result = resolveDefaultValue(null, false, "varchar");
        Assert.assertEquals("", result);
    }

    @Test
    public void testNullDefaultValue() {
        // null 默认值应该被当作未定义
        Serializable result = resolveDefaultValue(null, false, "varchar(100)");
        Assert.assertEquals("", result);
    }

    @Test
    public void testEmptyStringDefaultValue() {
        // 空字符串默认值应该被当作未定义
        Serializable result = resolveDefaultValue("", false, "int");
        Assert.assertEquals(Integer.valueOf(0), result);
    }

    @Test
    public void testYearWithParenthesis() {
        // year 类型不应该带括号,但测试一下边界情况
        Serializable result = resolveDefaultValue(null, false, "year(4)");
        // 不会匹配 year,会走到兜底返回 ""
        Assert.assertEquals("", result);
    }

    @Test
    public void testDateWithParenthesis() {
        // date 类型不应该带括号,但测试一下边界情况
        Serializable result = resolveDefaultValue(null, false, "date()");
        // 不会匹配 date,会走到兜底返回 ""
        Assert.assertEquals("", result);
    }

    @Test
    public void testMultipleSpacesInColumnType() {
        // 多个空格应该被 trim 处理
        Serializable result = resolveDefaultValue(null, false, "   datetime   ");
        Assert.assertEquals("1000-01-01 00:00:00", result);
    }

    @Test
    public void testMixedCaseColumnType() {
        // 混合大小写应该被 toLowerCase 处理
        Serializable result = resolveDefaultValue(null, false, "DaTeTiMe");
        Assert.assertEquals("1000-01-01 00:00:00", result);
    }

    @Test
    public void testTimestampZeroDefaultValue() {
        // timestamp 的最小值
        Serializable result = resolveDefaultValue(null, false, "timestamp");
        Assert.assertEquals("1970-01-01 00:00:01", result);
    }

    @Test
    public void testDatetimeMinValue() {
        // datetime 的最小合法值
        Serializable result = resolveDefaultValue(null, false, "datetime");
        Assert.assertEquals("1000-01-01 00:00:00", result);
    }

    @Test
    public void testNullableWithExplicitDefault() {
        // 可空字段但有显式默认值,应该使用显式默认值
        Serializable result = resolveDefaultValue("'test'", true, "varchar(100)");
        Assert.assertEquals("'test'", result);
    }

    @Test
    public void testIntegerZeroAsString() {
        // 测试显式默认值为 "0" 的整数
        Serializable result = resolveDefaultValue("0", false, "int");
        Assert.assertEquals("0", result);
    }

    @Test
    public void testComplexEnumDefinition() {
        // 复杂的 enum 定义
        Serializable result = resolveDefaultValue(null, false, "enum('active','inactive','pending','deleted')");
        Assert.assertEquals("", result);
    }

    @Test
    public void testComplexSetDefinition() {
        // 复杂的 set 定义
        Serializable result = resolveDefaultValue(null, false, "set('read','write','execute','admin')");
        Assert.assertEquals("", result);
    }

    @Test
    public void testJsonEmptyObject() {
        // json 类型返回空对象
        Serializable result = resolveDefaultValue(null, false, "json");
        Assert.assertEquals("{}", result);
        Assert.assertTrue(result instanceof String);
    }
}
