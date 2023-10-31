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
package com.aliyun.polardbx.binlog.experiment;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Test;

/**
 * created by ziyang.lb
 **/
public class ImmutablePairTest {

    @Test
    public void testEquals1() {
        String key = "abc";
        String value = "xyz";
        Pair<String, String> p1 = Pair.of(key, value);
        Pair<String, String> p2 = Pair.of(key, value);
        Assert.assertEquals(p1, p2);
    }

    @Test
    public void testEquals2() {
        Pair<String, String> p1 = Pair.of("abc", "xyz");
        Pair<String, String> p2 = Pair.of(new String("abc"), new String("xyz"));
        Assert.assertEquals(p1, p2);
    }
}
