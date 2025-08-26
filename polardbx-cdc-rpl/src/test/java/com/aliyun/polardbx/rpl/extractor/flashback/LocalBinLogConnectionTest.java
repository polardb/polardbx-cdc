/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.extractor.flashback;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class LocalBinLogConnectionTest {
    @Test
    public void testBinlogList() {
        List<String> binlogList = new ArrayList<>();
        LocalBinLogConnection localBinLogConnection = new LocalBinLogConnection("test", binlogList, false, null, 123);
        Assert.assertEquals(binlogList, localBinLogConnection.binlogList());
    }
}
