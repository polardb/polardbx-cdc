/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog.dbms;

import org.junit.Assert;
import org.junit.Test;

public class DefaultRowChangeTest {
    @Test
    public void testTrace(){
        DefaultRowChange rowChange = new DefaultRowChange();
        String trace = "drds-1234567";
        rowChange.setTraceInfo(trace);
        Assert.assertEquals(trace, rowChange.getTraceInfo());
    }
}
