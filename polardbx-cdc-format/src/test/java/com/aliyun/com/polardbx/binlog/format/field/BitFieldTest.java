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

public class BitFieldTest {

    private static final String defaultCharset = "utf8";

    @Test
    public void testBit11() {
        Field field = MakeFieldFactory.makeField("bit(1)", "0x01", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {1, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {1}, field.encode());
    }

    @Test
    public void testBit1False() {
        Field field = MakeFieldFactory.makeField("bit(1)", "false", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {1, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {0}, field.encode());
    }

    @Test
    public void testBit2True() {
        Field field = MakeFieldFactory.makeField("bit(1)", "true", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {1, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {1}, field.encode());
    }

    @Test
    public void testBit10() {
        Field field = MakeFieldFactory.makeField("bit(1)", "0x00", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {1, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {0}, field.encode());
    }

    @Test
    public void testBit32() {
        Field field = MakeFieldFactory.makeField("bit(32)", "0x00000007", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {0, 4}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {0, 0, 0, 7}, field.encode());
    }

    @Test
    public void testBit64() {
        Field field = MakeFieldFactory.makeField("bit(64)", "0x0000000000000020", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {0, 8}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 0, 0, 32}, field.encode());
    }
}
