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

public class SetFieldTest {
    private static final String defaultCharset = "utf8";

    @Test
    public void testSet1() {
        Field field = MakeFieldFactory.makeField("set('a')", "a", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {-8, 1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {1}, field.encode());
    }

    @Test
    public void testSet2() {
        Field field = MakeFieldFactory.makeField("set('a','b','c','d')", "a", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {-8, 1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {1}, field.encode());
    }

    @Test
    public void testSet3() {
        Field field = MakeFieldFactory.makeField("set('a','b','c','d')", "a,b", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {-8, 1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {3}, field.encode());
    }

    @Test
    public void testSet4() {
        Field field = MakeFieldFactory.makeField("set('a','b','c','d')", "a,b,c,d", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {-8, 1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {15}, field.encode());
    }
}
