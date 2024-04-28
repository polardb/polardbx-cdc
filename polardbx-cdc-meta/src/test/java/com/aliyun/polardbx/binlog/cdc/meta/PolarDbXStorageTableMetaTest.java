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
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.MockData;
import com.aliyun.polardbx.binlog.cdc.topology.TableType;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SHARE_TOPOLOGY_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_ENABLED;
import static com.aliyun.polardbx.binlog.cdc.topology.TopologyShareUtil.buildTopology;
import static com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig.ORIGIN_TSO;

public class PolarDbXStorageTableMetaTest extends BaseTestWithGmsTables {

    @Before
    public void before() {
        setConfig(META_PERSIST_ENABLED, "OFF");
        setConfig(META_BUILD_SHARE_TOPOLOGY_ENABLED, "OFF");
    }

    @Test
    public void testSameLogicPhyDbName() {
        LogicMetaTopology x =
            buildTopology(ORIGIN_TSO, () -> JSONObject.parseObject(MockData.BASE, LogicMetaTopology.class));
        PolarDbXLogicTableMeta logicTableMeta = new PolarDbXLogicTableMeta(new TopologyManager(), "5.7");
        logicTableMeta.init("Final");
        logicTableMeta.applyBase(new BinlogPosition(null, "1"), x, "000");
        LogicMetaTopology logicMetaTopology = logicTableMeta.getTopologyManager().getTopology();
        logicMetaTopology.getLogicDbMetas().forEach(d -> {
            d.getLogicTableMetas().forEach(t -> {
                TableMeta tableMeta = logicTableMeta.find(d.getSchema(), t.getTableName());
                Assert.assertNotNull(tableMeta);
            });
        });

        logicTableMeta.apply(new BinlogPosition(null, "2"),
            DDLRecord.builder().sqlKind("CREATE_DATABASE").schemaName("d1").ddlSql("create database d1")
                .metaInfo(MockData.CREATE_D1).build(), "000");

        logicTableMeta.apply(new BinlogPosition(null, "3"),
            DDLRecord.builder()
                .sqlKind("CREATE_TABLE")
                .schemaName("d1")
                .tableName("t1")
                .ddlSql(
                    "create PARTITION table d1.t1 (id int) dbpartition by hash(id) tbpartition by hash(id) tbpartitions 2")
                .metaInfo(MockData.CREATE_D1_T1).build(), "000");
        Assert.assertNotNull(logicTableMeta.find("d1", "t1"));

        LogicMetaTopology.LogicDbTopology logicDbTopology = new LogicMetaTopology.LogicDbTopology();
        LogicMetaTopology.LogicTableMetaTopology logicTableMetaTopology =
            new LogicMetaTopology.LogicTableMetaTopology();
        logicTableMetaTopology.setTableName("t1");
        logicTableMetaTopology.setTableType(TableType.SINGLE.getValue());
        logicTableMetaTopology.setCreateSql(
            "create PARTITION table d1.t1 (id int) dbpartition by hash(id) tbpartition by hash(id) tbpartitions 2");
        LogicMetaTopology.PhyTableTopology phyTableTopology = new LogicMetaTopology.PhyTableTopology();
        phyTableTopology.setGroup("GROUP_000_SINGLE");
        phyTableTopology.setSchema("d1");
        // logic schema name equals phy schema
        phyTableTopology.setStorageInstId("");
        phyTableTopology.setPhyTables(Lists.newArrayList("t_single"));
        logicTableMetaTopology.setPhySchemas(Lists.newArrayList(phyTableTopology));
        logicDbTopology.setLogicTableMetas(Lists.newArrayList(logicTableMetaTopology));
        logicDbTopology.setSchema("d1");
        logicDbTopology.setCharset("utf8mb4");
        x.add(logicDbTopology);
        TopologyManager manager = new TopologyManager(x);

        PolarDbXStorageTableMeta storageTableMeta = new PolarDbXStorageTableMeta("", logicTableMeta, manager, "1");
        storageTableMeta.applySnapshot("000");
        Map<String, String> snapshot = storageTableMeta.snapshot();
        Assert.assertEquals(1, snapshot.size());
        Assert.assertEquals("CREATE PARTITION TABLE `t_single` (\n"
            + "\tid int\n"
            + ") DEFAULT CHARACTER SET = utf8mb4\n"
            + "DBPARTITION BY hash(id)\n"
            + "TBPARTITION BY hash(id) TBPARTITIONS 2; \n", snapshot.get("d1"));
    }
}
