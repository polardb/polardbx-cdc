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
