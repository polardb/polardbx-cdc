/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.filter;

import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Method;

/**
 * @author yudong
 * @since 2023/11/30 14:25
 **/
public class RecoveryFilterTest {

    private static Class<?> clazz;

    @Before
    public void before() {
        try {
            clazz = Class.forName(RecoveryFilter.class.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @SneakyThrows
    public void testExtractTraceId() {
        Method method = clazz.getDeclaredMethod("extractTraceId", String.class);
        method.setAccessible(true);

        String res;
        res = (String) method.invoke(null, "/*DRDS /127.0.0.1/12fb0789b6000000/0// */");
        Assert.assertEquals("12fb0789b6000000", res);

        // 测试同一个事务内有多个逻辑SQL的情况
        res = (String) method.invoke(null, "/*DRDS /127.0.0.1/12fb0789b6000000-1/0// */");
        Assert.assertEquals("12fb0789b6000000-1", res);

        // 测试client ip异常的情况
        res = (String) method.invoke(null, "/*DRDS /null/12fb0789b6000000/0// */");
        Assert.assertEquals("12fb0789b6000000", res);
        res = (String) method.invoke(null, "/*DRDS //12fb0789b6000000/0// */");
        Assert.assertEquals("12fb0789b6000000", res);

        // 测试physical sql id异常的情况
        res = (String) method.invoke(null, "/*DRDS /127.0.0.1/12fb0789b6000000/// */");
        Assert.assertNull(res);
    }

    @Test
    @SneakyThrows
    public void testMatchTraceId() {
        Method method = clazz.getDeclaredMethod("matchTraceId", String.class, String.class);
        method.setAccessible(true);

        boolean res;

        // 精确匹配某个逻辑SQL
        res = (boolean) method.invoke(null, "12fb0789b6000000-1", "12fb0789b6000000-1");
        Assert.assertTrue(res);
        res = (boolean) method.invoke(null, "12fb0789b6000000-2", "12fb0789b6000000-1");
        Assert.assertFalse(res);
        res = (boolean) method.invoke(null, "12fb0789b6000000", "12fb0789b6000000-1");
        Assert.assertFalse(res);
        res = (boolean) method.invoke(null, "12fb0789b6000000-10", "12fb0789b6000000-1");
        Assert.assertFalse(res);

        // 匹配某个事务内的所有逻辑SQL
        res = (boolean) method.invoke(null, "12fb0789b6000000-1", "12fb0789b6000000");
        Assert.assertTrue(res);
        res = (boolean) method.invoke(null, "12fb0789b6000000-2", "12fb0789b6000000");
        Assert.assertTrue(res);
    }
}
