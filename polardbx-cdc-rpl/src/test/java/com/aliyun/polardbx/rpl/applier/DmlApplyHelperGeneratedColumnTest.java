/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSAction;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultColumnSet;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowData;
import com.aliyun.polardbx.rpl.RplWithGmsTablesBaseTest;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.extractor.full.ExtractorUtil;
import com.aliyun.polardbx.rpl.extractor.full.RowChangeBuilder;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;

import java.io.Serializable;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 测试 commit 167deb9e 中 DmlApplyHelper.getMergeInsertSqlExecContextV2 生成列过滤逻辑
 * 以及 ExtractorUtil.buildRowChangeMeta 生成列标记传递逻辑
 */
@Slf4j
public class DmlApplyHelperGeneratedColumnTest extends RplWithGmsTablesBaseTest {

    /**
     * 测试 getMergeInsertSqlExecContextV2 过滤生成列：
     * 当表有生成列时，INSERT SQL 不应包含该列
     */
    @Test
    public void testGetMergeInsertSqlExecContextV2_filtersGeneratedColumns() {
        // 构建 TableInfo：id(PK), name(普通列), full_name(生成列)
        TableInfo tableInfo = new TableInfo("testdb", "test_gen_col");
        List<ColumnInfo> columnInfos = new ArrayList<>();
        columnInfos.add(new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20));
        columnInfos.add(new ColumnInfo("name", Types.VARCHAR, null, true, false, "VARCHAR", 100));
        columnInfos.add(new ColumnInfo("full_name", Types.VARCHAR, null, true, true, "VARCHAR", 200));
        tableInfo.setColumns(columnInfos);
        tableInfo.setPks(Lists.newArrayList("id"));

        // 构建 DefaultRowChange（含3列，其中 full_name 标记为 generated）
        List<DBMSColumn> dbmsColumns = new ArrayList<>();
        DefaultColumn col1 = new DefaultColumn("id", 0, Types.BIGINT, false, false, false, false, false, false, false);
        DefaultColumn col2 =
            new DefaultColumn("name", 1, Types.VARCHAR, false, true, false, false, false, false, false);
        DefaultColumn col3 =
            new DefaultColumn("full_name", 2, Types.VARCHAR, false, true, false, false, true, false, false);
        dbmsColumns.add(col1);
        dbmsColumns.add(col2);
        dbmsColumns.add(col3);

        DefaultRowChange rowChange = new DefaultRowChange(DBMSAction.INSERT, "testdb", "test_gen_col",
            new DefaultColumnSet(dbmsColumns));
        rowChange.addRowData(new DefaultRowData(new Serializable[] {1L, "hello", "gen_hello"}));

        // 执行
        SqlContextV2 ctx = DmlApplyHelper.getMergeInsertSqlExecContextV2(rowChange, tableInfo,
            com.aliyun.polardbx.rpl.common.RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);

        // 验证生成的 SQL 不包含 full_name 列
        String sql = ctx.getSql();
        log.info("Generated SQL: {}", sql);
        Assert.assertTrue("SQL should contain column 'id'", sql.contains("`id`"));
        Assert.assertTrue("SQL should contain column 'name'", sql.contains("`name`"));
        Assert.assertFalse("SQL should NOT contain generated column 'full_name'", sql.contains("`full_name`"));

