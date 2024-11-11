/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.cdc.meta;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import com.aliyun.polardbx.binlog.canal.core.model.BinlogPosition;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_BUILD_SHARE_TOPOLOGY_ENABLED;
import static com.aliyun.polardbx.binlog.ConfigKeys.META_PERSIST_ENABLED;

/**
 * created by ziyang.lb
 **/
public class RplTableMetaManagerTest_Basic extends BaseTestWithGmsTables {

    @Before
    public void before() {
        setConfig(META_PERSIST_ENABLED, "OFF");
        setConfig(META_BUILD_SHARE_TOPOLOGY_ENABLED, "OFF");
    }

    @Test
    public void testBasic() throws SQLException {
        prepareData();
        Connection connection = getGmsDataSource().getConnection();
        RplTableMetaManager.DEFAULT_TABLE_NAME = "binlog_logic_meta_history";
        RplTableMetaManager tableMetaManager = new RplTableMetaManager(connection);
        tableMetaManager.rollback("104");

        TableMeta t1 = tableMetaManager.getTableMeta("xxx", "t1");
        Assert.assertEquals(Lists.newArrayList("id"),
            t1.getFields().stream().map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()));
        System.out.println(t1);

        TableMeta t2 = tableMetaManager.getTableMeta("xxx", "t2");
        Assert.assertEquals(Lists.newArrayList("id"),
            t2.getFields().stream().map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()));
        System.out.println(t2);

        TableMeta t3 = tableMetaManager.getTableMeta("xxx", "t3");
        Assert.assertEquals(Lists.newArrayList("id", "c1"),
            t3.getFields().stream().map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()));
        System.out.println(t3);

        tableMetaManager.apply(null, "xxx", "alter table t3 add column c2 bigint");
        TableMeta t4 = tableMetaManager.getTableMeta("xxx", "t3");
        Assert.assertEquals(Lists.newArrayList("id", "c1", "c2"),
            t4.getFields().stream().map(TableMeta.FieldMeta::getColumnName).collect(Collectors.toList()));
        System.out.println(t4);
    }

    @Test
    public void testParsePosition() {
        String position =
            "binlog.000013:0093495339#2710239168.1687082910.rtso(707613860112944339216072038166785392650000000000000000)";
        BinlogPosition.parseFromString(position);
    }

    private void prepareData() {
        BinlogLogicMetaHistoryMapper mapper = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);

        BinlogLogicMetaHistory history0 = new BinlogLogicMetaHistory();
        history0.setDdl("{}");
        history0.setDbName("*");
        history0.setType((byte) 1);
        history0.setTso("100");
        history0.setTopology("{\"logicDbMetas\":[],\"shared\":false,\"interned\":false,\"lowerCased\":true}");
        mapper.insertSelective(history0);

        BinlogLogicMetaHistory history1 = new BinlogLogicMetaHistory();
        history1.setDdl("create table t1(id bigint primary key)");
        history1.setTableName("t1");
        history1.setDbName("xxx");
        history1.setType((byte) 2);
        history1.setTso("101");
        history1.setSqlKind("CREATE_TABLE");
        mapper.insertSelective(history1);

        BinlogLogicMetaHistory history2 = new BinlogLogicMetaHistory();
        history2.setDdl("create table t2(id bigint primary key)");
        history2.setTableName("t2");
        history2.setDbName("xxx");
        history2.setType((byte) 2);
        history2.setTso("102");
        history2.setSqlKind("CREATE_TABLE");
        mapper.insertSelective(history2);

        BinlogLogicMetaHistory history3 = new BinlogLogicMetaHistory();
        history3.setDdl("create table t3(id bigint primary key, c1 varchar(100))");
        history3.setTableName("t3");
        history3.setDbName("xxx");
        history3.setType((byte) 2);
        history3.setTso("103");
        history3.setSqlKind("CREATE_TABLE");
        mapper.insertSelective(history3);
    }
}
