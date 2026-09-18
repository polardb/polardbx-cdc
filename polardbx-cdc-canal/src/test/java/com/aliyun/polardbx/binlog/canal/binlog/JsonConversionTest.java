/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.alibaba.fastjson.JSON;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

/**
 * JsonConversion#escape 方法的单元测试
 * 由于 escape 是 private static 方法，通过反射进行测试
 */
public class JsonConversionTest {

    /**
     * 通过反射调用 private static escape 方法
     */
    private String invokeEscape(String data) throws Exception {
        Method method = JsonConversion.class.getDeclaredMethod("escape", String.class);
        method.setAccessible(true);
        StringBuilder result = (StringBuilder) method.invoke(null, data);
        return result.toString();
    }

    /**
     * 测试空字符串
     */
    @Test
    public void testEmptyString() throws Exception {
        Assert.assertEquals("", invokeEscape(""));
    }

    /**
     * 测试普通字符串（不包含任何需要转义的字符）
     */
    @Test
    public void testNormalString() throws Exception {
        Assert.assertEquals("hello world", invokeEscape("hello world"));
    }

    /**
     * 测试双引号转义
     */
    @Test
    public void testDoubleQuote() throws Exception {
        Assert.assertEquals("\\\"", invokeEscape("\""));
        Assert.assertEquals("hello\\\"world", invokeEscape("hello\"world"));
    }

    /**
     * 测试换行符转义
     */
    @Test
    public void testNewline() throws Exception {
        Assert.assertEquals("\\n", invokeEscape("\n"));
        Assert.assertEquals("hello\\nworld", invokeEscape("hello\nworld"));
    }

    /**
     * 测试回车符转义
     */
    @Test
    public void testCarriageReturn() throws Exception {
        Assert.assertEquals("\\r", invokeEscape("\r"));
        Assert.assertEquals("hello\\rworld", invokeEscape("hello\rworld"));
    }

    /**
     * 测试反斜杠转义
     */
    @Test
    public void testBackslash() throws Exception {
        Assert.assertEquals("\\\\", invokeEscape("\\"));
        Assert.assertEquals("hello\\\\world", invokeEscape("hello\\world"));
    }

    /**
     * 测试制表符转义
     */
    @Test
    public void testTab() throws Exception {
        Assert.assertEquals("\\t", invokeEscape("\t"));
        Assert.assertEquals("hello\\tworld", invokeEscape("hello\tworld"));
    }

    /**
     * 测试 c < 16 的控制字符（排除 \n=0x0A, \r=0x0D, \t=0x09）
     * 应输出 \\u000 + hex
     */
    @Test
    public void testControlCharsBelow16() throws Exception {
        // \0 (0x00)
        Assert.assertEquals("\\u0000", invokeEscape("\u0000"));
        // \1 (0x01)
        Assert.assertEquals("\\u0001", invokeEscape("\u0001"));
        // \u0002
        Assert.assertEquals("\\u0002", invokeEscape("\u0002"));
        // \u0007 (BEL)
        Assert.assertEquals("\\u0007", invokeEscape("\u0007"));
        // \u000B (VT, 0x0B)
        Assert.assertEquals("\\u000b", invokeEscape("\u000B"));
        // \u000C (FF, 0x0C)
        Assert.assertEquals("\\u000c", invokeEscape("\u000C"));
        // \u000E (0x0E)
        Assert.assertEquals("\\u000e", invokeEscape("\u000E"));
        // \u000F (0x0F)
        Assert.assertEquals("\\u000f", invokeEscape("\u000F"));
    }

    /**
     * 测试 16 <= c < 32 的控制字符（排除已处理的字符）
     * 应输出 \\u00 + hex
     */
    @Test
    public void testControlChars16To31() throws Exception {
        // \u0010 (16)
        Assert.assertEquals("\\u0010", invokeEscape("\u0010"));
        // \u0011 (17)
        Assert.assertEquals("\\u0011", invokeEscape("\u0011"));
        // \u001B (ESC, 27)
        Assert.assertEquals("\\u001b", invokeEscape("\u001B"));
        // \u001F (31)
        Assert.assertEquals("\\u001f", invokeEscape("\u001F"));
    }

    /**
     * 测试 0x7F <= c <= 0xA0 范围的字符
     * 应输出 \\u00 + hex
     */
    @Test
    public void testHighControlChars() throws Exception {
        // \u007F (DEL, 127)
        Assert.assertEquals("\\u007f", invokeEscape("\u007F"));
        // \u0080 (128)
        Assert.assertEquals("\\u0080", invokeEscape("\u0080"));
        // \u0090 (144)
        Assert.assertEquals("\\u0090", invokeEscape("\u0090"));
        // \u00A0 (NBSP, 160)
        Assert.assertEquals("\\u00a0", invokeEscape("\u00A0"));
    }

