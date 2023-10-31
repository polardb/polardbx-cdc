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
import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLExtInfo;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsData;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mybatis.dynamic.sql.SqlBuilder;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SHARE_TOPOLOGY_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_ENABLED;
import static com.aliyun.polardbx.binlog.cdc.meta.RollbackMode.SNAPSHOT_EXACTLY;
import static com.aliyun.polardbx.binlog.cdc.meta.RollbackMode.SNAPSHOT_SEMI;
import static com.aliyun.polardbx.binlog.cdc.topology.TopologyShareUtil.buildTopology;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;
import static org.mybatis.dynamic.sql.SqlBuilder.isGreaterThan;

@Slf4j
public class PolarDbXTableMetaManagerTest_Full extends BaseTestWithGmsData {

    interface Callback<K> {
        void call(K k);
    }

    private final Supplier<Boolean> hiddenPkSupplier = () -> false;
    private final Supplier<String> dnVersionSupplier = () -> "5.7";
    private PolarDbXTableMetaManager metaManager;

    @Before
    public void before() {
        setConfig(META_PERSIST_ENABLED, "OFF");
        setConfig(META_BUILD_SHARE_TOPOLOGY_ENABLED, "OFF");
    }

    @Test
    public void testRollback_In_Snapshot_Exactly() {
        rollback(SNAPSHOT_EXACTLY.name());
    }

    @Test
    public void testRollback_In_Semi_Snapshot() {
        rollback(SNAPSHOT_SEMI.name());
    }

