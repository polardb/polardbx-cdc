/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.applier;

import com.aliyun.polardbx.binlog.ConfigKeys;
import com.aliyun.polardbx.binlog.DynamicApplicationConfig;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DBMSColumn;
import com.aliyun.polardbx.binlog.canal.binlog.dbms.DefaultRowChange;
import com.aliyun.polardbx.binlog.error.PolardbxException;
import com.aliyun.polardbx.rpl.common.RplConstants;
import com.aliyun.polardbx.rpl.dbmeta.ColumnInfo;
import com.aliyun.polardbx.rpl.dbmeta.TableInfo;
import com.aliyun.polardbx.rpl.taskmeta.ConflictStrategy;
import com.aliyun.polardbx.rpl.taskmeta.ConflictType;
import org.junit.After;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.sql.Types;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DmlApplyHelperReplicaTest {

    @Test
    public void updateAndDeleteRejectMissingIdentityMetadata() {
        TableInfo tableInfo = new TableInfo("dst", "tb");
        tableInfo.setPks(Collections.singletonList("id"));
        DefaultRowChange update = mock(DefaultRowChange.class);
        when(update.isForceAllColumns()).thenReturn(true);
        when(update.getRowSize()).thenReturn(1);
        doReturn(Collections.singletonList(dbmsColumn("note"))).when(update).getColumns();
        for (Runnable build : Arrays.<Runnable>asList(
            () -> DmlApplyHelper.getUpdateSqlExecContext(update, tableInfo),
            () -> DmlApplyHelper.getDeleteSqlExecContext(update, tableInfo),
            () -> DmlApplyHelper.getMergeDeleteSqlExecContext(update, tableInfo))) {
            try {
                build.run();
                Assert.fail("must not generate DML without row identity");
            } catch (PolardbxException expected) {
                Assert.assertTrue(expected.getMessage().contains("row identity metadata for dst.tb"));
            }
        }
    }

    @Test
    public void updateUsesPrimaryKeyBeforeImageAfterMetadataIsLoaded() {
        TableInfo tableInfo = new TableInfo("dst", "tb");
        tableInfo.setPks(Collections.singletonList("id"));
        tableInfo.setColumns(Arrays.asList(column("id", "BIGINT"), column("note", "VARCHAR")));
        DefaultRowChange update = mock(DefaultRowChange.class);
        when(update.isForceAllColumns()).thenReturn(true);
        doReturn(Collections.singletonList(dbmsColumn("note"))).when(update).getColumns();
        when(update.getRowValue(1, "id")).thenReturn(10000L);
        when(update.getChangeValue(1, "note")).thenReturn("updated-before-conflict");
        SqlContext sql = DmlApplyHelper.getUpdateSqlExecContext(update, tableInfo);
        Assert.assertEquals("UPDATE `dst`.`tb` SET `note`=? WHERE `id`=?", sql.getSql());
        Assert.assertEquals(Arrays.asList("updated-before-conflict", 10000L), sql.getParams());
    }

    @BeforeClass
    public static void initializeStaticConfig() throws Exception {
        try (MockedStatic<DynamicApplicationConfig> config = Mockito.mockStatic(DynamicApplicationConfig.class)) {
            config.when(() -> DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV)).thenReturn(false);
            Assert.assertFalse(DynamicApplicationConfig.getBoolean(ConfigKeys.IS_LAB_ENV));
            Class.forName(DmlApplyHelper.class.getName());
        }
    }

    @After
    public void resetStaticOptions() {
        DmlApplyHelper.setSkipMismatchedColumns(false);
        DmlApplyHelper.setFilterColumns(null);
    }

    @Test
    public void nonComparableType_RecognizesVectorAndSpatialFamilies() {
        Assert.assertFalse(DmlApplyHelper.isNonComparableType(column("blank", null)));
        Assert.assertFalse(DmlApplyHelper.isNonComparableType(column("regular", "VARCHAR")));
        Assert.assertTrue(DmlApplyHelper.isNonComparableType(column("vector", "VECTOR(3)")));
        Assert.assertTrue(DmlApplyHelper.isNonComparableType(column("geometry", "GEOMETRY")));
        Assert.assertTrue(DmlApplyHelper.isNonComparableType(column("point", "MULTIPOINT")));
        Assert.assertTrue(DmlApplyHelper.isNonComparableType(column("line", "LINESTRING")));
        Assert.assertTrue(DmlApplyHelper.isNonComparableType(column("polygon", "MULTIPOLYGON")));
    }

    @Test
    public void tableInfo_NoPrimaryKey_UsesOnlyComparableColumnsAsIdentity() {
        TableInfo tableInfo = new TableInfo("dst", "tb");
        ColumnInfo id = column("id", "BIGINT");
        ColumnInfo vector = column("embedding", "VECTOR(3)");
        ColumnInfo geometry = column("shape", "GEOMETRY");
        tableInfo.setColumns(Arrays.asList(id, vector, geometry));

        Assert.assertEquals(Collections.singletonList("id"), tableInfo.getKeyList());
        Assert.assertEquals(Collections.singletonList(id), tableInfo.getWithTypeKeyList());
        Assert.assertSame(vector, tableInfo.getColumnInfoOrNull("EMBEDDING"));
        Assert.assertNull(tableInfo.getColumnInfoOrNull("missing"));
    }

    @Test
    public void forceAllColumns_ExcludesIdentityKeysAndFallsBackForKeyOnlyTable() {
        DBMSColumn id = dbmsColumn("id");
        DBMSColumn payload = dbmsColumn("payload");
        DefaultRowChange rowChange = mock(DefaultRowChange.class);
        when(rowChange.isForceAllColumns()).thenReturn(true);
        doReturn(Arrays.asList(id, payload)).when(rowChange).getColumns();

        TableInfo tableInfo = new TableInfo("dst", "tb");
        tableInfo.setPks(Collections.singletonList("id"));
        Assert.assertEquals(Collections.singletonList(payload),
            DmlApplyHelper.getUpdateChangeColumns(rowChange, tableInfo));

        doReturn(Collections.singletonList(id)).when(rowChange).getColumns();
        TableInfo keyOnlyTable = new TableInfo("dst", "key_only");
        keyOnlyTable.setPks(Collections.singletonList("id"));
        Assert.assertEquals(Collections.singletonList(id),
            DmlApplyHelper.getUpdateChangeColumns(rowChange, keyOnlyTable));
    }

    @Test
    public void skipMismatchedColumns_RemovesUnknownColumnsFromInsertAndUpdate() {
        DmlApplyHelper.setSkipMismatchedColumns(true);
        DBMSColumn id = dbmsColumn("id");
        DBMSColumn removed = dbmsColumn("removed_col");
        DefaultRowChange rowChange = mock(DefaultRowChange.class);
        when(rowChange.getSchema()).thenReturn("src");
        when(rowChange.getTable()).thenReturn("tb");
        when(rowChange.getRowSize()).thenReturn(1);
        when(rowChange.getRowValue(1, "id")).thenReturn(1L);
        doReturn(Arrays.asList(id, removed)).when(rowChange).getColumns();

        TableInfo tableInfo = new TableInfo("dst", "tb");
        tableInfo.setColumns(Collections.singletonList(column("id", "BIGINT")));
        tableInfo.setPks(Collections.singletonList("id"));

        SqlContext insert = DmlApplyHelper.getInsertSqlExecContext(rowChange, tableInfo,
            RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);
        Assert.assertEquals("INSERT INTO `dst`.`tb`(`id`) VALUES (?)", insert.getSql());
        Assert.assertEquals(Collections.singletonList(1L), insert.getParams());

        SqlContextV2 merged = DmlApplyHelper.getMergeInsertSqlExecContextV2(rowChange, tableInfo,
            RplConstants.INSERT_MODE_SIMPLE_INSERT_OR_DELETE);
        Assert.assertEquals("INSERT INTO `dst`.`tb`(`id`) VALUES (?)", merged.getSql());
        Assert.assertEquals(Collections.singletonList(Collections.singletonList(1L)), merged.getParamsList());

        DefaultRowChange update = mock(DefaultRowChange.class);
        when(update.isForceAllColumns()).thenReturn(true);
        doReturn(Collections.singletonList(removed)).when(update).getColumns();
        Assert.assertNull(DmlApplyHelper.getUpdateSqlExecContext(update, tableInfo));
    }

    @Test
    public void interruptConflict_WithoutSqlExceptionStillReturnsActionableFailure() throws Exception {
        DefaultRowChange rowChange = mock(DefaultRowChange.class);
        when(rowChange.toString()).thenReturn("row[id=1]");

        try {
            DmlApplyHelper.handleDupException(null, rowChange, ConflictStrategy.INTERRUPT,
                ConflictType.UPDATE_MISSED, null);
            Assert.fail("interrupt strategy must throw");
        } catch (PolardbxException e) {
            Assert.assertTrue(e.getMessage().contains("UPDATE_MISSED"));
            Assert.assertTrue(e.getMessage().contains("row[id=1]"));
        }
    }

    @Test
    public void repairDmlName_EscapesEmbeddedBackticks() {
        Assert.assertEquals("`a``b`", DmlApplyHelper.repairDMLName("a`b"));
    }

    private static ColumnInfo column(String name, String typeName) {
        return new ColumnInfo(name, Types.VARCHAR, "UTF-8", true, false, typeName, 64);
    }

    private static DBMSColumn dbmsColumn(String name) {
        DBMSColumn column = mock(DBMSColumn.class);
        when(column.getName()).thenReturn(name);
        return column;
    }
}
