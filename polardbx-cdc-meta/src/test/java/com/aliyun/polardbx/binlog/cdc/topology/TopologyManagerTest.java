/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.topology;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.google.common.collect.Sets;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SHARE_TOPOLOGY_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_ENABLED;
import static com.aliyun.polardbx.binlog.cdc.topology.LowerCaseUtil.toLowerCaseLogicMetaTopology;

public class TopologyManagerTest extends BaseTestWithGmsTables {
    @Before
    public void before() {
        setConfig(META_PERSIST_ENABLED, "OFF");
        setConfig(META_BUILD_SHARE_TOPOLOGY_ENABLED, "OFF");
    }

    @Test
    public void applyAndCheck() {
        final LogicMetaTopology x = JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class);
        toLowerCaseLogicMetaTopology(x);

        TopologyManager manager = new TopologyManager(x);

        List<LogicMetaTopology.PhyTableTopology> phyTables = manager.getPhyTables(
            "polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet());
        long count = phyTables.stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList()).stream()
            .filter(i -> i.equals("user_Gvli")).count();
        Assert.assertEquals(16, phyTables.size());
        Assert.assertEquals(4, count);

        phyTables = manager.getPhyTables(
            "polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet("transfer_test.user"));
        count = phyTables.stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList()).stream()
            .filter(i -> i.equals("user_Gvli")).count();
        Assert.assertEquals(12, phyTables.size());
        Assert.assertEquals(0, count);

        phyTables = manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(),
            Sets.newHashSet("transfer_test.accounts"));
        count = phyTables.stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList()).stream()
            .filter(i -> i.equals("user_Gvli")).count();
        Assert.assertEquals(12, phyTables.size());
        Assert.assertEquals(4, count);

        phyTables = manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(),
            Sets.newHashSet("transfer_test.accountsxxx"));
        count = phyTables.stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList()).stream()
            .filter(i -> i.equals("user_Gvli")).count();
        Assert.assertEquals(16, phyTables.size());
        Assert.assertEquals(4, count);

        phyTables = manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet("transfer_test"),
            Sets.newHashSet("transfer_test.accountsxxx"));
        count = phyTables.stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList()).stream()
            .filter(i -> i.equals("user_Gvli")).count();
        Assert.assertEquals(8, phyTables.size());
        Assert.assertEquals(0, count);

        TopologyRecord r1 = JSONObject.parseObject(MockData.CREATE_D1, TopologyRecord.class);
        TopologyRecord r2 = JSONObject.parseObject(MockData.CREATE_D1_T1, TopologyRecord.class);
        TopologyRecord r3 = JSONObject.parseObject(MockData.MOVE_D1_0001_0, TopologyRecord.class);
        TopologyRecord r4 = JSONObject.parseObject(MockData.CREATE_D1_T2, TopologyRecord.class);
        TopologyRecord r5 = JSONObject.parseObject(MockData.MOVE_D1_0001_1, TopologyRecord.class);

        manager.apply(null, "d1", null, r1);//create db
        Assert.assertEquals("d1", manager.getLogicSchema("d1_000001"));
        manager.apply(null, "d1", "t1", r2);//create table d1.t1
        LogicBasicInfo schema = manager.getLogicBasicInfo("d1_000001", "t1_TRwG_02");
        manager.getLogicBasicInfo("d1_000001", "t1_TRwG_02");
        Assert.assertEquals("d1", schema.getSchemaName());
        Assert.assertEquals("t1", schema.getTableName());

        List<String> tables =
            manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet()).stream()
                .flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList());
        Assert.assertThat(tables, CoreMatchers.hasItems("t1_TRwG_02", "t1_TRwG_03"));

        manager.apply(null, "d1", null, r3);//move group
        tables = manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet())
            .stream()
            .flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList());
        Assert.assertThat(tables, CoreMatchers.not(CoreMatchers.hasItems("t1_TRwG_02", "t1_TRwG_03")));

        manager.apply(null, "d1", "t2", r4);//create table d1.t2
        schema = manager.getLogicBasicInfo("d1_000001", "t2_N6ql_02");
        Assert.assertEquals("d1", schema.getSchemaName());
        Assert.assertEquals("t2", schema.getTableName());

        tables = manager.getPhyTables("polardbx-storage-0-master", Sets.newHashSet(), Sets.newHashSet()).stream()
            .flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList());
        Assert.assertThat(tables, CoreMatchers.hasItems("t1_TRwG_02", "t1_TRwG_03", "t2_N6ql_02", "t2_N6ql_03"));

        manager.apply(null, "d1", null, r5);//move group back
        tables = manager.getPhyTables("polardbx-storage-0-master", Sets.newHashSet(), Sets.newHashSet()).stream()
            .flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList());
        Assert.assertThat(tables,
            CoreMatchers.not(CoreMatchers.hasItems("t1_TRwG_02", "t1_TRwG_03", "t2_N6ql_02", "t2_N6ql_03")));

        tables = manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet()).stream()
            .flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList());
        Assert.assertThat(tables, CoreMatchers.hasItems("t1_TRwG_02", "t1_TRwG_03", "t2_N6ql_02", "t2_N6ql_03"));
    }
}
