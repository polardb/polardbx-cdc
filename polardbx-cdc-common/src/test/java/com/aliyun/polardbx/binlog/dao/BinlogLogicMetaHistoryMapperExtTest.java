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

public class BinlogLogicMetaHistoryMapperExtTest extends BaseTestWithGmsTables {

    @Test
    public void test() {
        BinlogLogicMetaHistoryMapper mapper = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);

        BinlogLogicMetaHistory history1 = new BinlogLogicMetaHistory();
        history1.setDdl("create table t1(id bigint primary key)");
        history1.setTableName("t1");
        history1.setDbName("xxx");
        history1.setType((byte) 2);
        history1.setTso("101");
        history1.setSqlKind("CREATE_TABLE");
        history1.setDelete(true);
        mapper.insertSelective(history1);

        BinlogLogicMetaHistory history2 = new BinlogLogicMetaHistory();
        history2.setDdl("create table t2(id bigint primary key)");
        history2.setTableName("t2");
        history2.setDbName("xxx");
        history2.setType((byte) 2);
        history2.setTso("102");
        history2.setSqlKind("CREATE_TABLE");
        history2.setDelete(true);
        mapper.insertSelective(history2);

        BinlogLogicMetaHistoryMapperExtend binlogLogicMetaHistoryMapperExtend =
            SpringContextHolder.getObject(BinlogLogicMetaHistoryMapperExtend.class);
        int n = binlogLogicMetaHistoryMapperExtend.softClean("103");
        Assert.assertEquals(2, n);
    }
}
