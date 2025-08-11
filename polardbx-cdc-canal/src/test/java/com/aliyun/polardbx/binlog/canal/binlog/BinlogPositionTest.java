/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import org.junit.Assert;
import org.junit.Test;

public class BinlogPositionTest {
    @Test
    public void testCompareBinlogPositionString() {
        String p1 =
            "group1_stream_2_binlog.1000000:0003090649#181818.1733259166.rtso(726981585534766291218008810709005148160000000008152494)";
        String p2 =
            "group1_stream_2_binlog.999999:0009278440#181818.1733259144.rtso(726981576311072364818008809786551869440000000000000000)";
        int res = BinlogPosition.comparePositionString(p1, p2);
        Assert.assertEquals(1, res);

        p1 = null;
        res = BinlogPosition.comparePositionString(p1, p2);
        Assert.assertEquals(-1, res);

        p2 = null;
        p1 =
            "group1_stream_2_binlog.1000000:0003090649#181818.1733259166.rtso(726981585534766291218008810709005148160000000008152494)";
        res = BinlogPosition.comparePositionString(p1, p2);
        Assert.assertEquals(1, res);

        p2 =
            "group1_stream_2_binlog.999999:0009278440#181818.1733259144.rtso()";
        p1 =
            "group1_stream_2_binlog.1000000:0003090649#181818.1733259166.rtso()";
        res = BinlogPosition.comparePositionString(p1, p2);
        Assert.assertEquals(1, res);
    }
}
