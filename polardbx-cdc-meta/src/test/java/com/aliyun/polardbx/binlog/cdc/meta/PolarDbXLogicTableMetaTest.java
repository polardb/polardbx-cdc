/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.alibaba.fastjson.JSONObject;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.MockData;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SHARE_TOPOLOGY_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_ENABLED;
import static com.aliyun.polardbx.binlog.cdc.topology.TopologyShareUtil.buildTopology;
import static com.aliyun.polardbx.binlog.scheduler.model.ExecutionConfig.ORIGIN_TSO;

public class PolarDbXLogicTableMetaTest extends BaseTestWithGmsTables {
    @Before
    public void before() {
        setConfig(META_PERSIST_ENABLED, "OFF");
        setConfig(META_BUILD_SHARE_TOPOLOGY_ENABLED, "OFF");
    }

    @Test
    public void apply() {
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
    }
}
