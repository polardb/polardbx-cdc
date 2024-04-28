/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.com.polardbx.binlog.format.field;

import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import org.junit.Assert;
import org.junit.Test;

public class DateTime2FieldTest {

    private static final String defaultCharset = "utf8";

    @Test
    public void test0() {
        Field field = MakeFieldFactory.makeField("datetime", "2023-08-24 07:54:55", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-103, -80, -16, 125, -73}, field.encode());
    }

    @Test
    public void test3() {
        Field field = MakeFieldFactory.makeField("datetime(3)", "2023-08-24 07:54:55", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {3}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-103, -80, -16, 125, -73, 0, 0}, field.encode());
    }

    @Test
    public void test03() {
        Field field = MakeFieldFactory.makeField("datetime(3)", "0000-00-00 00:00:00", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {3}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-128, 0, 0, 0, 0, 0, 0}, field.encode());
    }

    @Test
    public void test03m() {
        Field field =
            MakeFieldFactory.makeField("datetime(3)", "2023-08-24 07:54:55.456", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {3}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-103, -80, -16, 125, -73, 17, -48}, field.encode());
    }

    @Test
    public void testZero() {
        Field field =
            MakeFieldFactory.makeField("datetime", "0000-00-00 00:00:0", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {-128, 0, 0, 0, 0}, field.encode());
    }
}
