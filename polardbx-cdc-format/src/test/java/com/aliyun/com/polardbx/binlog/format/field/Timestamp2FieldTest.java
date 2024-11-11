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

import java.util.TimeZone;

public class Timestamp2FieldTest {
    @Test
    public void testMin() {
        TimeZone.setDefault(TimeZone.getTimeZone("CTT"));
        Field field = MakeFieldFactory.makeField("timestamp", "1970-01-01 08:00:01", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {0}, field.doGetTableMeta());
        Assert.assertArrayEquals(
            new byte[] {0, 0, 0, 1},
            field.encode());
    }

    @Test
    public void testMax() {
        TimeZone.setDefault(TimeZone.getTimeZone("CTT"));
        Field field = MakeFieldFactory.makeField("timestamp", "2038-01-19 11:14:07", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {0}, field.doGetTableMeta());
        Assert.assertArrayEquals(
            new byte[] {127, -1, -1, -1},
            field.encode());
    }

    @Test
    public void testZero() {
        TimeZone.setDefault(TimeZone.getTimeZone("CTT"));
        Field field = MakeFieldFactory.makeField("timestamp", "0000-00-00 00:00:00.000", "utf8", false, false);
        Assert.assertArrayEquals(new byte[] {0}, field.doGetTableMeta());
        Assert.assertArrayEquals(
            new byte[] {0, 0, 0, 0},
            field.encode());
    }

}
