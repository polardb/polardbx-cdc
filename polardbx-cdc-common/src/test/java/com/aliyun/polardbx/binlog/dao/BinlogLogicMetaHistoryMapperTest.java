/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.dao;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

public class BinlogLogicMetaHistoryMapperTest extends BaseTestWithGmsTables {
    @Test
    public void testInsert() {
        BinlogLogicMetaHistory record = new BinlogLogicMetaHistory();
        record.setType((byte) 1);
        record.setDdl("ddl");
        record.setTopology("topology");
        record.setExtInfo("extInfo");
        record.setDdlJobId(1L);
        record.setInstructionId("instructionId");
        record.setDdlRecordId(1L);
        record.setDelete(false);
        record.setNeedApply(false);
        record.setDbName("dbName");
        record.setTableName("tableName");
        record.setSqlKind("sqlKind");
        record.setTso("tso");
        record.setGmtCreated(new java.util.Date());
        record.setGmtModified(new java.util.Date());

        BinlogLogicMetaHistoryMapper mapper = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);
        int result = mapper.insert(record);
        Assert.assertEquals(1, result);
    }

    @Test
    public void testUpdateAll() {
        BinlogLogicMetaHistory record = new BinlogLogicMetaHistory();
        record.setType((byte) 1);
        record.setDdl("ddl");
        record.setTopology("topology");
        record.setExtInfo("extInfo");
        record.setDdlJobId(1L);
        record.setInstructionId("instructionId");
        record.setDdlRecordId(1L);
        record.setDelete(false);
        record.setNeedApply(false);
        record.setDbName("dbName1");
        record.setTableName("tableName");
        record.setSqlKind("sqlKind");
        record.setTso("tso1");
        record.setGmtCreated(new java.util.Date());
        record.setGmtModified(new java.util.Date());

        BinlogLogicMetaHistoryMapper mapper = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);
        int result = mapper.insert(record);
        Assert.assertEquals(1, result);

        record.setType((byte) 2);
        record.setId(1);
        result = mapper.updateByPrimaryKey(record);
        Assert.assertEquals(1, result);

        result = mapper.updateByPrimaryKeySelective(record);
        Assert.assertEquals(1, result);
    }
}
