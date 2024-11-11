/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.testing.h2.H2Util;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaCache;
import com.aliyun.polardbx.rpl.dbmeta.DbMetaManager;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.extractor.full.ExtractorUtil;
import com.aliyun.polardbx.rpl.extractor.full.RowChangeBuilder;
import com.aliyun.polardbx.rpl.taskmeta.HostType;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.Serializable;
import java.sql.Types;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.aliyun.polardbx.rpl.applier.DmlApplyHelper.getIsLabEnv;
import static com.aliyun.polardbx.rpl.applier.DmlApplyHelper.getUpdateChangeColumns;
import static com.aliyun.polardbx.rpl.applier.DmlApplyHelper.setIsLabEnv;

import static org.mockito.Mockito.when;

@Slf4j
public class DmlApplyHelperTest extends RplWithGmsTablesBaseTest {

    @Test
    public void testGetPkColumnsIndex_CacheHit() throws Exception {
        Map<String, List<Integer>> allTbPkColumns = new HashMap<>();
        String fullTbName = "testSchema.testTable";
        List<Integer> expectedPkColumnsIndex = Lists.newArrayList(0, 1, 2);
        allTbPkColumns.put(fullTbName, expectedPkColumnsIndex);

        List<Integer> result = DmlApplyHelper.getPkColumnsIndex(allTbPkColumns, fullTbName, null);

        Assert.assertEquals(expectedPkColumnsIndex, result);
    }

    @Test
    public void testGetPkColumnsIndex_CacheMiss() throws Exception {
        Map<String, List<Integer>> allTbPkColumns = new HashMap<>();
        String fullTbName = "testSchema.testTable";
        // DefaultRowChange rowChange = new DefaultRowChange();
        DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
        when(rowChange.getSchema()).thenReturn("testSchema");
        when(rowChange.getTable()).thenReturn("testTable");

        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
        TableInfo tableInfo = Mockito.mock(TableInfo.class);

        when(dbMetaCache.getTableInfo("testSchema", "testTable")).thenReturn(tableInfo);
        when(tableInfo.getPks()).thenReturn(Lists.newArrayList("col1", "col2", "col3"));
        when(rowChange.getColumnIndex("col1")).thenReturn(0);
        when(rowChange.getColumnIndex("col2")).thenReturn(1);
        when(rowChange.getColumnIndex("col3")).thenReturn(2);

        DmlApplyHelper.setDbMetaCache(dbMetaCache);
        List<Integer> result = DmlApplyHelper.getPkColumnsIndex(allTbPkColumns, fullTbName, rowChange);

        List<Integer> expectedPkColumnsIndex = Lists.newArrayList(0, 1, 2);
        Assert.assertEquals(expectedPkColumnsIndex, result);
        Assert.assertTrue(allTbPkColumns.containsKey(fullTbName));
        Assert.assertEquals(expectedPkColumnsIndex, allTbPkColumns.get(fullTbName));
    }

    @Test
    public void testGetPkColumnsIndex_NoPks() throws Exception {
        Map<String, List<Integer>> allTbPkColumns = new HashMap<>();
        String fullTbName = "testSchema.testTable";
        DefaultRowChange rowChange = Mockito.mock(DefaultRowChange.class);
        when(rowChange.getSchema()).thenReturn("testSchema");
        when(rowChange.getTable()).thenReturn("testTable");

        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
        TableInfo tableInfo = Mockito.mock(TableInfo.class);

        when(dbMetaCache.getTableInfo("testSchema", "testTable")).thenReturn(tableInfo);
        when(tableInfo.getPks()).thenReturn(new ArrayList<>());

        DmlApplyHelper.setDbMetaCache(dbMetaCache);
        List<Integer> result = DmlApplyHelper.getPkColumnsIndex(allTbPkColumns, fullTbName, rowChange);

        Assert.assertTrue(result.isEmpty());
        Assert.assertTrue(allTbPkColumns.containsKey(fullTbName));
        Assert.assertTrue(allTbPkColumns.get(fullTbName).isEmpty());
    }

