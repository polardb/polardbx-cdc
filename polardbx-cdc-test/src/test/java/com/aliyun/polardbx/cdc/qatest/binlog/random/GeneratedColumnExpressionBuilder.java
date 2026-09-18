/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random;

import com.google.common.collect.ImmutableSet;

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
 * 生成列表达式构建工具，为随机测试框架生成安全的 GENERATED ALWAYS AS 表达式。
 * <p>
 * 仅对数值和简单字符串类型创建生成列，排除 BLOB/TEXT/GEO/JSON/ENUM/SET/BIT 等
 * MySQL 对生成列有限制的类型。
 */
public class GeneratedColumnExpressionBuilder {

    /**
     * 适合作为生成列结果类型的安全子集
     */
    private static final Set<ColumnTypeEnum> SAFE_GENERATED_RESULT_TYPES = ImmutableSet.of(
        TYPE_INT, TYPE_BIGINT, TYPE_TINYINT, TYPE_MEDIUMINT, TYPE_SMALLINT,
        TYPE_VARCAHR, TYPE_CHAR,
        TYPE_DOUBLE, TYPE_FLOAT, TYPE_DECIMAL, TYPE_DEC,
        TYPE_BOOLEAN
    );

    /**
     * 可作为生成列表达式源列的数值类型集合
     */
    private static final Set<ColumnTypeEnum> NUMERIC_SOURCE_TYPES = ImmutableSet.of(
        TYPE_INT, TYPE_BIGINT, TYPE_TINYINT, TYPE_MEDIUMINT, TYPE_SMALLINT,
        TYPE_DOUBLE, TYPE_FLOAT, TYPE_DECIMAL, TYPE_DEC, TYPE_BOOLEAN
    );

    /**
     * 可作为生成列表达式源列的字符串类型集合
     */
    private static final Set<ColumnTypeEnum> STRING_SOURCE_TYPES = ImmutableSet.of(
        TYPE_VARCAHR, TYPE_CHAR
    );

    public static Set<ColumnTypeEnum> getSafeGeneratedResultTypes() {
        return SAFE_GENERATED_RESULT_TYPES;
    }

    /**
     * 返回对于给定结果类型，哪些源列类型是兼容的
     */
    public static Set<ColumnTypeEnum> getCompatibleSourceTypes(ColumnTypeEnum resultType) {
        if (isNumericType(resultType)) {
            // 数值结果类型：数值源和字符串源都可以（字符串通过 LENGTH() 转换）
            return ImmutableSet.<ColumnTypeEnum>builder()
                .addAll(NUMERIC_SOURCE_TYPES)
                .addAll(STRING_SOURCE_TYPES)
                .build();
        } else if (isStringType(resultType)) {
            // 字符串结果类型：数值源和字符串源都可以（数值通过 CAST 转换）
            return ImmutableSet.<ColumnTypeEnum>builder()
                .addAll(NUMERIC_SOURCE_TYPES)
                .addAll(STRING_SOURCE_TYPES)
                .build();
        }
        return ImmutableSet.of();
    }

    /**
     * 构建生成列的 SQL 表达式
     *
     * @param resultType 生成列声明的类型
     * @param sourceCol 源列名（"id" 或其他普通列名）
     * @param sourceType 源列的类型（当 sourceCol = "id" 时传入 TYPE_BIGINT）
     * @return 带括号的 SQL 表达式，如 "(id % 1000000)"
     */
    public static String buildExpression(ColumnTypeEnum resultType, String sourceCol,
                                         ColumnTypeEnum sourceType) {
        if ("id".equals(sourceCol)) {
            return buildExpressionFromId(resultType);
        }
        return buildExpressionFromColumn(resultType, sourceCol, sourceType);
    }

    /**
     * 基于 id 主键（BIGINT auto_increment）构建表达式
     */
    private static String buildExpressionFromId(ColumnTypeEnum resultType) {
        switch (resultType) {
        case TYPE_INT:
            return "(`id` % 1000000)";
        case TYPE_BIGINT:
            return "(`id` + 0)";
        case TYPE_TINYINT:
            return "(`id` % 100)";
        case TYPE_MEDIUMINT:
            return "(`id` % 1000000)";
        case TYPE_SMALLINT:
            return "(`id` % 10000)";
        case TYPE_VARCAHR:
        case TYPE_CHAR:
            return "(CAST(`id` AS CHAR))";
        case TYPE_DOUBLE:
            return "(`id` * 1.0)";
        case TYPE_FLOAT:
            return "(`id` % 1000000 + 0.0)";
        case TYPE_DECIMAL:
        case TYPE_DEC:
            return "(`id` % 10000 * 1.000)";
        case TYPE_BOOLEAN:
            return "(`id` > 0)";
        default:
            // 不应到达这里，安全回退
            return "(`id` + 0)";
        }
    }

