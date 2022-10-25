/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.SpringContextBootStrap;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.cdc.meta.domain.DDLRecord;
import com.aliyun.polardbx.binlog.cdc.topology.LogicMetaTopology;
import com.aliyun.polardbx.binlog.cdc.topology.MockData;
import com.aliyun.polardbx.binlog.cdc.topology.TopologyManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.Before;
import org.junit.Test;

public class PolarDbXLogicTableMetaTest {
    SpringContextBootStrap appContextBootStrap;
    Gson gson = new GsonBuilder().create();

    @Before
    public void init() {
        appContextBootStrap = new SpringContextBootStrap("spring/spring.xml");
        appContextBootStrap.boot();
    }

    @Test
    public void apply() {

        LogicMetaTopology x = gson.fromJson(MockData.BASE, LogicMetaTopology.class);
        PolarDbXLogicTableMeta logicTableMeta = new PolarDbXLogicTableMeta(new TopologyManager());
        logicTableMeta.init("Final");
        logicTableMeta.applyBase(new BinlogPosition(null, "1"), x);

        System.out.println(logicTableMeta.find("transfer_test", "accounts"));
        logicTableMeta.apply(new BinlogPosition(null, "2"),
            DDLRecord.builder().sqlKind("CREATE_DATABASE").schemaName("d1").ddlSql("create database d1")
                .metaInfo(MockData.CREATE_D1).build(), null);

        logicTableMeta.apply(new BinlogPosition(null, "3"),
            DDLRecord.builder().sqlKind("CREATE_TABLE").schemaName("d1").tableName("t1").ddlSql(
                "create PARTITION table d1.t1 (id int) dbpartition by hash(id) tbpartition by hash(id) tbpartitions 2")
                .metaInfo(MockData.CREATE_D1_T1).build(), null);

        System.out.println(logicTableMeta.find("d1", "t1"));

    }

    @Test
    public void rollback() {
        LogicMetaTopology x = gson.fromJson(MockData.BASE, LogicMetaTopology.class);
        PolarDbXLogicTableMeta logicTableMeta = new PolarDbXLogicTableMeta(new TopologyManager());
        logicTableMeta.init("Final");

        logicTableMeta.rollback(new BinlogPosition(null, "3"));
        System.out.println(logicTableMeta.find("d1", "t1"));

    }
}