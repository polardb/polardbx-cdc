/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.com.polardbx.binlog.format.field;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.canal.binlog.JsonConversion;
import com.aliyun.polardbx.binlog.canal.binlog.LogBuffer;
import com.aliyun.polardbx.binlog.canal.binlog.event.RowsLogBuffer;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * JsonField对JSON中数值类型(BigDecimal/BigInteger等)的编码正确性测试。
 * 背景：fastjson将JSON中带小数的数值解析为BigDecimal、超出long范围的整数解析为BigInteger，
 * 修复前serialJsonValue无对应分支会静默不写任何数据，导致生成的binlog中JSON二进制内部
 * offset错乱、无法解析。
 * 编码策略：小数按OPAQUE decimal编码（数值无损，不受double 17位有效数字限制；解码文本在
 * 小数末段全为0时会丢失尾零，如10000.00解出10000.0，故文本尾零不做保证）；整数依次尝试
 * INT64、UINT64（与MySQL一致），超出UINT64_MAX走decimal编码；超出decimal精度上限(65,30)退化DOUBLE。
 */
public class JsonFieldDecimalTest extends BaseTest {

    private static final String DEFAULT_CHARSET = "utf8";

    private Field makeJsonField(String json) {
        return MakeFieldFactory.makeField("json", json, DEFAULT_CHARSET, true, false);
    }

    private String encodeAndDecode(String json) {
        Field field = makeJsonField(json);
        byte[] data = field.encode();
        RowsLogBuffer rowsLogBuffer = new RowsLogBuffer(new LogBuffer(data, 0, data.length), 1, DEFAULT_CHARSET);
        Serializable decoded = rowsLogBuffer.nextValue(field.getMysqlType().getType(), 4);
        return decoded.toString();
    }

    /**
     * encode()输出为: 4字节长度前缀 + JSONB类型字节 + payload，取顶层标量的JSONB类型字节
     */
    private int topLevelJsonbType(String json) {
        return makeJsonField(json).encode()[4] & 0xff;
    }

    @Test
    public void testDecimalInObject() {
        String json = "{\"brokenCount\":12.5,\"unloadCount\":33.04,\"name\":\"abc\"}";
        JSONObject decoded = JSON.parseObject(encodeAndDecode(json));
        Assert.assertEquals(0, new BigDecimal("12.5").compareTo(decoded.getBigDecimal("brokenCount")));
        Assert.assertEquals(0, new BigDecimal("33.04").compareTo(decoded.getBigDecimal("unloadCount")));
        Assert.assertEquals("abc", decoded.getString("name"));
    }

    /**
     * 复现线上损坏场景：数值字段与字符串字段交错，修复前数值字段丢失数据导致
     * 后续字符串字段的offset被数值字段的value entry悬空引用，解析报错
     */
    @Test
    public void testDecimalInterleavedWithString() {
        String json = "{\"businessOrderNumber\":\"H36451314A\",\"brokenCount\":0.0,"
            + "\"associatedTime\":\"2026-07-30 11:00:50\",\"unloadCount\":33.04,"
            + "\"receiverCode\":\"91320105MACYW8E487-1\",\"transportPrice\":128.75,"
            + "\"orderTotal\":10000.00,\"missingCount\":0}";
        JSONObject decoded = JSON.parseObject(encodeAndDecode(json));
        Assert.assertEquals("H36451314A", decoded.getString("businessOrderNumber"));
        Assert.assertEquals("2026-07-30 11:00:50", decoded.getString("associatedTime"));
        Assert.assertEquals("91320105MACYW8E487-1", decoded.getString("receiverCode"));
        Assert.assertEquals(0, new BigDecimal("0.0").compareTo(decoded.getBigDecimal("brokenCount")));
        Assert.assertEquals(0, new BigDecimal("33.04").compareTo(decoded.getBigDecimal("unloadCount")));
        Assert.assertEquals(0, new BigDecimal("128.75").compareTo(decoded.getBigDecimal("transportPrice")));
        Assert.assertEquals(0, new BigDecimal("10000.00").compareTo(decoded.getBigDecimal("orderTotal")));
        Assert.assertEquals(0, decoded.getIntValue("missingCount"));
    }

    @Test
    public void testDecimalNumericRoundTrip() {
        // decimal编码语义为数值无损：解码文本在小数末段全为0时会丢尾零(10000.00解出10000.0)，
        // 文本尾零不做保证，统一断言数值相等
        Assert.assertEquals(JsonConversion.JSONB_TYPE_OPAQUE, topLevelJsonbType("12.5"));
        JSONObject decoded = JSON.parseObject(encodeAndDecode("{\"a\":12.50}"));
        Assert.assertEquals(0, new BigDecimal("12.50").compareTo(decoded.getBigDecimal("a")));
        // 小数段全零场景：数值无损，scale可能退化
        decoded = JSON.parseObject(encodeAndDecode("{\"orderTotal\":10000.00}"));
        Assert.assertEquals(0, new BigDecimal("10000.00").compareTo(decoded.getBigDecimal("orderTotal")));
    }

