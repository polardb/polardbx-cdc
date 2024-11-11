/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.scheduler.model;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ResourceTest extends BaseTest {

    @Test
    public void testGetFreeMemMb() {
        List<Integer> list = new ArrayList<>();
        list.add(1024);
        list.add(2048);
        list.add(4096);
        list.add(8192);
        list.add(16384);

        for (int i = 0; i < list.size(); i++) {
            Resource resource = Resource.builder().cpu(8).memory_mb(list.get(i)).build();
            int size = resource.getFreeMemMb();
            if (i == 0) {
                Assert.assertEquals(size, 614);
            } else if (i == 1) {
                Assert.assertEquals(size, 1433);
            } else if (i == 2) {
                Assert.assertEquals(size, 3276);
            } else if (i == 3) {
                Assert.assertEquals(size, 6963);
            } else if (i == 4) {
                Assert.assertEquals(size, 14745);
            }
        }
    }
}
