/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.api.rds;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

/**
 * AbstractRequest HmacSHA1Encrypt method unit tests
 */
public class AbstractRequestTest {

    /**
     * 可测试的AbstractRequest子类，重写了HmacSHA1Encrypt方法以避免依赖CharsetCache
     */
    public static class TestableAbstractRequest extends AbstractRequest<Object> {
        @Override
        protected Object processResult(org.apache.http.HttpResponse response) throws Exception {
            return null;
        }

        /**
         * 公开访问HmacSHA1Encrypt方法
         */
        public byte[] publicHmacSHA1Encrypt(String encryptText, String encryptKey) throws Exception {
            return HmacSHA1Encrypt(encryptText, encryptKey);
        }
    }

    @Test
    public void testHmacSHA1Encrypt() throws Exception {
        // 创建一个可测试的子类实例
        TestableAbstractRequest request = new TestableAbstractRequest();

        // 测试数据
        String encryptText = "Hello World";
        String encryptKey = "secretKey";

        // 执行方法
        byte[] result = request.publicHmacSHA1Encrypt(encryptText, encryptKey);

        // 验证结果不为null且长度正确
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Result should not be empty", result.length > 0);

        // 验证相同输入产生相同输出
        byte[] result2 = request.publicHmacSHA1Encrypt(encryptText, encryptKey);
        Assert.assertArrayEquals("Same inputs should produce same output", result, result2);

        // 验证不同输入产生不同输出
        byte[] result3 = request.publicHmacSHA1Encrypt("Different Text", encryptKey);
        Assert.assertFalse("Different text should produce different output", java.util.Arrays.equals(result, result3));

        // 验证不同密钥产生不同输出
        byte[] result4 = request.publicHmacSHA1Encrypt(encryptText, "differentKey");
        Assert.assertFalse("Different key should produce different output", java.util.Arrays.equals(result, result4));
    }

    @Test
    public void testHmacSHA1EncryptWithSpecialCharacters() throws Exception {
        // 创建一个可测试的子类实例
        TestableAbstractRequest request = new TestableAbstractRequest();

        // 测试包含特殊字符的数据
        String encryptText = "Hello World!@#$%^&*()_+-={}[]|\\:;\"'<>?,./";
        String encryptKey = "密钥key123!@#";

        // 执行方法
        byte[] result = request.publicHmacSHA1Encrypt(encryptText, encryptKey);

        // 验证结果不为null且长度正确
        Assert.assertNotNull("Result should not be null", result);
        Assert.assertTrue("Result should not be empty", result.length > 0);
    }

    @Test
    public void testHmacSHA1EncryptEmptyInputs() throws Exception {
        // 创建一个可测试的子类实例
        TestableAbstractRequest request = new TestableAbstractRequest();

        // 测试空字符串输入
        String encryptText = "";
        String encryptKey = "nonEmptyKey";

        // 执行方法
        byte[] result = request.publicHmacSHA1Encrypt(encryptText, encryptKey);

        // 验证结果不为null
        Assert.assertNotNull("Result should not be null", result);

        // 测试空密钥应该抛出异常
        try {
            request.publicHmacSHA1Encrypt("someText", "");
            Assert.fail("Expected IllegalArgumentException for empty key");
        } catch (IllegalArgumentException e) {
            // 这是我们期望的行为
            Assert.assertEquals("Empty key", e.getMessage());
        }
    }

    @Test
    public void testHmacSHA1EncryptKnownValues() throws Exception {
        // 创建一个可测试的子类实例
        TestableAbstractRequest request = new TestableAbstractRequest();

        // 使用标准Java API计算期望的结果
        String encryptText = "Hello World";
        String encryptKey = "secretKey";

        // 使用标准API计算HMAC-SHA1
        byte[] data = encryptKey.getBytes(StandardCharsets.UTF_8);
        javax.crypto.SecretKey secretKey = new javax.crypto.spec.SecretKeySpec(data, "HmacSHA1");
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
        mac.init(secretKey);
        byte[] expected = mac.doFinal(encryptText.getBytes(StandardCharsets.UTF_8));

        // 执行方法
        byte[] actual = request.publicHmacSHA1Encrypt(encryptText, encryptKey);

        // 验证结果与标准API计算的一致
        Assert.assertArrayEquals("Result should match standard HMAC-SHA1 implementation", expected, actual);
    }
}