    @Test
    public void testApply() {
        setConfig(ConfigKeys.META_BUILD_CHECK_CONSISTENCY_ENABLED, "true");
        BinlogLogicMetaHistoryMapper logicMapper = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);
        BinlogPhyDdlHistoryMapper phyMapper = SpringContextHolder.getObject(BinlogPhyDdlHistoryMapper.class);
        List<BinlogLogicMetaHistory> logicSnapshotList = logicMapper.select(
            s -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.type, isEqualTo((byte) 1))
                .orderBy(BinlogLogicMetaHistoryDynamicSqlSupport.tso));
        BinlogLogicMetaHistory logicSnapshot = logicSnapshotList.get(0);

        executeWithCallback(k -> {
            buildMetaManager(k.getValue());
            metaManager.applyBase(new BinlogPosition(null, logicSnapshot.getTso()),
                buildTopology(logicSnapshot.getTso(),
                    () -> buildLogicMetaTopology(logicMapper, logicSnapshot.getTso())), null);

            List<BinlogLogicMetaHistory> logicList = logicMapper.select(
                s -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.tso, isGreaterThan(logicSnapshot.getTso()))
                    .and(BinlogLogicMetaHistoryDynamicSqlSupport.type, isEqualTo((byte) 2)));
            List<BinlogPhyDdlHistory> phyList = phyMapper.select(s ->
                s.where(BinlogPhyDdlHistoryDynamicSqlSupport.tso, isGreaterThan(logicSnapshot.getTso()))
                    .and(BinlogPhyDdlHistoryDynamicSqlSupport.clusterId, isEqualTo(k.getKey()))
                    .and(BinlogPhyDdlHistoryDynamicSqlSupport.storageInstId, isEqualTo(k.getValue())));
            List<Object> allList = mergeSort(logicList, phyList);

            allList.forEach(i -> {
                if (i instanceof BinlogLogicMetaHistory) {
                    BinlogLogicMetaHistory logicDdl = (BinlogLogicMetaHistory) i;
                    DDLRecord ddlRecord = new DDLRecord();
                    ddlRecord.setDdlSql(logicDdl.getDdl());
                    ddlRecord.setId(logicDdl.getDdlRecordId());
                    ddlRecord.setJobId(logicDdl.getDdlJobId());
                    ddlRecord.setSchemaName(logicDdl.getDbName());
                    ddlRecord.setTableName(logicDdl.getTableName());
                    ddlRecord.setSqlKind(logicDdl.getSqlKind());
                    ddlRecord.setExtInfo(DDLExtInfo.parseExtInfo(logicDdl.getExtInfo()));
                    ddlRecord.setMetaInfo(logicDdl.getTopology());
                    metaManager.applyLogic(new BinlogPosition(null, logicDdl.getTso()), ddlRecord,
                        logicDdl.getInstructionId());
                } else if (i instanceof BinlogPhyDdlHistory) {
                    BinlogPhyDdlHistory phyDdl = (BinlogPhyDdlHistory) i;
                    metaManager.applyPhysical(new BinlogPosition(null, phyDdl.getTso()),
                        phyDdl.getDbName(), phyDdl.getDdl(), null);
                }
            });

            Assert.assertTrue(metaManager.getConsistencyChecker().getCheckCount().get() > 0);
        });
    }

    private void buildMetaManager(String storageInstId) {
        metaManager = new PolarDbXTableMetaManager(storageInstId, hiddenPkSupplier, dnVersionSupplier);
        metaManager.init();
        metaManager.getConsistencyChecker().setOriginMetaSupplier(i -> "");
    }

    private void rollback(String rollbackMode) {
        setConfig(ConfigKeys.META_RECOVER_ROLLBACK_MODE, rollbackMode);
        BinlogLogicMetaHistoryMapper logicMapper = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);

        Optional<BinlogLogicMetaHistory> maxTso = logicMapper.selectOne(s ->
            s.orderBy(BinlogLogicMetaHistoryDynamicSqlSupport.tso.descending()).limit(1));
        long count = logicMapper.count(s -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.type,
            isEqualTo((byte) 1)));
        log.info("logic snapshot count is " + count);

        executeWithCallback((k) -> {
            log.info("start to rollback with cluster_id {} and with storage_inst_id {}.", k.getKey(), k.getValue());
            buildMetaManager(k.getValue());
            metaManager.rollback(new BinlogPosition(null, maxTso.get().getTso()));
            Map<String, Set<String>> result = metaManager.initDeltaChangeMap(maxTso.get().getTso());
            Assert.assertTrue(result.isEmpty());
            Assert.assertTrue(metaManager.getDeltaChangeMap().isEmpty());
        });
    }

    private void executeWithCallback(Callback<Pair<String, String>> callback) {
        JdbcTemplate jdbcTemplate = SpringContextHolder.getObject("metaJdbcTemplate");
        List<String> storageInstIds =
            jdbcTemplate.queryForList("select distinct storage_inst_id from binlog_phy_ddl_history", String.class);
        log.info("storage inst ids for test is " + storageInstIds);

        List<String> clusterIds =
            jdbcTemplate.queryForList("select distinct cluster_id from binlog_phy_ddl_history", String.class);
        log.info("cluster ids for test is " + clusterIds);

        for (String clusterId : clusterIds) {
            setConfig(ConfigKeys.CLUSTER_ID, clusterId);
            for (String storageInstId : storageInstIds) {
                callback.call(Pair.of(clusterId, storageInstId));
            }
        }
    }

    private LogicMetaTopology buildLogicMetaTopology(BinlogLogicMetaHistoryMapper logicMapper, String snapshotTso) {
        long queryStartTime = System.currentTimeMillis();
        Optional<BinlogLogicMetaHistory> snapshot = logicMapper.selectOne(s -> s
            .where(BinlogLogicMetaHistoryDynamicSqlSupport.tso, SqlBuilder.isEqualTo(snapshotTso)));
        return JSONObject.parseObject(snapshot.get().getTopology(), LogicMetaTopology.class);
    }

    public static List<Object> mergeSort(List<BinlogLogicMetaHistory> logicList, List<BinlogPhyDdlHistory> phyList) {
        List<Object> mergedList = new ArrayList<>();
        int i = 0, j = 0;
        while (i < logicList.size() && j < phyList.size()) {
            if (logicList.get(i).getTso().compareTo(phyList.get(j).getTso()) < 0) {
                mergedList.add(logicList.get(i));
                i++;
            } else {
                mergedList.add(phyList.get(j));
                j++;
            }
        }
        while (i < logicList.size()) {
            mergedList.add(logicList.get(i));
            i++;
        }

        while (j < phyList.size()) {
            mergedList.add(phyList.get(j));
            j++;
        }

        return mergedList;
    }
}
