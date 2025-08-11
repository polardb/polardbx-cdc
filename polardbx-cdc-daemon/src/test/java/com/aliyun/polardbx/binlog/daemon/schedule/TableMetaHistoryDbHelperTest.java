/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.daemon.schedule;

import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogLogicMetaHistoryMapperExtend;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryDynamicSqlSupport;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapper;
import com.aliyun.polardbx.binlog.dao.BinlogPhyDdlHistoryMapperExtend;
import com.aliyun.polardbx.binlog.dao.BinlogPolarxCommandMapper;
import com.aliyun.polardbx.binlog.domain.po.BinlogLogicMetaHistory;
import com.aliyun.polardbx.binlog.domain.po.BinlogPhyDdlHistory;
import com.aliyun.polardbx.binlog.testing.BaseTest;
import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Date;

import static com.aliyun.polardbx.binlog.ConfigKeys.META_PURGE_LOGIC_DDL_SOFT_DELETE_ENABLED;
import static com.aliyun.polardbx.binlog.SpringContextHolder.getObject;
import static org.mybatis.dynamic.sql.SqlBuilder.isEqualTo;

public class TableMetaHistoryDbHelperTest extends BaseTest {

    @Test
    public void testTryCleanPhyDDL() {
        BinlogPhyDdlHistoryMapper phyMapper = getObject(BinlogPhyDdlHistoryMapper.class);
        BinlogPhyDdlHistoryMapperExtend phyMapperExt = getObject(BinlogPhyDdlHistoryMapperExtend.class);
        TableMetaHistoryDbHelper dbHelper = new TableMetaHistoryDbHelper();

        for (int i = 0; i < 10000; i++) {
            BinlogPhyDdlHistory entity = new BinlogPhyDdlHistory();
            entity.setDdl("create table t(id int)");
            entity.setTso(StringUtils.leftPad(String.valueOf(i), 16, "0"));
            entity.setId(i);
            entity.setDbName("d1");
            entity.setExtra("");
            entity.setBinlogFile("binlog.000001");
            entity.setClusterId("cluster1");
            entity.setGmtCreated(new Date());
            entity.setGmtModified(new Date());
            phyMapper.insertSelective(entity);
        }

        int count = dbHelper.tryCleanPhyDDL(StringUtils.leftPad("0", 16, "0"), phyMapperExt);
        Assert.assertEquals(0, count);
        Assert.assertEquals(10000, phyMapper.count(s -> s));

        count = dbHelper.tryCleanPhyDDL(StringUtils.leftPad("1000", 16, "0"), phyMapperExt);
        Assert.assertEquals(1000, count);
        Assert.assertEquals(9000, phyMapper.count(s -> s));
        Assert.assertEquals(1000,
            phyMapper.select(s -> s.orderBy(BinlogPhyDdlHistoryDynamicSqlSupport.id).limit(1)).get(0).getId()
                .intValue());

        count = dbHelper.tryCleanPhyDDL(StringUtils.leftPad("6000", 16, "0"), phyMapperExt);
        Assert.assertEquals(5000, count);
        Assert.assertEquals(4000, phyMapper.count(s -> s));
        Assert.assertEquals(6000,
            phyMapper.select(s -> s.orderBy(BinlogPhyDdlHistoryDynamicSqlSupport.id).limit(1)).get(0).getId()
                .intValue());
    }