    @Test
    public void testNegativeDecimal() {
        JSONObject decoded = JSON.parseObject(encodeAndDecode("{\"a\":-123.456,\"b\":-0.001}"));
        Assert.assertEquals(0, new BigDecimal("-123.456").compareTo(decoded.getBigDecimal("a")));
        Assert.assertEquals(0, new BigDecimal("-0.001").compareTo(decoded.getBigDecimal("b")));
    }

    @Test
    public void testDecimalInArray() {
        JSONArray decoded = JSON.parseArray(encodeAndDecode("[1.5,2,\"x\",-3.25]"));
        Assert.assertEquals(0, new BigDecimal("1.5").compareTo(decoded.getBigDecimal(0)));
        Assert.assertEquals(2, decoded.getIntValue(1));
        Assert.assertEquals("x", decoded.getString(2));
        Assert.assertEquals(0, new BigDecimal("-3.25").compareTo(decoded.getBigDecimal(3)));
    }

    @Test
    public void testTopLevelDecimal() {
        Assert.assertEquals(0, new BigDecimal("12.5").compareTo(new BigDecimal(encodeAndDecode("12.5"))));
    }

    @Test
    public void testScientificNotationDecimal() {
        // 科学计数法小数同样按DOUBLE编码
        JSONObject decoded = JSON.parseObject(encodeAndDecode("{\"a\":1E+2}"));
        Assert.assertEquals(0, new BigDecimal("100").compareTo(decoded.getBigDecimal("a")));
    }

    @Test
    public void testHugeScaleFallbackToDouble() {
        // scale超过MySQL decimal上限(30)，退化为DOUBLE编码
        String bigScale = "0.1234567890123456789012345678901234567890";
        Assert.assertEquals(JsonConversion.JSONB_TYPE_DOUBLE, topLevelJsonbType(bigScale));
        JSONObject decoded = JSON.parseObject(encodeAndDecode("{\"a\":" + bigScale + "}"));
        Assert.assertEquals(new BigDecimal(bigScale).doubleValue(), decoded.getDoubleValue("a"), 0.0d);
    }

    @Test
    public void testUint64LowerBound() {
        // 2^63，刚超出Long.MAX_VALUE，与MySQL一致存为UINT64
        String v = "9223372036854775808";
        Assert.assertEquals(JsonConversion.JSONB_TYPE_UINT64, topLevelJsonbType(v));
        JSONObject decoded = JSON.parseObject(encodeAndDecode("{\"a\":" + v + "}"));
        Assert.assertEquals(0, new BigDecimal(v).compareTo(decoded.getBigDecimal("a")));
    }

    @Test
    public void testUint64UpperBound() {
        // UINT64_MAX = 2^64-1
        String v = "18446744073709551615";
        Assert.assertEquals(JsonConversion.JSONB_TYPE_UINT64, topLevelJsonbType(v));
        JSONObject decoded = JSON.parseObject(encodeAndDecode("{\"a\":" + v + "}"));
        Assert.assertEquals(0, new BigDecimal(v).compareTo(decoded.getBigDecimal("a")));
    }

    @Test
    public void testIntegerBeyondUint64Max() {
        // 超出UINT64_MAX的整数走decimal编码，数值无损
        String v = "18446744073709551616";
        Assert.assertEquals(JsonConversion.JSONB_TYPE_OPAQUE, topLevelJsonbType(v));
        JSONObject decoded = JSON.parseObject(encodeAndDecode("{\"a\":" + v + "}"));
        Assert.assertEquals(0, new BigDecimal(v).compareTo(decoded.getBigDecimal("a")));
    }

    @Test
    public void testNegativeIntegerBeyondLongRange() {
        // 低于Long.MIN_VALUE的负整数无法用INT64/UINT64表示，走decimal编码
        String v = "-92233720368547758080000";
        Assert.assertEquals(JsonConversion.JSONB_TYPE_OPAQUE, topLevelJsonbType(v));
        JSONObject decoded = JSON.parseObject(encodeAndDecode("{\"a\":" + v + "}"));
        Assert.assertEquals(0, new BigDecimal(v).compareTo(decoded.getBigDecimal("a")));
    }

    @Test
    public void testBigIntegerWithinLongRange() {
        // 可容纳于long的BigInteger降级为Long编码
        Assert.assertEquals(JsonConversion.JSONB_TYPE_INT64, topLevelJsonbType("9223372036854775807"));
        JSONObject decoded = JSON.parseObject(encodeAndDecode("{\"a\":9223372036854775807}"));
        Assert.assertEquals(Long.MAX_VALUE, decoded.getLongValue("a"));
    }

    @Test
    public void testMixedNestedDecimal() {
        String json = "{\"outer\":{\"price\":99.99,\"tags\":[0.5,\"t\"]},\"count\":3}";
        JSONObject decoded = JSON.parseObject(encodeAndDecode(json));
        JSONObject outer = decoded.getJSONObject("outer");
        Assert.assertEquals(0, new BigDecimal("99.99").compareTo(outer.getBigDecimal("price")));
        Assert.assertEquals(0, new BigDecimal("0.5").compareTo(outer.getJSONArray("tags").getBigDecimal(0)));
        Assert.assertEquals("t", outer.getJSONArray("tags").getString(1));
        Assert.assertEquals(3, decoded.getIntValue("count"));
    }
}
