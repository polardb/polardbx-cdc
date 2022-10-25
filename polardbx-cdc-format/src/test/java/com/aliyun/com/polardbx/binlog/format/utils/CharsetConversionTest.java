/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.com.polardbx.binlog.format.utils;

import com.aliyun.polardbx.binlog.canal.binlog.CharsetConversion;
import org.junit.Assert;
import org.junit.Test;

/**
 * created by ziyang.lb
 **/
public class CharsetConversionTest {

    @Test
    public void testGetJavaCharset() {
        String s1 = CharsetConversion.getJavaCharset("utf8");
        String s2 = CharsetConversion.getJavaCharset("utf8mb4");
        System.out.println(s1);
        System.out.println(s2);
        Assert.assertEquals(s1, "UTF-8");
        Assert.assertEquals(s2, "UTF-8");
    }
}
