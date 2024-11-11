/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.common;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class CommonUtilTest {

    @Test
    public void testHostSafeCheck1() {
        CommonUtil.hostSafeCheck("acbdabdasdhkj.com");
    }

    @Test
    public void testHostSafeCheck2() {
        boolean exception = false;
        try {
            CommonUtil.hostSafeCheck("acbdabdasdhkj.com==");
        } catch (Exception e) {
            exception = true;
        }
        assertTrue(exception);
    }
}