    /**
     * 测试不在 0x7F-0xA0 范围内的高字符（应直接输出）
     */
    @Test
    public void testNormalHighChars() throws Exception {
        // \u00A1 (161) - 超出范围，应直接输出
        Assert.assertEquals("\u00A1", invokeEscape("\u00A1"));
        // 中文字符
        Assert.assertEquals("中文", invokeEscape("中文"));
        // \u007E (~, 126) - 在 0x7F 之前，应直接输出
        Assert.assertEquals("~", invokeEscape("~"));
    }

    /**
     * 测试混合场景：字符串包含多种需转义的字符
     */
    @Test
    public void testMixedString() throws Exception {
        // 组合多种转义字符
        String input = "hello\"world\nfoo\rbar\\baz\t";
        String expected = "hello\\\"world\\nfoo\\rbar\\\\baz\\t";
        Assert.assertEquals(expected, invokeEscape(input));
    }

    /**
     * 测试混合场景：包含控制字符和普通字符
     */
    @Test
    public void testMixedControlAndNormal() throws Exception {
        // \u0001 + "abc" + \u0010 + "def" + \u007F
        String input = "\u0001abc\u0010def\u007F";
        String expected = "\\u0001abc\\u0010def\\u007f";
        Assert.assertEquals(expected, invokeEscape(input));
    }

    /**
     * 测试连续的特殊字符
     */
    @Test
    public void testConsecutiveSpecialChars() throws Exception {
        Assert.assertEquals("\\\"\\\"", invokeEscape("\"\""));
        Assert.assertEquals("\\n\\r\\t", invokeEscape("\n\r\t"));
        Assert.assertEquals("\\\\\\\\", invokeEscape("\\\\"));
    }

    /**
     * 测试单个普通字符
     */
    @Test
    public void testSingleNormalChar() throws Exception {
        Assert.assertEquals("a", invokeEscape("a"));
        Assert.assertEquals("Z", invokeEscape("Z"));
        Assert.assertEquals("0", invokeEscape("0"));
        Assert.assertEquals(" ", invokeEscape(" "));
    }

    /**
     * 模拟 toJsonString 中的用法：'"' + escape(value) + '"'
     * 构造 JSON 字符串值后，用 JSON 解析器解析，验证结果与原始字符串一致
     */
    private void assertJsonRoundTrip(String original) throws Exception {
        String escaped = invokeEscape(original);
        // 模拟 JsonConversion.toJsonString 中的拼接方式
        String jsonString = "\"" + escaped + "\"";
        // 用 JSON 解析器解析，应该还原出原始字符串
        String parsed = JSON.parseObject(jsonString, String.class);
        Assert.assertEquals(original, parsed);
    }

    /**
     * 验证普通字符串 escape 后构造的 JSON 可被正确解析还原
     */
    @Test
    public void testJsonRoundTripNormalString() throws Exception {
        assertJsonRoundTrip("hello world");
        assertJsonRoundTrip("abc123");
    }

    /**
     * 验证包含双引号的字符串 escape 后的 JSON 语义正确性
     */
    @Test
    public void testJsonRoundTripWithQuotes() throws Exception {
        assertJsonRoundTrip("say \"hello\"");
        assertJsonRoundTrip("\"");
    }

    /**
     * 验证包含换行、回车、制表符的字符串 escape 后的 JSON 语义正确性
     */
    @Test
    public void testJsonRoundTripWithWhitespaceChars() throws Exception {
        assertJsonRoundTrip("line1\nline2");
        assertJsonRoundTrip("col1\tcol2");
        assertJsonRoundTrip("cr\r");
        assertJsonRoundTrip("mixed\n\r\t");
    }

    /**
     * 验证包含反斜杠的字符串 escape 后的 JSON 语义正确性
     */
    @Test
    public void testJsonRoundTripWithBackslash() throws Exception {
        assertJsonRoundTrip("path\\to\\file");
        assertJsonRoundTrip("\\");
    }

    /**
     * 验证包含控制字符的字符串 escape 后的 JSON 语义正确性
     */
    @Test
    public void testJsonRoundTripWithControlChars() throws Exception {
        // c < 16 的控制字符
        assertJsonRoundTrip("before\u0001after");
        assertJsonRoundTrip("\u000B");
        // 16 <= c < 32 的控制字符
        assertJsonRoundTrip("before\u0010after");
        assertJsonRoundTrip("\u001F");
    }

    /**
     * 验证包含中文和 Unicode 字符的字符串 escape 后的 JSON 语义正确性
     */
    @Test
    public void testJsonRoundTripWithUnicode() throws Exception {
        assertJsonRoundTrip("中文测试");
        assertJsonRoundTrip("emoji: \uD83D\uDE00");
        assertJsonRoundTrip("\u00A1");
    }

    /**
     * 验证复杂混合字符串 escape 后的 JSON 语义正确性：
     * 同时包含双引号、反斜杠、换行、制表符、控制字符和中文
     */
    @Test
    public void testJsonRoundTripComplex() throws Exception {
        assertJsonRoundTrip("name: \"张三\"\npath: C:\\data\tvalue\u0001end");
        assertJsonRoundTrip("{\"key\": \"value\"}");
    }
}
