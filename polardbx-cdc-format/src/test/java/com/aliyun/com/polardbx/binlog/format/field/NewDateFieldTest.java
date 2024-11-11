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

public class NewDateFieldTest {
    private static final String defaultCharset = "utf8";

    @Test
    public void testMax() {
        Field field = MakeFieldFactory.makeField("date", "9999-12-31", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-97, 31, 78}, field.encode());

    }

    @Test
    public void testMin() {
        Field field = MakeFieldFactory.makeField("date", "1000-01-01", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {33, -48, 7}, field.encode());
    }

    @Test
    public void testNormal() {
        Field field = MakeFieldFactory.makeField("date", "2023-08-24", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {24, -49, 15}, field.encode());
    }

    @Test
    public void testZero() {
        Field field = MakeFieldFactory.makeField("date", "0000-00-00", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {0, 0, 0}, field.encode());
    }
}
