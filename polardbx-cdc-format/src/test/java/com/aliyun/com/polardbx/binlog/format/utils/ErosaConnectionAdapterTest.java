/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.com.polardbx.binlog.format.utils;

import com.aliyun.polardbx.binlog.format.utils.ErosaConnectionAdapter;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

public class ErosaConnectionAdapterTest {
    @Test
    public void binlogListTest() {
        ErosaConnectionAdapter adapter = new ErosaConnectionAdapter();
        Assert.assertEquals(Collections.emptyList(), adapter.binlogList());
    }
}