    @Test
    public void getDeleteThenReplaceDml() throws Exception {

        H2Util.execUpdate(srcDataSource, "create SCHEMA if not exists testdb");
        H2Util.execUpdate(srcDataSource, "CREATE TABLE IF NOT EXISTS testdb.testtable (\n"
            + "    `id1` bigint(20) unsigned NOT NULL,\n"
            + "    `id2` bigint(20) unsigned NOT NULL DEFAULT '0',\n"
            + "    PRIMARY KEY (`id1`)\n"
            + "    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;");

        TableInfo tableInfo = DbMetaManager.getTableInfo(srcDataSource, "testdb", "testtable",
            HostType.RDS, false);
        tableInfo.getColumns().add(new ColumnInfo("id1", 0, null, false,
            false, "", 0));
        tableInfo.getColumns().add(new ColumnInfo("id2", 0, null, false,
            false, "", 0));

        RowChangeBuilder builder = ExtractorUtil.buildRowChangeMeta(tableInfo, "testdb", "testtable",
            DBMSAction.UPDATE);
        Map<String, Serializable> fieldValueMap = new HashMap<>(tableInfo.getColumns().size());
        for (ColumnInfo column : tableInfo.getColumns()) {
            Serializable value = "1";
            fieldValueMap.put(column.getName(), value);
        }
        builder.addRowData(fieldValueMap);

        Map<String, Serializable> fieldValueMapChange = new HashMap<>(tableInfo.getColumns().size());
        for (ColumnInfo column : tableInfo.getColumns()) {
            Serializable value = "2";
            fieldValueMapChange.put(column.getName(), value);
        }
        builder.addChangeRowData(fieldValueMapChange);
        DefaultRowChange rowChange = builder.build();

        List<SqlContext> sqlContexts = DmlApplyHelper.getDeleteThenReplaceSqlExecContext(rowChange, tableInfo);
        log.info(sqlContexts.get(0).toString());
        log.info(sqlContexts.get(1).toString());
        Assert.assertEquals(sqlContexts.size(), 2);
        // random compare all make sqlContexts.get(0).params.size() 1 or 2
        // Assert.assertEquals(sqlContexts.get(0).params.size(), 1);
        Assert.assertEquals(sqlContexts.get(1).params.size(), 2);
        Assert.assertEquals(sqlContexts.get(0).params.get(0), "1");
        Assert.assertEquals(sqlContexts.get(1).params.get(0), "2");
        Assert.assertEquals(sqlContexts.get(1).params.get(1), "2");
    }

    @Test
    public void testShouldSerialExecute() throws Exception {
        String dbName = "testdb";
        String tbName1 = "testtable1";
        String tbName2 = "testtable2";
        String tbName3 = "testtable3";

        DbMetaCache dbMetaCache = Mockito.mock(DbMetaCache.class);
        TableInfo tableInfo1 = new TableInfo(dbName, tbName1);
        tableInfo1.setPks(Lists.newArrayList());

        TableInfo tableInfo2 = new TableInfo(dbName, tbName2);
        tableInfo2.setPks(Lists.newArrayList("pk1_c1"));
        tableInfo2.setUks(Lists.newArrayList("uk1_c1", "uk1_c2"));

        TableInfo tableInfo3 = new TableInfo(dbName, tbName2);
        tableInfo3.setPks(Lists.newArrayList("pk1_c1"));

        when(dbMetaCache.getTableInfo(dbName, tbName1)).thenReturn(tableInfo1);
        when(dbMetaCache.getTableInfo(dbName, tbName2)).thenReturn(tableInfo2);
        when(dbMetaCache.getTableInfo(dbName, tbName3)).thenReturn(tableInfo3);

        DmlApplyHelper.setDbMetaCache(dbMetaCache);

        // test table without pks
        DefaultRowChange rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.UPDATE);
        rowChange.setSchema(dbName);
        rowChange.setTable(tbName1);
        boolean result = DmlApplyHelper.shouldSerialExecute(rowChange);
        Assert.assertTrue(result);

        rowChange.setAction(DBMSAction.DELETE);
        result = DmlApplyHelper.shouldSerialExecute(rowChange);
        Assert.assertTrue(result);

        rowChange.setAction(DBMSAction.INSERT);
        result = DmlApplyHelper.shouldSerialExecute(rowChange);
        Assert.assertFalse(result);

