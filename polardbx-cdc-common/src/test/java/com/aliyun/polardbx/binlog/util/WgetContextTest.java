/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util;

import org.junit.Assert;
import org.junit.Test;

public class WgetContextTest {
    @Test
    public void testLog() {
        WgetContext.add("196700K .......... .......... .......... .......... .......... 38% 6.50M 48s");
        Assert.assertTrue(WgetContext.totalSpeed() > 0);
        WgetContext.finish();
    }
}
