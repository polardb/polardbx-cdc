/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog;

import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;

import static com.aliyun.polardbx.binlog.ConfigKeys.TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED;
import static com.aliyun.polardbx.binlog.DynamicApplicationConfig.getValue;
import static com.aliyun.polardbx.binlog.extractor.filter.rebuild.DDLConverter.buildDdlEventSql;
import static org.mockito.Mockito.when;

public class Test extends BaseTest {

    @org.junit.Test
    public void test1() {
        when(getValue(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED)).thenReturn("true");
        String sql1 =
            "ALTER TABLE t_order ADD UNIQUE GLOBAL INDEX `g_i_buyer` (`buyer_id`) COVERING (`order_snapshot`) PARTITION BY KEY (`buyer_id`) PARTITIONS 4";
        String sql2 = buildDdlEventSql("", sql1, null, "", "",
            "ALTER TABLE t_order ADD UNIQUE GLOBAL INDEX `g_i_buyer` (`buyer_id`) COVERING (`order_snapshot`) PARTITION BY KEY (`buyer_id`) PARTITIONS 4");
        Assert.assertTrue(sql2.contains("# POLARX_ORIGIN_SQL="));
        Assert.assertTrue(sql2.contains("# POLARX_TSO="));

        when((getValue(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED))).thenReturn("false");
        String sql3 = "alter table nnn change column b bb bigint ALGORITHM=XXX";
        String sql4 = buildDdlEventSql(sql3, null, null, "");
        Assert.assertFalse(sql4.contains("# POLARX_ORIGIN_SQL="));
        Assert.assertFalse(sql4.contains("# POLARX_TSO="));
    }

    @org.junit.Test
    public void test2() {
        String s = DynamicApplicationConfig.getString(TASK_REFORMAT_ATTACH_PRIVATE_DDL_ENABLED);
        System.out.println(s);
    }
}
