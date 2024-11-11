/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.extractor.log;

import org.junit.Assert;
import org.junit.Test;

public class VirtualTSOTest {

    @Test
    public void testCompare() {
        VirtualTSO last = new VirtualTSO(6798848743826568064L, 1329913957179199503L, 0);
        VirtualTSO curr = new VirtualTSO(6798848743826568064L, 1329913959330877442L, 4);
        Assert.assertEquals(1, curr.compareTo(last));
    }
}
