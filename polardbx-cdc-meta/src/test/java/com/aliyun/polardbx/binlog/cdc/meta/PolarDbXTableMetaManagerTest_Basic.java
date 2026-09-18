/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.MockData;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.testing.BaseTest;
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
import static com.aliyun.polardbx.binlog.cdc.topology.TopologyShareUtil.buildSnapshotTopology;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Slf4j
public class PolarDbXTableMetaManagerTest_Basic extends BaseTest {

    private static final String STORAGE_INST_ID = "polardbx-storage-0-master";
    private final Supplier<Boolean> hiddenPkSupplier = () -> false;
    private final Supplier<String> dnVersionSupplier = () -> "5.7";
    private PolarDbXTableMetaManager metaManager;

    @Before
    public void before() {
        mockConfig(META_PERSIST_ENABLED, "OFF");
        mockConfig(META_BUILD_SHARE_TOPOLOGY_ENABLED, "OFF");
        buildMetaManager();
    }

    @Test
    public void testApply() {
        LogicMetaTopology x = buildSnapshotTopology("000",
            () -> JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class));

        PolarDbXTableMetaManager metaManager1 = new PolarDbXTableMetaManager("polardbx-storage-0-master",
            hiddenPkSupplier, dnVersionSupplier);
        metaManager1.init();
        metaManager1.applyBase(new BinlogPosition(null, "1"), x, "000");
        Set<String> set1 = metaManager1.getPhyTables("polardbx-storage-0-master", Sets.newHashSet(), Sets.newHashSet())
            .stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toSet());
        assertEquals(
            Sets.newHashSet("ddl_test_11", "ddl_test_22", "ddl_test_33", "__drds_heartbeat___GOBU", "accounts_ap0Y",
                "ddl_test", "__drds_heartbeat_single__", "user_Gvli", "brd_tbl", "accounts_SuV2"), new HashSet<>(set1));

        PolarDbXTableMetaManager metaManager2 =
            new PolarDbXTableMetaManager("polardbx-storage-1-master", hiddenPkSupplier, dnVersionSupplier);
        metaManager2.init();
        metaManager2.applyBase(new BinlogPosition(null, "2"), x, "000");
        Set<String> set2 = metaManager2.getPhyTables("polardbx-storage-1-master", Sets.newHashSet(), Sets.newHashSet())
            .stream().flatMap(p -> p.getPhyTables().stream()).collect(Collectors.toSet());
        assertEquals(
            Sets.newHashSet("__drds_heartbeat___GOBU", "accounts_ap0Y", "user_Gvli", "accounts_SuV2"),
            set2);
    }

    @Test
    public void testFindPhyTable() {
        // prepare data
        LogicMetaTopology x = buildSnapshotTopology("000",
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
        x = buildSnapshotTopology("000",
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
                                assertNotNull(tableMeta);

                                checkSet.add(pair);
                            } else {
                                TableMeta tableMeta = metaManager.findPhyTable(p.getSchema(), s, false);
                                assertNotNull(tableMeta);
                            }
                        });
                    }
                });
            });
        });
        assertEquals(seeds, checkSet);
    }

    @Test
    public void testMetaCache() throws NoSuchFieldException, IllegalAccessException, InterruptedException {
        LogicMetaTopology x = buildSnapshotTopology("000",
            () -> JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class));

        PolarDbXTableMetaManager metaManager1 = new PolarDbXTableMetaManager("polardbx-storage-0-master",
            hiddenPkSupplier, dnVersionSupplier);
        metaManager1.init();
        metaManager1.applyBase(new BinlogPosition(null, "1"), x, "000");
        metaManager1.compare("transfer_test_000002", "accounts_ap0Y", 2);
        Field field = PolarDbXTableMetaManager.class.getDeclaredField("compareCache");
        field.setAccessible(true);
        Map<String, LogicTableMeta> cache = (Map<String, LogicTableMeta>) field.get(metaManager1);
        assertEquals(1, cache.size());
        mockConfig(ConfigKeys.TASK_REFORMAT_ATTACH_DRDS_HIDDEN_PK_ENABLED, "true");
        DynamicApplicationConfig.setValue(ConfigKeys.TASK_REFORMAT_ATTACH_DRDS_HIDDEN_PK_ENABLED, "true");
        Thread.sleep(10000);
        assertEquals(0, cache.size());
    }

    @Test
    public void testCloneAndProcessBeforeApply() {
        DDLExtInfo ddlExtInfo = mock(DDLExtInfo.class);
        when(ddlExtInfo.getSqlMode()).thenReturn("");
        DDLRecord ddlRecord =
            new DDLRecord(1L, 1L, "FLUSH_LOGS", "test", "test", "CREATE TABLE test", "test", 1, ddlExtInfo);
        DDLRecord res = PolarDbXTableMetaManager.cloneAndProcessBeforeApply(ddlRecord);
        assertEquals(ddlRecord.getExtInfo().getSqlMode(), res.getExtInfo().getSqlMode());
    }

    @Test
    public void testSetTableIdForVirtualTable() {
        metaManager.setTableIdForVirtualTable(null, null);
    }

    @Test
    public void testBuildSnapshotWithUpperCaseLogicIdentifiers() {
        LogicMetaTopology base = buildSnapshotTopology("000",
            () -> JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class));
        metaManager.applyBase(new BinlogPosition(null, "1"), base, "000");

        LogicMetaTopology snapshot = JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class);
        LogicMetaTopology.LogicDbTopology logicDb = snapshot.getLogicDbMetas().get(0);
        logicDb.setSchema("UPPER_CASE_DB");
        logicDb.getLogicTableMetas().get(0).setTableName("UPPER_CASE_TABLE");

        BinlogPosition snapshotPosition = new BinlogPosition(null, "2");
        snapshotPosition.setRtso("001");
        metaManager.buildSnapshot(snapshotPosition, JSONObject.toJSONString(snapshot), "001");

        Assert.assertTrue(metaManager.getTableId("upper_case_db", "upper_case_table") > 0);
    }

    @Test
    public void testBuildSnapshotSkippedWhenTsoNotGreaterThanRollBackTso() throws Exception {
        LogicMetaTopology base = buildSnapshotTopology("000",
            () -> JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class));
        metaManager.applyBase(new BinlogPosition(null, "1"), base, "000");
        long tableIdBefore = metaManager.getTableId("transfer_test", "accounts");
        long maxTableIdBefore = getTopologyManager().getTopology().getMaxTableId();

        // 模拟崩溃恢复：回滚完成后 rollBackTso 已设置，起始位点锚定在 build-snapshot 指令自身（指令 tso == rollBackTso）
        getTopologyManager().setRollBackTso("002");
        LogicMetaTopology snapshot = JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class);
        BinlogPosition equalPosition = new BinlogPosition(null, "3");
        equalPosition.setRtso("002");
        int affectRow = metaManager.buildSnapshot(equalPosition, JSONObject.toJSONString(snapshot), "002");

        // 指令效果已由回滚阶段重建，应跳过：不落库、不执行 realloc（counter 与既有映射均不变）
        assertEquals(0, affectRow);
        assertEquals(maxTableIdBefore, getTopologyManager().getTopology().getMaxTableId());
        assertEquals(tableIdBefore, (long) metaManager.getTableId("transfer_test", "accounts"));

        // tso 小于 rollBackTso 同样跳过
        BinlogPosition earlierPosition = new BinlogPosition(null, "4");
        earlierPosition.setRtso("001");
        assertEquals(0, metaManager.buildSnapshot(earlierPosition, JSONObject.toJSONString(snapshot), "001x"));
        assertEquals(maxTableIdBefore, getTopologyManager().getTopology().getMaxTableId());
    }

    @Test
    public void testBuildSnapshotExecutedWhenTsoGreaterThanRollBackTso() throws Exception {
        LogicMetaTopology base = buildSnapshotTopology("000",
            () -> JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class));
        metaManager.applyBase(new BinlogPosition(null, "1"), base, "000");
        long maxTableIdBefore = getTopologyManager().getTopology().getMaxTableId();

        // 正常 live 场景：新指令 tso 大于 rollBackTso，buildSnapshot 照常执行 realloc
        getTopologyManager().setRollBackTso("001");
        LogicMetaTopology snapshot = JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class);
        BinlogPosition snapshotPosition = new BinlogPosition(null, "3");
        snapshotPosition.setRtso("002");
        int affectRow = metaManager.buildSnapshot(snapshotPosition, JSONObject.toJSONString(snapshot), "002");

        assertEquals(1, affectRow);
        Assert.assertTrue(getTopologyManager().getTopology().getMaxTableId() > maxTableIdBefore);
    }

    private TopologyManager getTopologyManager() throws Exception {
        Field field = PolarDbXTableMetaManager.class.getDeclaredField("topologyManager");
        field.setAccessible(true);
        return (TopologyManager) field.get(metaManager);
    }

    private void buildMetaManager() {
        metaManager = new PolarDbXTableMetaManager(STORAGE_INST_ID, hiddenPkSupplier, dnVersionSupplier);
        metaManager.init();
        metaManager.getConsistencyChecker().setOriginMetaSupplier(i -> "");
    }
}
