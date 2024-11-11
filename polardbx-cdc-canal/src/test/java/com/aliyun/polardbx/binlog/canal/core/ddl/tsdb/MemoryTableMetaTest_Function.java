/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl.tsdb;

import org.junit.Test;

public class MemoryTableMetaTest_Function extends MemoryTableMetaBase {

    @Test
    public void testCreateFunction() {
        MemoryTableMeta memoryTableMeta = newMemoryTableMeta();
        String sql = "create function fac(n int unsigned) returns bigint unsigned\n"
            + "begin\n"
            + "declare f bigint unsigned default 1;\n"
            + "\n"
            + "while n > 1 do\n"
            + "set f = f * n;\n"
            + "set n = n - 1;\n"
            + "end while;\n"
            + "return f;\n"
            + "end";
        memoryTableMeta.apply(null, "d1", sql, null);
    }
}
