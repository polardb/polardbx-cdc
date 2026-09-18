/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.jvm;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

/**
 * Test for G1JvmUtils
 */
public class JvmUtilsTest extends BaseTest {

    @Test
    public void testBuildJvmSnapshot() {
        // Test that we can build a JVM snapshot without throwing exceptions
        JvmSnapshot snapshot = JvmUtils.buildJvmSnapshot();
        Assert.assertNotNull(snapshot);

        // Verify that the snapshot contains reasonable values
        Assert.assertTrue(snapshot.getStartTime() > 0);
        Assert.assertTrue(snapshot.getCurrentThreadCount() >= 0);
    }

    @Test
    public void testGetOldUsedRatio() {
        // Test that we can get the old generation usage ratio without throwing exceptions
        double ratio = JvmUtils.getOldUsedRatio();
        Assert.assertTrue(ratio >= 0.0 && ratio <= 1.0);
    }

    @Test
    public void testGetTotalUsedRatio() {
        // Test that we can get the total heap usage ratio without throwing exceptions
        double ratio = JvmUtils.getTotalUsedRatio();
        Assert.assertTrue(ratio >= 0.0 && ratio <= 1.0);
    }
}