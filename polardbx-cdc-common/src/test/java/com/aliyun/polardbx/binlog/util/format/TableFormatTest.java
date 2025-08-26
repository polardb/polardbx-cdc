/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util.format;

import org.junit.Assert;
import org.junit.Test;

public class TableFormatTest {

    @Test
    public void testPrint() {
        TableFormat tf = new TableFormat("test");
        tf.addColumn("tid", "rt", "event status", "pos", "delay");
        tf.addRow(26, 21, "complete", "master-bin.000067:262316#1610679074", 24958);
        tf.addRow(24, 0, "complete", "master-bin.000056:223844#1610679196", 24836);
        String expected = "\ntest:\n"
            + "-------------------------------------------------------------------------\n"
            + "| tid | rt | event status | pos                                 | delay |\n"
            + "-------------------------------------------------------------------------\n"
            + "| 26  | 21 | complete     | master-bin.000067:262316#1610679074 | 24958 |\n"
            + "| 24  | 0  | complete     | master-bin.000056:223844#1610679196 | 24836 |\n"
            + "-------------------------------------------------------------------------\n";
        Assert.assertEquals(expected, tf.print());
    }
}