    /**
     * 基于非 id 普通列构建表达式，根据源类型和结果类型选择合适的转换
     */
    private static String buildExpressionFromColumn(ColumnTypeEnum resultType, String sourceCol,
                                                    ColumnTypeEnum sourceType) {
        boolean sourceIsNumeric = isNumericType(sourceType);
        boolean resultIsNumeric = isNumericType(resultType);
        boolean resultIsString = isStringType(resultType);

        if (sourceIsNumeric && resultIsNumeric) {
            // 数值 -> 数值：用取模防溢出
            return buildNumericToNumericExpr(resultType, sourceCol);
        } else if (sourceIsNumeric && resultIsString) {
            // 数值 -> 字符串：CAST
            return "(CAST(`" + sourceCol + "` AS CHAR))";
        } else if (!sourceIsNumeric && resultIsString) {
            // 字符串 -> 字符串：CONCAT
            return "(CONCAT(`" + sourceCol + "`, '_g'))";
        } else if (!sourceIsNumeric && resultIsNumeric) {
            // 字符串 -> 数值：LENGTH
            return buildStringToNumericExpr(resultType, sourceCol);
        }

        // 回退到基于 id 的表达式
        return buildExpressionFromId(resultType);
    }

    /**
     * 数值列 -> 数值结果类型，用取模保证不溢出
     */
    private static String buildNumericToNumericExpr(ColumnTypeEnum resultType, String sourceCol) {
        switch (resultType) {
        case TYPE_TINYINT:
            return "(`" + sourceCol + "` % 100)";
        case TYPE_SMALLINT:
            return "(`" + sourceCol + "` % 10000)";
        case TYPE_MEDIUMINT:
        case TYPE_INT:
            return "(`" + sourceCol + "` % 1000000)";
        case TYPE_BIGINT:
            return "(`" + sourceCol + "` + 0)";
        case TYPE_FLOAT:
            return "(`" + sourceCol + "` % 1000000 + 0.0)";
        case TYPE_DOUBLE:
            return "(`" + sourceCol + "` * 1.0)";
        case TYPE_DECIMAL:
        case TYPE_DEC:
            return "(`" + sourceCol + "` % 10000 * 1.000)";
        case TYPE_BOOLEAN:
            return "(`" + sourceCol + "` > 0)";
        default:
            return "(`" + sourceCol + "` + 0)";
        }
    }

    /**
     * 字符串列 -> 数值结果类型，通过 LENGTH() 转换
     */
    private static String buildStringToNumericExpr(ColumnTypeEnum resultType, String sourceCol) {
        switch (resultType) {
        case TYPE_TINYINT:
            return "(LENGTH(`" + sourceCol + "`) % 100)";
        case TYPE_SMALLINT:
            return "(LENGTH(`" + sourceCol + "`) % 10000)";
        case TYPE_MEDIUMINT:
        case TYPE_INT:
            return "(LENGTH(`" + sourceCol + "`))";
        case TYPE_BIGINT:
            return "(CAST(LENGTH(`" + sourceCol + "`) AS SIGNED))";
        case TYPE_FLOAT:
        case TYPE_DOUBLE:
            return "(LENGTH(`" + sourceCol + "`) * 1.0)";
        case TYPE_DECIMAL:
        case TYPE_DEC:
            return "(LENGTH(`" + sourceCol + "`) * 1.000)";
        case TYPE_BOOLEAN:
            return "(LENGTH(`" + sourceCol + "`) > 0)";
        default:
            return "(LENGTH(`" + sourceCol + "`))";
        }
    }

    private static boolean isNumericType(ColumnTypeEnum type) {
        return NUMERIC_SOURCE_TYPES.contains(type);
    }

    private static boolean isStringType(ColumnTypeEnum type) {
        return STRING_SOURCE_TYPES.contains(type);
    }
}
