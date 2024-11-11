/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

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
        String s3 = CharsetConversion.getJavaCharset("");
        System.out.println(s1);
        System.out.println(s2);
        System.out.println(s3);
        Assert.assertEquals(s1, "UTF-8");
        Assert.assertEquals(s2, "UTF-8");
    }
}
