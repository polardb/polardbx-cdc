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
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}
