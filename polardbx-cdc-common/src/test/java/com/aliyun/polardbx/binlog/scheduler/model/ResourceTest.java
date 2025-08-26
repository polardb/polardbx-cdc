/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler.model;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static com.aliyun.polardbx.binlog.ConfigKeys.TOPOLOGY_RESOURCE_ADAPTIVE_USE_RATIO_ENABLED;

public class ResourceTest extends BaseTest {

    @Test
    public void testGetFreeMemMb() {
        List<Integer> list = new ArrayList<>();
        list.add(1024);
        list.add(2048);
        list.add(4096);
        list.add(8192);
        list.add(16384);
        list.add(32768);
        list.add(65536);

        for (int i = 0; i < list.size(); i++) {
            Resource resource = Resource.builder().cpu(8).memory_mb(list.get(i)).build();
            int size = resource.getFreeMemMb();
            if (i == 0) {
                Assert.assertEquals(614, size);
            } else if (i == 1) {
                Assert.assertEquals(1331, size);
            } else if (i == 2) {
                Assert.assertEquals(2867, size);
            } else if (i == 3) {
                Assert.assertEquals(6144, size);
            } else if (i == 4) {
                Assert.assertEquals(13107, size);
            } else if (i == 5) {
                Assert.assertEquals(27852, size);
            } else if (i == 6) {
                Assert.assertEquals(58982, size);
            }
        }

        mockConfig(TOPOLOGY_RESOURCE_ADAPTIVE_USE_RATIO_ENABLED, "false");
        for (int i = 0; i < list.size(); i++) {
            Resource resource = Resource.builder().cpu(8).memory_mb(list.get(i)).build();
            int size = resource.getFreeMemMb();
            if (i == 0) {
                Assert.assertEquals(819, size);
            } else if (i == 1) {
                Assert.assertEquals(1638, size);
            } else if (i == 2) {
                Assert.assertEquals(3276, size);
            } else if (i == 3) {
                Assert.assertEquals(6553, size);
            } else if (i == 4) {
                Assert.assertEquals(13107, size);
            } else if (i == 5) {
                Assert.assertEquals(26214, size);
            } else if (i == 6) {
                Assert.assertEquals(52428, size);
            }
        }
    }
}
