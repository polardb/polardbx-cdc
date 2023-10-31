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
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.MockData;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
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

    private void buildMetaManager() {
        metaManager = new PolarDbXTableMetaManager(STORAGE_INST_ID, hiddenPkSupplier, dnVersionSupplier);
        metaManager.init();
        metaManager.getConsistencyChecker().setOriginMetaSupplier(i -> "");
    }
}
