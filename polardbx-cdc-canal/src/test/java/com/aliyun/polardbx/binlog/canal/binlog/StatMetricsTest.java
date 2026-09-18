/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSEvent;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultQueryLog;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.unit.StatMetrics;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.junit.Assert;
import org.junit.Test;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class StatMetricsTest extends BaseTest {

    @Test
    public void testDdlWithLab() {
        mockConfig(ConfigKeys.IS_LAB_ENV, "true");
        StatMetrics metrics = new StatMetrics();
        List<DBMSEvent> eventList = new ArrayList<>();
        eventList.add(new DefaultQueryLog("drds_polarx1_qatest_app",
            "# POLARX_ORIGIN_SQL=DROP TABLE IF EXISTS alter_partition_ddl_primary_table_multi_pk\n"
                + "# POLARX_TSO=733300161482483308818640668303651020800000000000000000\n"
                + "# POLARX_DDL_ID=0\n"
                + "DROP TABLE IF EXISTS alter_partition_ddl_primary_table_multi_pk",
            new Timestamp(System.currentTimeMillis()), 1, 1));
        eventList.add(
            new DefaultQueryLog("polardbx", "CALL trigger_sync_point_trx(0)", new Timestamp(System.currentTimeMillis()),
                1, 1));
        eventList.add(new DefaultRowChange());
        metrics.addCommitCount(eventList);
        Assert.assertEquals(2, metrics.getPeriodCommitCount().get());
    }

    @Test
    public void testDdlWithLabFalse() {
        mockConfig(ConfigKeys.IS_LAB_ENV, "false");
        StatMetrics metrics = new StatMetrics();
        List<DBMSEvent> eventList = new ArrayList<>();
        eventList.add(new DefaultQueryLog("drds_polarx1_qatest_app",
            "# POLARX_ORIGIN_SQL=DROP TABLE IF EXISTS alter_partition_ddl_primary_table_multi_pk\n"
                + "# POLARX_TSO=733300161482483308818640668303651020800000000000000000\n"
                + "# POLARX_DDL_ID=0\n"
                + "DROP TABLE IF EXISTS alter_partition_ddl_primary_table_multi_pk",
            new Timestamp(System.currentTimeMillis()), 1, 1));
        eventList.add(
            new DefaultQueryLog("polardbx", "CALL trigger_sync_point_trx(0)", new Timestamp(System.currentTimeMillis()),
                1, 1));
        eventList.add(new DefaultRowChange());
        metrics.addCommitCount(eventList);
        Assert.assertEquals(3, metrics.getPeriodCommitCount().get());
    }

    /**
     * 测试心跳计数功能：addHeartbeatCount 累加计数，getHeartbeatCount().getAndSet(0) 取值后清零
     * 此测试不依赖 Spring 上下文，直接操作 StatMetrics 实例
     */
    @Test
    public void testHeartbeatCount() {
        mockConfig(ConfigKeys.IS_LAB_ENV, "false");
        StatMetrics metrics = new StatMetrics();

        // 初始为0
        Assert.assertEquals(0, metrics.getHeartbeatCount().get());

        // 累加心跳计数
        metrics.addHeartbeatCount(1);
        Assert.assertEquals(1, metrics.getHeartbeatCount().get());

        metrics.addHeartbeatCount(5);
        Assert.assertEquals(6, metrics.getHeartbeatCount().get());

        // getAndSet(0) 取值并清零（模拟 StatisticalProxy 中的使用方式）
        long count = metrics.getHeartbeatCount().getAndSet(0);
        Assert.assertEquals(6, count);
        Assert.assertEquals(0, metrics.getHeartbeatCount().get());
    }

    /**
     * 测试 DefaultColumn.setGenerated 方法（commit 167deb9e 新增）
     */
    @Test
    public void testDefaultColumnSetGenerated() {
        mockConfig(ConfigKeys.IS_LAB_ENV, "false");
        com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn column =
            new com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn("col1", 0, java.sql.Types.VARCHAR);

        // 默认不是生成列
        Assert.assertFalse(column.isGenerated());

        // 设置为生成列
        column.setGenerated(true);
        Assert.assertTrue(column.isGenerated());

        // 恢复为非生成列
        column.setGenerated(false);
        Assert.assertFalse(column.isGenerated());
    }

}
