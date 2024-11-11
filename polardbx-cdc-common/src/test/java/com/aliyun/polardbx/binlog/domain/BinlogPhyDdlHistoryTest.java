/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.domain;

import com.aliyun.polardbx.binlog.SpringContextHolder;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
import com.aliyun.polardbx.binlog.testing.BaseTestWithGmsTables;
import org.junit.Assert;
import org.junit.Test;

import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class BinlogPhyDdlHistoryTest extends BaseTestWithGmsTables {
    @Test
    public void testWhitespaceInTable() throws Exception {
        BinlogPhyDdlHistoryMapper map = SpringContextHolder.getObject(BinlogPhyDdlHistoryMapper.class);
        BinlogPhyDdlHistory history = new BinlogPhyDdlHistory();
        history.setDbName("test_db_ ");

        history.setDdl("create table `test_tb_ `(id bigint(20) primary key auto_increment)");
        history.setBinlogFile("f.01");
        history.setPos(100);
        history.setClusterId("ccc");
        history.setStorageInstId("dn-1");
        history.setTso("aaa");
        map.insertSelective(history);
        BinlogPhyDdlHistory result =
            map.select(s -> s.where(BinlogPhyDdlHistoryDynamicSqlSupport.tso, isEqualTo("aaa"))).get(0);
        Assert.assertEquals("test_db_ ", result.getDbName());
    }
}
