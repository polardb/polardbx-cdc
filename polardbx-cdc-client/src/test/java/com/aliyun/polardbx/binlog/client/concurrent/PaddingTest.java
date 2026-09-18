/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.client.concurrent;

import org.junit.Assert;
import org.junit.Test;

/**
 * Padding 缓存行填充包装类的单元测试
 */
public class PaddingTest {

    @Test
    public void testPaddingLongInitialValue() {
        Padding.PaddingLong pl = new Padding.PaddingLong(42);
        Assert.assertEquals(42L, pl.value);
    }

    @Test
    public void testPaddingLongZero() {
        Padding.PaddingLong pl = new Padding.PaddingLong(0);
        Assert.assertEquals(0L, pl.value);
    }

    @Test
    public void testPaddingLongNegative() {
        Padding.PaddingLong pl = new Padding.PaddingLong(-100);
        Assert.assertEquals(-100L, pl.value);
    }

    @Test
    public void testPaddingLongVolatileReadWrite() {
        Padding.PaddingLong pl = new Padding.PaddingLong(0);
        pl.value = Long.MAX_VALUE;
        Assert.assertEquals(Long.MAX_VALUE, pl.value);
        pl.value = Long.MIN_VALUE;
        Assert.assertEquals(Long.MIN_VALUE, pl.value);
    }

    @Test
    public void testPaddingThreadInitialNull() {
        Padding.PaddingThread pt = new Padding.PaddingThread(null);
        Assert.assertNull(pt.value);
    }

    @Test
    public void testPaddingThreadSetAndGet() {
        Thread t = Thread.currentThread();
        Padding.PaddingThread pt = new Padding.PaddingThread(t);
        Assert.assertSame(t, pt.value);
    }

    @Test
    public void testPaddingThreadVolatileReadWrite() {
        Padding.PaddingThread pt = new Padding.PaddingThread(null);
        Thread t = Thread.currentThread();
        pt.value = t;
        Assert.assertSame(t, pt.value);
        pt.value = null;
        Assert.assertNull(pt.value);
    }

    @Test
    public void testPaddingBooleanInitialTrue() {
        Padding.PaddingBoolean pb = new Padding.PaddingBoolean(true);
        Assert.assertTrue(pb.value);
    }

    @Test
    public void testPaddingBooleanInitialFalse() {
        Padding.PaddingBoolean pb = new Padding.PaddingBoolean(false);
        Assert.assertFalse(pb.value);
    }

    @Test
    public void testPaddingBooleanVolatileReadWrite() {
        Padding.PaddingBoolean pb = new Padding.PaddingBoolean(false);
        pb.value = true;
        Assert.assertTrue(pb.value);
        pb.value = false;
        Assert.assertFalse(pb.value);
    }

    @Test
    public void testMultiplePaddingLongInstances() {
        // 确保多个实例之间互不影响
        Padding.PaddingLong a = new Padding.PaddingLong(1);
        Padding.PaddingLong b = new Padding.PaddingLong(2);
        a.value = 100;
        Assert.assertEquals(100L, a.value);
        Assert.assertEquals(2L, b.value);
    }
}