        // 验证参数列表仅包含2个值（id 和 name），不含 full_name
        Assert.assertEquals(1, ctx.getParamsList().size());
        Assert.assertEquals(2, ctx.getParamsList().get(0).size());
    }

    /**
     * 测试 getMergeInsertSqlExecContextV2：无生成列时所有列都包含
     */
    @Test
    public void testGetMergeInsertSqlExecContextV2_noGeneratedColumns() {
        TableInfo tableInfo = new TableInfo("testdb", "test_no_gen");
        List<ColumnInfo> columnInfos = new ArrayList<>();
        columnInfos.add(new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20));
        columnInfos.add(new ColumnInfo("name", Types.VARCHAR, null, true, false, "VARCHAR", 100));
        columnInfos.add(new ColumnInfo("age", Types.INTEGER, null, true, false, "INT", 11));
        tableInfo.setColumns(columnInfos);
        tableInfo.setPks(Lists.newArrayList("id"));

        List<DBMSColumn> dbmsColumns = new ArrayList<>();
        dbmsColumns.add(new DefaultColumn("id", 0, Types.BIGINT, false, false, false, false, false, false, false));
        dbmsColumns.add(new DefaultColumn("name", 1, Types.VARCHAR, false, true, false, false, false, false, false));
        dbmsColumns.add(new DefaultColumn("age", 2, Types.INTEGER, false, true, false, false, false, false, false));

        DefaultRowChange rowChange = new DefaultRowChange(DBMSAction.INSERT, "testdb", "test_no_gen",
            new DefaultColumnSet(dbmsColumns));
        rowChange.addRowData(new DefaultRowData(new Serializable[] {1L, "hello", 25}));

        SqlContextV2 ctx = DmlApplyHelper.getMergeInsertSqlExecContextV2(rowChange, tableInfo,
            com.aliyun.polardbx.rpl.common.RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);

        String sql = ctx.getSql();
        Assert.assertTrue(sql.contains("`id`"));
        Assert.assertTrue(sql.contains("`name`"));
        Assert.assertTrue(sql.contains("`age`"));
        Assert.assertEquals(1, ctx.getParamsList().size());
        Assert.assertEquals(3, ctx.getParamsList().get(0).size());
    }

    /**
     * 测试 ExtractorUtil.buildRowChangeMeta：生成列标记从 TableInfo 传递到 RowChange 的列元数据
     */
    @Test
    public void testBuildRowChangeMeta_generatedColumnMarked() {
        TableInfo tableInfo = new TableInfo("testdb", "test_gen");
        List<ColumnInfo> columnInfos = new ArrayList<>();
        columnInfos.add(new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20));
        columnInfos.add(new ColumnInfo("name", Types.VARCHAR, null, true, false, "VARCHAR", 100));
        columnInfos.add(new ColumnInfo("gen_col", Types.VARCHAR, null, true, true, "VARCHAR", 200));
        tableInfo.setColumns(columnInfos);
        tableInfo.setPks(Lists.newArrayList("id"));

        RowChangeBuilder builder = ExtractorUtil.buildRowChangeMeta(tableInfo, "testdb", "test_gen",
            DBMSAction.INSERT);

        // 验证 gen_col 在 metaColumns 中被标记为 generated
        List<DBMSColumn> metaColumns = builder.getMetaColumns();
        Assert.assertEquals(3, metaColumns.size());

        DBMSColumn genCol = metaColumns.stream()
            .filter(c -> c.getName().equals("gen_col"))
            .findFirst()
            .orElse(null);
        Assert.assertNotNull("gen_col should exist in metaColumns", genCol);
        Assert.assertTrue("gen_col should be marked as generated", genCol.isGenerated());

        // 验证非生成列不被标记
        DBMSColumn nameCol = metaColumns.stream()
            .filter(c -> c.getName().equals("name"))
            .findFirst()
            .orElse(null);
        Assert.assertNotNull(nameCol);
        Assert.assertFalse("name should NOT be marked as generated", nameCol.isGenerated());
    }

    /**
     * 测试 ExtractorUtil.buildRowChangeMeta：无生成列时所有列都不标记
     */
    @Test
    public void testBuildRowChangeMeta_noGeneratedColumns() {
        TableInfo tableInfo = new TableInfo("testdb", "test_plain");
        List<ColumnInfo> columnInfos = new ArrayList<>();
        columnInfos.add(new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20));
        columnInfos.add(new ColumnInfo("value", Types.INTEGER, null, true, false, "INT", 11));
        tableInfo.setColumns(columnInfos);
        tableInfo.setPks(Lists.newArrayList("id"));

        RowChangeBuilder builder = ExtractorUtil.buildRowChangeMeta(tableInfo, "testdb", "test_plain",
            DBMSAction.INSERT);

        List<DBMSColumn> metaColumns = builder.getMetaColumns();
        for (DBMSColumn col : metaColumns) {
            Assert.assertFalse("Column " + col.getName() + " should not be generated", col.isGenerated());
        }
    }

    /**
     * 测试多行插入时生成列也被正确过滤
     */
    @Test
    public void testGetMergeInsertSqlExecContextV2_multiRowFiltersGeneratedColumns() {
        TableInfo tableInfo = new TableInfo("testdb", "test_multi_gen");
        List<ColumnInfo> columnInfos = new ArrayList<>();
        columnInfos.add(new ColumnInfo("id", Types.BIGINT, null, false, false, "BIGINT", 20));
        columnInfos.add(new ColumnInfo("val", Types.INTEGER, null, true, false, "INT", 11));
        columnInfos.add(new ColumnInfo("computed", Types.INTEGER, null, true, true, "INT", 11));
        tableInfo.setColumns(columnInfos);
        tableInfo.setPks(Lists.newArrayList("id"));

        List<DBMSColumn> dbmsColumns = new ArrayList<>();
        dbmsColumns.add(new DefaultColumn("id", 0, Types.BIGINT, false, false, false, false, false, false, false));
        dbmsColumns.add(new DefaultColumn("val", 1, Types.INTEGER, false, true, false, false, false, false, false));
        dbmsColumns.add(new DefaultColumn("computed", 2, Types.INTEGER, false, true, false, false, true, false, false));

        DefaultRowChange rowChange = new DefaultRowChange(DBMSAction.INSERT, "testdb", "test_multi_gen",
            new DefaultColumnSet(dbmsColumns));
        rowChange.addRowData(new DefaultRowData(new Serializable[] {1L, 10, 100}));
        rowChange.addRowData(new DefaultRowData(new Serializable[] {2L, 20, 200}));
        rowChange.addRowData(new DefaultRowData(new Serializable[] {3L, 30, 300}));

        SqlContextV2 ctx = DmlApplyHelper.getMergeInsertSqlExecContextV2(rowChange, tableInfo,
            com.aliyun.polardbx.rpl.common.RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);

        // 每行只应有2个参数（id, val），不含 computed
        Assert.assertEquals(3, ctx.getParamsList().size());
        for (List<Serializable> row : ctx.getParamsList()) {
            Assert.assertEquals(2, row.size());
        }
        Assert.assertFalse(ctx.getSql().contains("`computed`"));
    }
}
