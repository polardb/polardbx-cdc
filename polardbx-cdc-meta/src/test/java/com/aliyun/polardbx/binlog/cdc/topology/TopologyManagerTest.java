/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.cdc.topology;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.cdc.topology.vo.TopologyRecord;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.cdc.topology.LowerCaseUtil.toLowerCaseLogicMetaTopology;

public class TopologyManagerTest {

    @Before
    public void before() {
        SpringContextBootStrap appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @Test
    public void applyAndCheck() {
        Gson gson = new GsonBuilder().create();
        final LogicMetaTopology x = gson.fromJson(MockData.BASE, LogicMetaTopology.class);
        toLowerCaseLogicMetaTopology(x);

        TopologyManager manager = new TopologyManager(x);

        List<LogicMetaTopology.PhyTableTopology> phyTables =
            manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet());
        long count = phyTables.stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList()).stream()
            .filter(i -> i.equals("user_Gvli")).count();
        Assert.assertEquals(16, phyTables.size());
        Assert.assertEquals(4, count);

        phyTables =
            manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet("transfer_test.user"));
        count = phyTables.stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList()).stream()
            .filter(i -> i.equals("user_Gvli")).count();
        Assert.assertEquals(12, phyTables.size());
        Assert.assertEquals(0, count);

        phyTables =
            manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(),
                Sets.newHashSet("transfer_test.accounts"));
        count = phyTables.stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList()).stream()
            .filter(i -> i.equals("user_Gvli")).count();
        Assert.assertEquals(12, phyTables.size());
        Assert.assertEquals(4, count);

        phyTables =
            manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(),
                Sets.newHashSet("transfer_test.accountsxxx"));
        count = phyTables.stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList()).stream()
            .filter(i -> i.equals("user_Gvli")).count();
        Assert.assertEquals(16, phyTables.size());
        Assert.assertEquals(4, count);

        phyTables =
            manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet("transfer_test"),
                Sets.newHashSet("transfer_test.accountsxxx"));
        count = phyTables.stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toList()).stream()
            .filter(i -> i.equals("user_Gvli")).count();
        Assert.assertEquals(8, phyTables.size());
        Assert.assertEquals(0, count);

        TopologyRecord r1 = gson.fromJson(MockData.CREATE_D1, TopologyRecord.class);
        TopologyRecord r2 = gson.fromJson(MockData.CREATE_D1_T1, TopologyRecord.class);
        TopologyRecord r3 = gson.fromJson(MockData.MOVE_D1_0001_0, TopologyRecord.class);
        TopologyRecord r4 = gson.fromJson(MockData.CREATE_D1_T2, TopologyRecord.class);
        TopologyRecord r5 = gson.fromJson(MockData.MOVE_D1_0001_1, TopologyRecord.class);

        manager.apply(null, "d1", null, r1);//create db
        Assert.assertEquals("d1", manager.getLogicSchema("d1_000001"));
        manager.apply(null, "d1", "t1", r2);//create table d1.t1
        LogicBasicInfo schema = manager.getLogicBasicInfo("d1_000001", "t1_TRwG_02");
        manager.getLogicBasicInfo("d1_000001", "t1_TRwG_02");
        Assert.assertEquals("d1", schema.getSchemaName());
        Assert.assertEquals("t1", schema.getTableName());

        List<String> tables =
            manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet()).stream()
                .flatMap(p -> p.getPhyTables().stream()).collect(
                Collectors.toList());
        Assert.assertThat(tables, CoreMatchers.hasItems("t1_TRwG_02", "t1_TRwG_03"));

        manager.apply(null, "d1", null, r3);//move group
        tables = manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet()).stream()
            .flatMap(p -> p.getPhyTables().stream()).collect(
                Collectors.toList());
        Assert.assertThat(tables, CoreMatchers.not(CoreMatchers.hasItems("t1_TRwG_02", "t1_TRwG_03")));

        manager.apply(null, "d1", "t2", r4);//create table d1.t2
        schema = manager.getLogicBasicInfo("d1_000001", "t2_N6ql_02");
        Assert.assertEquals("d1", schema.getSchemaName());
        Assert.assertEquals("t2", schema.getTableName());

        tables = manager.getPhyTables("polardbx-storage-0-master", Sets.newHashSet(), Sets.newHashSet()).stream()
            .flatMap(p -> p.getPhyTables().stream()).collect(
                Collectors.toList());
        Assert.assertThat(tables, CoreMatchers.hasItems("t1_TRwG_02", "t1_TRwG_03", "t2_N6ql_02", "t2_N6ql_03"));

        manager.apply(null, "d1", null, r5);//move group back
        tables = manager.getPhyTables("polardbx-storage-0-master", Sets.newHashSet(), Sets.newHashSet()).stream()
            .flatMap(p -> p.getPhyTables().stream()).collect(
                Collectors.toList());
        Assert.assertThat(tables,
            CoreMatchers.not(CoreMatchers.hasItems("t1_TRwG_02", "t1_TRwG_03", "t2_N6ql_02", "t2_N6ql_03")));

        tables = manager.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet()).stream()
            .flatMap(p -> p.getPhyTables().stream()).collect(
                Collectors.toList());
        Assert.assertThat(tables, CoreMatchers.hasItems("t1_TRwG_02", "t1_TRwG_03", "t2_N6ql_02", "t2_N6ql_03"));

    }
}