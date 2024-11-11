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

public class StringFieldTest {

    private static final String gbk = "GBK";

    private static final String utf8 = "UTF8MB4";
    private static final String latin1 = "LATIN1";

    @Test
    public void testCharGbk() {
        Field field = MakeFieldFactory.makeField("char(32)", "测试汉字gbk", gbk, false, false);
        Assert.assertArrayEquals(new byte[] {-2, 64}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {11, -78, -30, -54, -44, -70, -70, -41, -42, 103, 98, 107}, field.encode());
    }

    @Test
    public void testCharUtf8mb4() {
        Field field = MakeFieldFactory.makeField("char(32)", "测试汉字\uD83D\uDE01", utf8, false, false);
        Assert.assertArrayEquals(new byte[] {-2, -128}, field.doGetTableMeta());
        Assert.assertArrayEquals(
            new byte[] {16, -26, -75, -117, -24, -81, -107, -26, -79, -119, -27, -83, -105, -16, -97, -104, -127},
            field.encode());
    }

    @Test
    public void testCharLatin() {
        Field field = MakeFieldFactory.makeField("char(32)", "this is test", latin1, false, false);
        Assert.assertArrayEquals(new byte[] {-2, 32}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {12, 116, 104, 105, 115, 32, 105, 115, 32, 116, 101, 115, 116},
            field.encode());
    }

    @Test
    public void testCharNullValue() {
        Field field = MakeFieldFactory.makField4TypeMisMatch("char(32)", "null", utf8, false, "null", false);
        Assert.assertArrayEquals(new byte[] {-2, -128}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {4, 110, 117, 108, 108},
            field.encode());
    }

    @Test
    public void testCharNull() {
        Field field = MakeFieldFactory.makField4TypeMisMatch("char(32)", null, utf8, false, "null", false);
        Assert.assertArrayEquals(new byte[] {-2, -128}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {},
            field.encode());
    }
}
