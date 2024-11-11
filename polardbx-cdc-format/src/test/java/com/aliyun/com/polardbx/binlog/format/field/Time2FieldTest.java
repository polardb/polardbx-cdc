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

public class Time2FieldTest {
    @Test
    public void testMin() {
        Field field = MakeFieldFactory.makeField("time", "-838:59:59.000000", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {0}, field.doGetTableMeta());
        Assert.assertArrayEquals(
            new byte[] {75, -111, 5},
            field.encode());
    }

    @Test
    public void testMax() {
        Field field = MakeFieldFactory.makeField("time", "838:59:59.000000", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {0}, field.doGetTableMeta());
        Assert.assertArrayEquals(
            new byte[] {-76, 110, -5},
            field.encode());
    }
}
