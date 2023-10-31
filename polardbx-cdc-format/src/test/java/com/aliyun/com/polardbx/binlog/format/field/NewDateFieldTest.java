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
}
