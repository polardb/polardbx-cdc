/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class BinlogLogicMetaHistoryTest extends BaseTestWithGmsTables {
    @Test
    public void testWhitespaceInTable() throws Exception {
        BinlogLogicMetaHistoryMapper map = SpringContextHolder.getObject(BinlogLogicMetaHistoryMapper.class);
        BinlogLogicMetaHistory history = new BinlogLogicMetaHistory();
        history.setDbName("test_db_ ");
        history.setTableName("test_tb_ ");
        history.setDdl("create table `test_tb_ `(id bigint(20) primary key auto_increment)");
        history.setDdlJobId(1L);
        history.setDdlRecordId(1L);
        history.setSqlKind("CREATE_TABLE");
        history.setTso("aaa");
        history.setType((byte) 1);
        map.insertSelective(history);
        BinlogLogicMetaHistory result =
            map.select(s -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.ddlJobId, isEqualTo(1L))).get(0);
        Assert.assertEquals("test_db_ ", result.getDbName());
        Assert.assertEquals("test_tb_ ", result.getTableName());
    }
}
