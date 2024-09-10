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
package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.MockData;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SHARE_TOPOLOGY_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_ENABLED;
import static com.aliyun.polardbx.binlog.cdc.topology.TopologyShareUtil.buildTopology;

@Slf4j
public class PolarDbXTableMetaManagerTest_Basic extends BaseTestWithGmsTables {

    private static final String STORAGE_INST_ID = "polardbx-storage-0-master";
    private final Supplier<Boolean> hiddenPkSupplier = () -> false;
    private final Supplier<String> dnVersionSupplier = () -> "5.7";
    private PolarDbXTableMetaManager metaManager;

    @Before
    public void before() {
        setConfig(META_PERSIST_ENABLED, "OFF");
        setConfig(META_BUILD_SHARE_TOPOLOGY_ENABLED, "OFF");
        buildMetaManager();
    }

    @Test
    public void testApply() {
        LogicMetaTopology x = buildTopology("000",
            () -> JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class));

        PolarDbXTableMetaManager metaManager1 = new PolarDbXTableMetaManager("polardbx-storage-0-master",
            hiddenPkSupplier, dnVersionSupplier);
        metaManager1.init();
        metaManager1.applyBase(new BinlogPosition(null, "1"), x, "000");
        Set<String> set1 = metaManager1.getPhyTables("polardbx-storage-0-master", Sets.newHashSet(), Sets.newHashSet())
            .stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toSet());
        Assert.assertEquals(
            Sets.newHashSet("ddl_test_11", "ddl_test_22", "ddl_test_33", "__drds_heartbeat___GOBU", "accounts_ap0Y",
                "ddl_test", "__drds_heartbeat_single__", "user_Gvli", "brd_tbl", "accounts_SuV2"), new HashSet<>(set1));

        PolarDbXTableMetaManager metaManager2 =
            new PolarDbXTableMetaManager("polardbx-storage-1-master", hiddenPkSupplier, dnVersionSupplier);
        metaManager2.init();
        metaManager2.applyBase(new BinlogPosition(null, "2"), x, "000");
        Set<String> set2 = metaManager2.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet())
            .stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toSet());
        Assert.assertEquals(
            Sets.newHashSet("__drds_heartbeat___GOBU", "accounts_ap0Y", "user_Gvli", "accounts_SuV2"),
            set2);
    }

    @Test
    public void testFindPhyTable() {
        // prepare data
        LogicMetaTopology x = buildTopology("000",
            () -> JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class));

        // remove some physical table
        Set<Pair<String, String>> seeds = new HashSet<>();
        x.getLogicDbMetas().forEach(d -> {
            d.getLogicTableMetas().forEach(t -> {
                t.getPhySchemas().forEach(p -> {
                    if (STORAGE_INST_ID.equals(p.getStorageInstId())) {
                        String phyTable = p.getPhyTables().get(0);
                        seeds.add(Pair.of(p.getSchema(), phyTable));
                    }
                });
            });
        });

        // do apply
        metaManager.applyBase(new BinlogPosition(null, "1"), x, "000");
        seeds.forEach(s -> {
            metaManager.applyPhysical(new BinlogPosition(null, "1"), s.getKey(), "drop table " + s.getValue(), null);
        });

        // check
        x = buildTopology("000",
            () -> JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class));
        Set<Pair<String, String>> checkSet = new HashSet<>();
        x.getLogicDbMetas().forEach(d -> {
            d.getLogicTableMetas().forEach(t -> {
                t.getPhySchemas().forEach(p -> {
                    if (STORAGE_INST_ID.equals(p.getStorageInstId())) {
                        p.getPhyTables().forEach(s -> {
                            Pair<String, String> pair = Pair.of(p.getSchema(), s);
                            if (seeds.contains(pair)) {
                                TableMeta tableMeta = metaManager.findPhyTable(p.getSchema(), s, false);
                                Assert.assertNull(tableMeta);

                                tableMeta = metaManager.findPhyTable(p.getSchema(), s, true);
                                Assert.assertNotNull(tableMeta);

                                checkSet.add(pair);
                            } else {
                                TableMeta tableMeta = metaManager.findPhyTable(p.getSchema(), s, false);
                                Assert.assertNotNull(tableMeta);
                            }
                        });
                    }
                });
            });
        });
        Assert.assertEquals(seeds, checkSet);
    }

    @Test
    public void testMetaCache() throws NoSuchFieldException, IllegalAccessException, InterruptedException {
        LogicMetaTopology x = buildTopology("000",
            () -> JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class));

        PolarDbXTableMetaManager metaManager1 = new PolarDbXTableMetaManager("polardbx-storage-0-master",
            hiddenPkSupplier, dnVersionSupplier);
        metaManager1.init();
        metaManager1.applyBase(new BinlogPosition(null, "1"), x, "000");
        metaManager1.compare("transfer_test_000002", "accounts_ap0Y", 2);
        Field field = PolarDbXTableMetaManager.class.getDeclaredField("compareCache");
        field.setAccessible(true);
        Map<String, LogicTableMeta> cache = (Map<String, LogicTableMeta>) field.get(metaManager1);
        Assert.assertEquals(1, cache.size());
        setConfig(ConfigKeys.TASK_REFORMAT_ATTACH_DRDS_HIDDEN_PK_ENABLED, "true");
        DynamicApplicationConfig.setValue(ConfigKeys.TASK_REFORMAT_ATTACH_DRDS_HIDDEN_PK_ENABLED, "true");
        Thread.sleep(10000);
        Assert.assertEquals(0, cache.size());
    }

    private void buildMetaManager() {
        metaManager = new PolarDbXTableMetaManager(STORAGE_INST_ID, hiddenPkSupplier, dnVersionSupplier);
        metaManager.init();
        metaManager.getConsistencyChecker().setOriginMetaSupplier(i -> "");
    }
}
