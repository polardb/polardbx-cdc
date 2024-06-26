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

public class VarCharFieldTest {

    private static final String gbk = "GBK";

    private static final String utf8 = "UTF8MB4";
    private static final String latin1 = "LATIN1";

    @Test
    public void testVarCharGbk() {
        Field field = MakeFieldFactory.makeField("varchar(32)", "测试汉字gbk", gbk, false, false);
        Assert.assertArrayEquals(new byte[] {64, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {11, -78, -30, -54, -44, -70, -70, -41, -42, 103, 98, 107}, field.encode());
    }

    @Test
    public void testVarCharUtf8mb4() {
        Field field = MakeFieldFactory.makeField("varchar(32)", "测试汉字\uD83D\uDE01", utf8, false, false);
        Assert.assertArrayEquals(new byte[] {-128, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(
            new byte[] {16, -26, -75, -117, -24, -81, -107, -26, -79, -119, -27, -83, -105, -16, -97, -104, -127},
            field.encode());
    }

    @Test
    public void testVarCharLatin() {
        Field field = MakeFieldFactory.makeField("varchar(32)", "this is test", latin1, false, false);
        Assert.assertArrayEquals(new byte[] {32, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {12, 116, 104, 105, 115, 32, 105, 115, 32, 116, 101, 115, 116},
            field.encode());
    }

    @Test
    public void testVarCharNullValue() {
        Field field = MakeFieldFactory.makField4TypeMisMatch("varchar(32)", "null", utf8, false, "null", false);
        Assert.assertArrayEquals(new byte[] {-128, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {4, 110, 117, 108, 108},
            field.encode());
    }

    @Test
    public void testVarCharNull() {
        Field field = MakeFieldFactory.makField4TypeMisMatch("varchar(32)", null, utf8, false, "null", false);
        Assert.assertArrayEquals(new byte[] {-128, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {},
            field.encode());
    }

    @Test
    public void testVarCharNullValue2() {
        Field field = MakeFieldFactory.makeField("varchar(32)", "null", utf8, false, false);
        Assert.assertArrayEquals(new byte[] {-128, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {4, 110, 117, 108, 108},
            field.encode());
    }

    @Test
    public void testVarCharNull2() {
        Field field = MakeFieldFactory.makeField("varchar(32)", null, utf8, false, false);
        Assert.assertArrayEquals(new byte[] {-128, 0}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {},
            field.encode());
    }
}
