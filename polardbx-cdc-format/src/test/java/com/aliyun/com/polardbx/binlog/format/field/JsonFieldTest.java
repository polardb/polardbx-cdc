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

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.binlog.format.field.Field;
import com.aliyun.polardbx.binlog.format.field.MakeFieldFactory;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;

public class JsonFieldTest {
    private static final String defaultCharset = "utf8";

    @Test
    public void testNull() {
        Field field = MakeFieldFactory.makeField("json", null, defaultCharset, true, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {}, field.encode());
    }

    @Test
    public void test1Obj() {
        Field field = MakeFieldFactory.makeField("json", "1", defaultCharset, true, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {3, 0, 0, 0, 5, 1, 0}, field.encode());
    }

    @Test
    public void testNormal() {
        Field field = MakeFieldFactory.makeField("json", "{\"a\":1,\"b\":[1,2,3],\"c\":\"abc\",\"d\":{\"e\":\"f\"}}",
            defaultCharset, true, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        System.out.println(Arrays.toString(field.encode()));
        Assert.assertArrayEquals(new byte[] {
            68, 0, 0, 0, 0, 4, 0, 67, 0, 32, 0, 1, 0, 33, 0, 1, 0, 34, 0, 1, 0, 35, 0, 1, 0, 5, 1, 0, 2, 36, 0, 12, 49,
            0, 0, 53, 0, 97, 98, 99, 100, 3, 0, 13, 0, 5, 1, 0, 5, 2, 0, 5, 3, 0, 3, 97, 98, 99, 1, 0, 14, 0, 11, 0, 1,
            0, 12, 12, 0, 101, 1, 102}, field.encode());

    }

    @Test
    public void testSimple() {
        Field field = MakeFieldFactory.makeField("json", "{\"a\":1,\"b\":2}",
            defaultCharset, true, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {
            21, 0, 0, 0, 0, 2, 0, 20, 0, 18, 0, 1, 0, 19, 0, 1, 0, 5, 1, 0, 5, 2, 0, 97, 98}, field.encode());

    }

    @Test
    public void testArray() {
        Field field = MakeFieldFactory.makeField("json", "{\"a\":[1,2,3]}",
            defaultCharset, true, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        Assert.assertArrayEquals(new byte[] {
                26, 0, 0, 0, 0, 1, 0, 25, 0, 11, 0, 1, 0, 2, 12, 0, 97, 3, 0, 13, 0, 5, 1, 0, 5, 2, 0, 5, 3, 0},
            field.encode());

    }

    @Test
    public void testSubObj() {
        Field field = MakeFieldFactory.makeField("json", "{\"a\":{\"e\":\"f\"}}",
            defaultCharset, true, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        System.out.println(Arrays.toString(field.encode()));
        Assert.assertArrayEquals(new byte[] {
                27, 0, 0, 0, 0, 1, 0, 26, 0, 11, 0, 1, 0, 0, 12, 0, 97, 1, 0, 14, 0, 11, 0, 1, 0, 12, 12, 0, 101, 1, 102},
            field.encode());

    }

    @Test
    public void testWbj() {
        Field field = MakeFieldFactory.makeField("json", "{\"a\":\"cj\",\"b\":\"test\"}",
            defaultCharset, true, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        System.out.println(Arrays.toString(field.encode()));
        Assert.assertArrayEquals(new byte[] {
                29, 0, 0, 0, 0, 2, 0, 28, 0, 18, 0, 1, 0, 19, 0, 1, 0, 12, 20, 0, 12, 23, 0, 97, 98, 2, 99, 106, 4, 116,
                101, 115, 116},
            field.encode());

    }

    @Test
    public void testWbjArray() {
        Field field = MakeFieldFactory.makeField("json", "{\"a\": [\"a\", \"b\", \"c\"]}",
            defaultCharset, true, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        System.out.println(Arrays.toString(field.encode()));
        Assert.assertArrayEquals(new byte[] {
                32, 0, 0, 0, 0, 1, 0, 31, 0, 11, 0, 1, 0, 2, 12, 0, 97, 3, 0, 19, 0, 12, 13, 0, 12, 15, 0, 12, 17, 0, 1, 97,
                1, 98, 1, 99},
            field.encode());

    }

    @Test
    public void testSimpleBigJson() {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{\"a\":\"");
        for (int i = 0; i < 1000; i++) {
            jsonBuilder.append(RandomStringUtils.randomAlphanumeric(1000));
        }
        jsonBuilder.append("\"}");
        Field field = MakeFieldFactory.makeField("json", jsonBuilder.toString(),
            defaultCharset, true, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        System.out.println(field.encode().length);
    }

    @Test
    public void testComplexBigJson() {
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        int propertyCount = 10000;
        for (int i = 0; i < propertyCount; i++) {
            String name = RandomStringUtils.randomAlphabetic(5) + "_" + i;
            int rate = RandomUtils.nextInt(0, 100);
            Serializable v = null;
            if (rate < 30) {
                v = RandomUtils.nextLong();
            } else if (rate < 60) {
                v = "\"" + RandomStringUtils.randomAlphabetic(200) + "\"";
            } else {
                List<String> arrayList = Lists.newArrayListWithCapacity(10);
                for (int j = 0; j < 10; j++) {
                    arrayList.add(RandomStringUtils.randomAlphabetic(200));
                }
                v = JSON.toJSONString(arrayList);
            }
            jsonBuilder.append("\"").append(name).append("\":").append(v);
            if (i != propertyCount - 1) {
                jsonBuilder.append(",");
            }
        }
        jsonBuilder.append("}");
        Field field = MakeFieldFactory.makeField("json", jsonBuilder.toString(),
            defaultCharset, true, false);
        Assert.assertArrayEquals(new byte[] {4}, field.doGetTableMeta());
        System.out.println(field.encode().length);
    }
}
