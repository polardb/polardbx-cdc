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
import org.junit.Test;

public class DecimalFieldTest {
    @Test
    public void testDec() {
        Field field = MakeFieldFactory.makeField("dec", "11", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {10, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-128, 0, 0, 0, 11}, field.encode());
    }

    @Test
    public void testDec1() {
        Field field = MakeFieldFactory.makeField("dec(1)", "9", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {1, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-119}, field.encode());
    }

    @Test
    public void testDec2_1() {
        Field field = MakeFieldFactory.makeField("dec(2,1)", "9.9", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {2, 1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-119, 9}, field.encode());
    }

    @Test
    public void testDec2_2() {
        Field field = MakeFieldFactory.makeField("dec(2,2)", "0.99", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {2, 2}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-29}, field.encode());
    }

    @Test
    public void testDecimal() {
        Field field = MakeFieldFactory.makeField("decimal", "11", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {10, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-128, 0, 0, 0, 11}, field.encode());
    }

    @Test
    public void testDecimal1() {
        Field field = MakeFieldFactory.makeField("decimal(1)", "9", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {1, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-119}, field.encode());
    }

    @Test
    public void testDecimal2_1() {
        Field field = MakeFieldFactory.makeField("decimal(2,1)", "9.9", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {2, 1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-119, 9}, field.encode());
    }

    @Test
    public void testDecimal2_2() {
        Field field = MakeFieldFactory.makeField("decimal(2,2)", "0.99", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {2, 2}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-29}, field.encode());
    }
}