        // test table with uks
        rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.DELETE);
        rowChange.setSchema(dbName);
        rowChange.setTable(tbName2);
        result = DmlApplyHelper.shouldSerialExecute(rowChange);
        Assert.assertTrue(result);

        rowChange.setAction(DBMSAction.INSERT);
        result = DmlApplyHelper.shouldSerialExecute(rowChange);
        Assert.assertFalse(result);

        // test table without uks
        rowChange = new DefaultRowChange();
        rowChange.setAction(DBMSAction.DELETE);
        rowChange.setSchema(dbName);
        rowChange.setTable(tbName3);
        result = DmlApplyHelper.shouldSerialExecute(rowChange);
        Assert.assertFalse(result);
    }

    /**
     * 测试更新列的不同模式
     * 主要目的是验证在更新操作中，根据不同模式（ONUPDATE, ALL, TIMESTAMP）选择正确的列进行更新
     * 通过构建表信息、列信息以及模拟的更新操作，来测试系统对更新列的处理是否符合预期
     */
    @Test
    public void testUpdateCols() throws Exception {
        H2Util.execUpdate(srcDataSource, "create SCHEMA if not exists cdc_update_db");
        H2Util.execUpdate(srcDataSource, "CREATE TABLE IF NOT EXISTS cdc_update_db.cdc_update (\n"
            + "    `id` bigint(20) unsigned NOT NULL,\n"
            + "    `c1` bigint(20) unsigned NOT NULL,\n"
            + "    `c2` bigint(20) unsigned NOT NULL,\n"
            + "    `t` TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,\n"
            + "    PRIMARY KEY (`id`)\n"
            + "    ) ENGINE=InnoDB DEFAULT CHARSET=utf8;");

        // 构建TableInfo, ColumnInfo
        TableInfo tableInfo = DbMetaManager.getTableInfo(srcDataSource, "cdc_update_db", "cdc_update",
            HostType.RDS, false);

        List<ColumnInfo> columnInfos = new ArrayList<>();
        columnInfos.add(new ColumnInfo("id", 0, null, false,
            false, "", 0));
        columnInfos.add(new ColumnInfo("c1", 0, null, false,
            false, "", 0));
        columnInfos.add(new ColumnInfo("c2", 0, null, false,
            false, "", 0));
        columnInfos.add(new ColumnInfo("t", Types.TIMESTAMP, null, false,
            false, "", 0));

        tableInfo.setColumns(columnInfos);
        columnInfos.get(3).setOnUpdate(true);
        List<DBMSColumn> dbmsColumns = Lists.newArrayList();

        // 构建DBMSColumns
        for (int i = 0; i < 4; i++) {
            ColumnInfo columnInfo = columnInfos.get(i);
            DefaultColumn column =
                new DefaultColumn(columnInfo.getName(), i, Types.OTHER, false, columnInfo.isNullable(),
                    false, false, columnInfo.isGenerated(), false, false);
            if (column.getName().equalsIgnoreCase("t")) {
                column.setIsOnUpdate(true);
                column.setSqlType(Types.TIMESTAMP);
            }
            dbmsColumns.add(column);
        }

        // 构建rowChanges
        DefaultRowChange rowChange =
            new DefaultRowChange(DBMSAction.UPDATE, "cdc_update_db", "cdc_update", new DefaultColumnSet(dbmsColumns));
        BitSet actualChangeColumns = new BitSet(4);
        rowChange.setChangeColumnsBitSet(actualChangeColumns);
        actualChangeColumns.set(1);

        boolean isLabEnv = getIsLabEnv();
        setIsLabEnv(true);
        // on update 模式更新列是：所有变更列 + on update 列
        setConfig(ConfigKeys.RPL_COLS_UPDATE_MODE, "ONUPDATE");
        List<DBMSColumn> changeColumns = getUpdateChangeColumns(rowChange, tableInfo);
        Set<String> colNames = changeColumns.stream().map(DBMSColumn::getName).collect(Collectors.toSet());
        Assert.assertEquals(changeColumns.size(), 2);
        Assert.assertTrue(colNames.contains("c1") && colNames.contains("t"));

        // all 模式更新列是：所有列
        setConfig(ConfigKeys.RPL_COLS_UPDATE_MODE, "ALL");
        changeColumns = getUpdateChangeColumns(rowChange, tableInfo);
        Assert.assertEquals(changeColumns.size(), 4);

        // timestamp 模式更新列是：所有变更列 + timestamp 类型列
        setConfig(ConfigKeys.RPL_COLS_UPDATE_MODE, "TIMESTAMP");
        changeColumns = getUpdateChangeColumns(rowChange, tableInfo);
        colNames = changeColumns.stream().map(DBMSColumn::getName).collect(Collectors.toSet());
        Assert.assertEquals(changeColumns.size(), 2);
        Assert.assertTrue(colNames.contains("c1") && colNames.contains("t"));

        setIsLabEnv(isLabEnv);
    }
}