/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import com.aliyun.polardbx.binlog.testing.TestConfig;
import org.junit.Assert;
import org.junit.Test;

public class TestTestConfig {

    @Test
    public void testGetConfig() {
        String value = TestConfig.getConfig(TestTestConfig.class, "sample");
        Assert.assertEquals(value, "sample");
    }
}
