/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util.format;

import org.junit.Assert;
import org.junit.Test;

public class ColumnTest {
    @Test
    public void testColumn() {
        Column column = new Column("test");
        column.setColumnLen(10);
        column.setIndex(1);
        Assert.assertEquals("test", column.getTitle());
        Assert.assertEquals(10, column.getColumnLen());
        Assert.assertEquals(1, column.getIndex());
    }
}