    @Test
    public void testCleanLogicMeta() {
        BinlogLogicMetaHistoryMapper logicMapper = getObject(BinlogLogicMetaHistoryMapper.class);
        BinlogLogicMetaHistoryMapperExtend logicMapperExt = getObject(BinlogLogicMetaHistoryMapperExtend.class);
        TableMetaHistoryDbHelper dbHelper = new TableMetaHistoryDbHelper();

        for (int i = 0; i < 10000; i++) {
            BinlogLogicMetaHistory entity = new BinlogLogicMetaHistory();
            entity.setDdl("create table t(id int)");
            entity.setTso(StringUtils.leftPad(String.valueOf(i), 16, "0"));
            entity.setId(i);
            entity.setType((byte) 2);
            entity.setDbName("d1");
            entity.setDdlJobId((long) i);
            entity.setDelete(false);
            entity.setGmtCreated(new Date());
            entity.setGmtModified(new Date());
            logicMapper.insertSelective(entity);
        }

        mockConfig(META_PURGE_LOGIC_DDL_SOFT_DELETE_ENABLED, "true");
        int count = dbHelper.cleanLogicMeta(StringUtils.leftPad("4000", 16, "0"), logicMapperExt);
        Assert.assertEquals(4000, count);
        Assert.assertEquals(10000, logicMapper.count(s -> s));
        Assert.assertEquals(4000,
            logicMapper.count(s -> s.where(BinlogLogicMetaHistoryDynamicSqlSupport.delete, isEqualTo(true))));

        mockConfig(META_PURGE_LOGIC_DDL_SOFT_DELETE_ENABLED, "false");
        count = dbHelper.cleanLogicMeta(StringUtils.leftPad("0", 16, "0"), logicMapperExt);
        Assert.assertEquals(0, count);
        Assert.assertEquals(10000, logicMapper.count(s -> s));

        count = dbHelper.cleanLogicMeta(StringUtils.leftPad("1000", 16, "0"), logicMapperExt);
        Assert.assertEquals(1000, count);
        Assert.assertEquals(9000, logicMapper.count(s -> s));
        Assert.assertEquals(1000,
            logicMapper.select(s -> s.orderBy(BinlogLogicMetaHistoryDynamicSqlSupport.id).limit(1)).get(0).getId()
                .intValue());

        count = dbHelper.cleanLogicMeta(StringUtils.leftPad("6000", 16, "0"), logicMapperExt);
        Assert.assertEquals(5000, count);
        Assert.assertEquals(4000, logicMapper.count(s -> s));
        Assert.assertEquals(6000,
            logicMapper.select(s -> s.orderBy(BinlogLogicMetaHistoryDynamicSqlSupport.id).limit(1)).get(0).getId()
                .intValue());
    }

    @Test
    public void testTrySetRebuildTableMetaSnapFlag() {
        BinlogPhyDdlHistoryMapper phyDdlHistoryMapper = getObject(BinlogPhyDdlHistoryMapper.class);
        BinlogPhyDdlHistoryMapper phyMapper = getObject(BinlogPhyDdlHistoryMapper.class);
        BinlogLogicMetaHistoryMapperExtend logicMapperExt = getObject(BinlogLogicMetaHistoryMapperExtend.class);

        TableMetaHistoryDbHelper dbHelper = new TableMetaHistoryDbHelper();
        dbHelper.setLogicMetaHistoryMapperExt(logicMapperExt);
        dbHelper.setPhyDdlHistoryMapper(phyDdlHistoryMapper);
        boolean result = dbHelper.trySetRebuildTableMetaSnapFlag();
        Assert.assertFalse(result);

        mockConfig("meta_build_full_snapshot_threshold", "9999");
        for (int i = 0; i < 10000; i++) {
            BinlogPhyDdlHistory entity = new BinlogPhyDdlHistory();
            entity.setDdl("create table t(id int)");
            entity.setTso(StringUtils.leftPad(String.valueOf(i), 16, "0"));
            entity.setId(i);
            entity.setDbName("d1");
            entity.setExtra("");
            entity.setBinlogFile("binlog.000001");
            entity.setClusterId("cluster1");
            entity.setGmtCreated(new Date());
            entity.setGmtModified(new Date());
            phyMapper.insertSelective(entity);
        }
        BinlogPolarxCommandMapper polarxCommandMapper = Mockito.mock(BinlogPolarxCommandMapper.class);
        dbHelper.setPolarxCommandMapper(polarxCommandMapper);
        result = dbHelper.trySetRebuildTableMetaSnapFlag();
        Assert.assertTrue(result);
    }
}
