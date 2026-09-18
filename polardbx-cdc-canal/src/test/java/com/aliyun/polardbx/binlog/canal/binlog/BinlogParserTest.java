/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.canal.binlog;

import com.aliyun.polardbx.binlog.canal.core.ddl.TableMeta;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit tests for {@link BinlogParser#isBinaryColumn(TableMeta.FieldMeta)}.
 */
public class BinlogParserTest {

    @Test
    public void testIsBinaryColumn_Null() {
        Assert.assertFalse(BinlogParser.isBinaryColumn(null));
    }

    @Test
    public void testIsBinaryColumn_Varbinary() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("c", "VARBINARY(16)", true, false, null, false);
        Assert.assertTrue(BinlogParser.isBinaryColumn(fm));
    }

    @Test
    public void testIsBinaryColumn_Binary() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("c", "BINARY(8)", true, false, null, false);
        Assert.assertTrue(BinlogParser.isBinaryColumn(fm));
    }

    @Test
    public void testIsBinaryColumn_VectorUpperCase() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("v", "VECTOR(4)", true, false, null, false);
        Assert.assertTrue(BinlogParser.isBinaryColumn(fm));
    }

    @Test
    public void testIsBinaryColumn_VectorLowerCase() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("v", "vector(8)", true, false, null, false);
        Assert.assertTrue(BinlogParser.isBinaryColumn(fm));
    }

    @Test
    public void testIsBinaryColumn_VectorMixedCase() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("v", "Vector(16)", true, false, null, false);
        Assert.assertTrue(BinlogParser.isBinaryColumn(fm));
    }

    @Test
    public void testIsBinaryColumn_Varchar() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("name", "VARCHAR(100)", true, false, null, false);
        Assert.assertFalse(BinlogParser.isBinaryColumn(fm));
    }

    @Test
    public void testIsBinaryColumn_Int() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("id", "INT", false, true, null, false);
        Assert.assertFalse(BinlogParser.isBinaryColumn(fm));
    }

    @Test
    public void testIsBinaryColumn_Text() {
        TableMeta.FieldMeta fm = new TableMeta.FieldMeta("c", "TEXT", true, false, null, false);
        Assert.assertFalse(BinlogParser.isBinaryColumn(fm));
    }
}
