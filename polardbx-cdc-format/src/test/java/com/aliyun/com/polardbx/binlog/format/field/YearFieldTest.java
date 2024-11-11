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

public class YearFieldTest {

    private static final int MIN_YEAR = 1901, MAX_YEAR = 2155;

    @Test
    public void testNormal() {
        Field field = MakeFieldFactory.makeField("YEAR", "2022", "utf8", false, false);
        Assert.assertEquals(0, field.doGetTableMeta().length);
        Assert.assertArrayEquals(new byte[] {122}, field.encode());
    }

    @Test
    public void testMin() {
        Field field = MakeFieldFactory.makeField("YEAR", MIN_YEAR, "utf8", false, false);
        Assert.assertEquals(0, field.doGetTableMeta().length);
        Assert.assertArrayEquals(new byte[] {1}, field.encode());
    }

    @Test
    public void testMax() {
        Field field = MakeFieldFactory.makeField("YEAR", MAX_YEAR, "utf8", false, false);
        Assert.assertEquals(0, field.doGetTableMeta().length);
        Assert.assertArrayEquals(new byte[] {-1}, field.encode());
    }
}
