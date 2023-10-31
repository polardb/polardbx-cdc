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

public class EnumFieldTest {

    private static final String defaultCharset = "utf8";

    @Test
    public void test0() {
        Field field = MakeFieldFactory.makeField("enum('a')", " ", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {-9, 1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {0}, field.encode());
    }

    @Test
    public void test1() {
        Field field = MakeFieldFactory.makeField("enum('a')", "a", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {-9, 1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {1}, field.encode());
    }

    @Test
    public void test2() {
        Field field = MakeFieldFactory.makeField("enum('a','b')", "b", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {-9, 1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {2}, field.encode());
    }

    @Test
    public void test3() {
        Field field =
            MakeFieldFactory.makeField("enum('1','2','3')", "3", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {-9, 1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {3}, field.encode());
    }

    @Test
    public void test31() {
        Field field =
            MakeFieldFactory.makeField("enum('a','b','c')", "3", defaultCharset, false, false);
        Assert.assertArrayEquals(new byte[] {-9, 1}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {3}, field.encode());
    }

}
