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

public class FieldFormatErrorTest {

    private final String utf8 = "utf8";

    @Test
    public void testIntReformatErrorNull() {
        Field field = MakeFieldFactory.makField4TypeMisMatch("int(32)", "abc", utf8, false, null, false);
        Assert.assertArrayEquals(new byte[] {}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {},
            field.encode());
    }

    @Test
    public void testIntReformatErrorDefaultValue() {
        Field field = MakeFieldFactory.makField4TypeMisMatch("int(32)", "abc", utf8, false, "123", false);
        Assert.assertArrayEquals(new byte[] {}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {123, 0, 0, 0},
            field.encode());
    }

}
