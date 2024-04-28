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
