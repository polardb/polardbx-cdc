/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random;

import org.junit.Assert;
import org.junit.Test;

import java.util.Set;

import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_BIGINT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_BOOLEAN;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_CHAR;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_DEC;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_DECIMAL;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_DOUBLE;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_FLOAT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_INT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_MEDIUMINT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_SMALLINT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_TINYINT;
import static com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum.TYPE_VARCAHR;

/**
 * GeneratedColumnExpressionBuilder 单元测试，验证各类型表达式模板输出合法 SQL
 */
public class GeneratedColumnExpressionBuilderTest {

    // ==================== 基于 id 的表达式测试 ====================

    @Test
    public void testBuildExpressionFromId_INT() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_INT, "id", TYPE_BIGINT);
        Assert.assertEquals("(`id` % 1000000)", expr);
    }

    @Test
    public void testBuildExpressionFromId_BIGINT() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_BIGINT, "id", TYPE_BIGINT);
        Assert.assertEquals("(`id` + 0)", expr);
    }

    @Test
    public void testBuildExpressionFromId_TINYINT() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_TINYINT, "id", TYPE_BIGINT);
        Assert.assertEquals("(`id` % 100)", expr);
    }

    @Test
    public void testBuildExpressionFromId_MEDIUMINT() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_MEDIUMINT, "id", TYPE_BIGINT);
        Assert.assertEquals("(`id` % 1000000)", expr);
    }

    @Test
    public void testBuildExpressionFromId_SMALLINT() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_SMALLINT, "id", TYPE_BIGINT);
        Assert.assertEquals("(`id` % 10000)", expr);
    }

    @Test
    public void testBuildExpressionFromId_VARCHAR() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_VARCAHR, "id", TYPE_BIGINT);
        Assert.assertEquals("(CAST(`id` AS CHAR))", expr);
    }

    @Test
    public void testBuildExpressionFromId_CHAR() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_CHAR, "id", TYPE_BIGINT);
        Assert.assertEquals("(CAST(`id` AS CHAR))", expr);
    }

    @Test
    public void testBuildExpressionFromId_DOUBLE() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_DOUBLE, "id", TYPE_BIGINT);
        Assert.assertEquals("(`id` * 1.0)", expr);
    }

    @Test
    public void testBuildExpressionFromId_FLOAT() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_FLOAT, "id", TYPE_BIGINT);
        Assert.assertEquals("(`id` % 1000000 + 0.0)", expr);
    }

    @Test
    public void testBuildExpressionFromId_DECIMAL() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_DECIMAL, "id", TYPE_BIGINT);
        Assert.assertEquals("(`id` % 10000 * 1.000)", expr);
    }

    @Test
    public void testBuildExpressionFromId_DEC() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_DEC, "id", TYPE_BIGINT);
        Assert.assertEquals("(`id` % 10000 * 1.000)", expr);
    }

    @Test
    public void testBuildExpressionFromId_BOOLEAN() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_BOOLEAN, "id", TYPE_BIGINT);
        Assert.assertEquals("(`id` > 0)", expr);
    }

    // ==================== 基于非 id 列的表达式测试 ====================

    @Test
    public void testBuildExpression_numericToNumeric() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_INT, "c_bigint", TYPE_BIGINT);
        Assert.assertEquals("(`c_bigint` % 1000000)", expr);
    }

    @Test
    public void testBuildExpression_numericToString() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_VARCAHR, "c_int", TYPE_INT);
        Assert.assertEquals("(CAST(`c_int` AS CHAR))", expr);
    }

    @Test
    public void testBuildExpression_stringToString() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_VARCAHR, "c_varchar", TYPE_VARCAHR);
        Assert.assertEquals("(CONCAT(`c_varchar`, '_g'))", expr);
    }

    @Test
    public void testBuildExpression_stringToNumeric() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_INT, "c_varchar", TYPE_VARCAHR);
        Assert.assertEquals("(LENGTH(`c_varchar`))", expr);
    }

    @Test
    public void testBuildExpression_stringToTinyint() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_TINYINT, "c_char", TYPE_CHAR);
        Assert.assertEquals("(LENGTH(`c_char`) % 100)", expr);
    }

    @Test
    public void testBuildExpression_stringToBoolean() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_BOOLEAN, "c_varchar", TYPE_VARCAHR);
        Assert.assertEquals("(LENGTH(`c_varchar`) > 0)", expr);
    }

    @Test
    public void testBuildExpression_numericToBigint() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_BIGINT, "c_int", TYPE_INT);
        Assert.assertEquals("(`c_int` + 0)", expr);
    }

    @Test
    public void testBuildExpression_numericToDouble() {
        String expr = GeneratedColumnExpressionBuilder.buildExpression(TYPE_DOUBLE, "c_int", TYPE_INT);
        Assert.assertEquals("(`c_int` * 1.0)", expr);
    }

    // ==================== 安全类型集合测试 ====================

    @Test
    public void testSafeGeneratedResultTypes_notEmpty() {
        Set<ColumnTypeEnum> safeTypes = GeneratedColumnExpressionBuilder.getSafeGeneratedResultTypes();
        Assert.assertFalse(safeTypes.isEmpty());
        Assert.assertEquals(12, safeTypes.size());
    }

    @Test
    public void testSafeGeneratedResultTypes_containsExpectedTypes() {
        Set<ColumnTypeEnum> safeTypes = GeneratedColumnExpressionBuilder.getSafeGeneratedResultTypes();
        Assert.assertTrue(safeTypes.contains(TYPE_INT));
        Assert.assertTrue(safeTypes.contains(TYPE_BIGINT));
        Assert.assertTrue(safeTypes.contains(TYPE_VARCAHR));
        Assert.assertTrue(safeTypes.contains(TYPE_DOUBLE));
        Assert.assertTrue(safeTypes.contains(TYPE_BOOLEAN));
    }

    @Test
    public void testSafeGeneratedResultTypes_excludesUnsafeTypes() {
        Set<ColumnTypeEnum> safeTypes = GeneratedColumnExpressionBuilder.getSafeGeneratedResultTypes();
        Assert.assertFalse(safeTypes.contains(ColumnTypeEnum.TYPE_JSON));
        Assert.assertFalse(safeTypes.contains(ColumnTypeEnum.TYPE_GEO));
        Assert.assertFalse(safeTypes.contains(ColumnTypeEnum.TYPE_BLOB));
        Assert.assertFalse(safeTypes.contains(ColumnTypeEnum.TYPE_TEXT));
        Assert.assertFalse(safeTypes.contains(ColumnTypeEnum.TYPE_ENUM));
        Assert.assertFalse(safeTypes.contains(ColumnTypeEnum.TYPE_SET));
        Assert.assertFalse(safeTypes.contains(ColumnTypeEnum.TYPE_BIT));
    }

    // ==================== 兼容性类型测试 ====================

    @Test
    public void testCompatibleSourceTypes_forNumericResult() {
        Set<ColumnTypeEnum> compatible = GeneratedColumnExpressionBuilder.getCompatibleSourceTypes(TYPE_INT);
        Assert.assertTrue(compatible.contains(TYPE_BIGINT));
        Assert.assertTrue(compatible.contains(TYPE_INT));
        Assert.assertTrue(compatible.contains(TYPE_VARCAHR));
        Assert.assertTrue(compatible.contains(TYPE_CHAR));
    }

    @Test
    public void testCompatibleSourceTypes_forStringResult() {
        Set<ColumnTypeEnum> compatible = GeneratedColumnExpressionBuilder.getCompatibleSourceTypes(TYPE_VARCAHR);
        Assert.assertTrue(compatible.contains(TYPE_INT));
        Assert.assertTrue(compatible.contains(TYPE_BIGINT));
        Assert.assertTrue(compatible.contains(TYPE_VARCAHR));
    }

    // ==================== 表达式格式验证 ====================

    @Test
    public void testAllExpressionsFromId_haveParentheses() {
        for (ColumnTypeEnum type : GeneratedColumnExpressionBuilder.getSafeGeneratedResultTypes()) {
            String expr = GeneratedColumnExpressionBuilder.buildExpression(type, "id", TYPE_BIGINT);
            Assert.assertTrue("Expression for " + type + " should start with '(': " + expr,
                expr.startsWith("("));
            Assert.assertTrue("Expression for " + type + " should end with ')': " + expr,
                expr.endsWith(")"));
        }
    }

    @Test
    public void testAllExpressionsFromId_referenceId() {
        for (ColumnTypeEnum type : GeneratedColumnExpressionBuilder.getSafeGeneratedResultTypes()) {
            String expr = GeneratedColumnExpressionBuilder.buildExpression(type, "id", TYPE_BIGINT);
            Assert.assertTrue("Expression for " + type + " should reference `id`: " + expr,
                expr.contains("`id`"));
        }
    }

    // ==================== GeneratedColumnInfo 测试 ====================

    @Test
    public void testGeneratedColumnInfo_virtual() {
        GeneratedColumnInfo info = new GeneratedColumnInfo("id", "(id + 0)", TYPE_BIGINT, false);
        Assert.assertEquals("id", info.getSourceColumnName());
        Assert.assertEquals("(id + 0)", info.getExpression());
        Assert.assertEquals(TYPE_BIGINT, info.getResultType());
        Assert.assertFalse(info.isStored());
        Assert.assertEquals("VIRTUAL", info.getStorageKeyword());
    }

    @Test
    public void testGeneratedColumnInfo_stored() {
        GeneratedColumnInfo info = new GeneratedColumnInfo("c_int", "(c_int % 100)", TYPE_INT, true);
        Assert.assertTrue(info.isStored());
        Assert.assertEquals("STORED", info.getStorageKeyword());
        Assert.assertEquals("c_int", info.getSourceColumnName());
    }
}
