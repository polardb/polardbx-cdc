/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
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
