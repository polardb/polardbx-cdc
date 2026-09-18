/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.core.ddl;

import com.google.common.collect.Lists;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class TableMetaTest {

    private TableMeta a;
    private TableMeta b;

    @Before
    public void setUp() {
        // 初始化两个基本相同的 TableMeta
        a = createSampleTableMeta(false);
        b = createSampleTableMeta(false);
    }

    private TableMeta createSampleTableMeta(boolean useImplicitPk) {
        TableMeta meta = new TableMeta();
        meta.setCharset("utf8mb4");
        meta.setUseImplicitPk(useImplicitPk);

        TableMeta.FieldMeta field1 = new TableMeta.FieldMeta("id", "INT", false, true, null, false);
        TableMeta.FieldMeta field2 = new TableMeta.FieldMeta("name", "VARCHAR(100)", true, false, null, false);
        meta.setFields(Lists.newArrayList(field1, field2));

        return meta;
    }

    @Test
    public void testBasicEquals_Self() {
        Assert.assertTrue(a.basicEquals(a));
    }

    @Test
    public void testBasicEquals_Null() {
        Assert.assertFalse(a.basicEquals(null));
    }

    @Test
    public void testBasicEquals_DifferentClass() {
        Assert.assertFalse(a.basicEquals(new Object()));
    }

    @Test
    public void testBasicEquals_FieldsDifferent() {
        List<TableMeta.FieldMeta> list = b.getFields(); // 清空字段列表
        list.clear();
        Assert.assertFalse(a.basicEquals(b));
    }

    @Test
    public void testBasicEquals_CharsetDifferent() {
        b.setCharset("latin1"); // 修改字符集
        Assert.assertFalse(a.basicEquals(b));
    }

    @Test
    public void testBasicEquals_UseImplicitPkDifferent() {
        b.setUseImplicitPk(true); // 修改隐式主键标志
        Assert.assertFalse(a.basicEquals(b));
    }

    @Test
    public void testBasicEquals_AllMatch() {
        Assert.assertTrue(a.basicEquals(b));
    }

    @Test
    public void testBasicEquals_CompareWithAnotherInstanceOfSameClass() {
        TableMeta c = createSampleTableMeta(false);
        Assert.assertTrue(a.basicEquals(c));
    }

    @Test
    public void testFieldMetaIsBinary_Varbinary() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("c", "VARBINARY(16)", true, false, null, false);
        Assert.assertTrue(fm.isBinary());
    }

    @Test
    public void testFieldMetaIsBinary_Binary() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("c", "BINARY(8)", true, false, null, false);
        Assert.assertTrue(fm.isBinary());
    }

    @Test
    public void testFieldMetaIsBinary_VectorUpperCase() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("v", "VECTOR(4)", true, false, null, false);
        Assert.assertTrue(fm.isBinary());
    }

    @Test
    public void testFieldMetaIsBinary_VectorLowerCase() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("v", "vector(8)", true, false, null, false);
        Assert.assertTrue(fm.isBinary());
    }

    @Test
    public void testFieldMetaIsBinary_VectorMixedCase() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("v", "Vector(16)", true, false, null, false);
        Assert.assertTrue(fm.isBinary());
    }

    @Test
    public void testFieldMetaIsBinary_Varchar() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("name", "VARCHAR(100)", true, false, null, false);
        Assert.assertFalse(fm.isBinary());
    }

    @Test
    public void testFieldMetaIsBinary_Int() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("id", "INT", false, true, null, false);
        Assert.assertFalse(fm.isBinary());
    }

    @Test
    public void testFieldMetaIsBinary_SetColumnTypeRecomputes() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("c", "VARCHAR(10)", true, false, null, false);
        Assert.assertFalse(fm.isBinary());
        fm.setColumnType("VECTOR(4)");
        Assert.assertTrue(fm.isBinary());
    }
}
