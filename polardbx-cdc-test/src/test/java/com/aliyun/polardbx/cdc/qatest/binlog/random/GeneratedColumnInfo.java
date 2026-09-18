/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random;

/**
 * 生成列（虚拟列）的元数据信息，用于追踪随机测试中动态添加的 GENERATED ALWAYS AS 列
 */
public class GeneratedColumnInfo {

    private final String sourceColumnName;
    private final String expression;
    private final ColumnTypeEnum resultType;
    private final boolean stored;

    public GeneratedColumnInfo(String sourceColumnName, String expression,
                               ColumnTypeEnum resultType, boolean stored) {
        this.sourceColumnName = sourceColumnName;
        this.expression = expression;
        this.resultType = resultType;
        this.stored = stored;
    }

    public String getSourceColumnName() {
        return sourceColumnName;
    }

    public String getExpression() {
        return expression;
    }

    public ColumnTypeEnum getResultType() {
        return resultType;
    }

    public boolean isStored() {
        return stored;
    }

    /**
     * 返回 MySQL DDL 关键字 STORED 或 VIRTUAL
     */
    public String getStorageKeyword() {
        return stored ? "STORED" : "VIRTUAL";
    }

    @Override
    public String toString() {
        return "GeneratedColumnInfo{" +
            "sourceColumn='" + sourceColumnName + '\'' +
            ", expression='" + expression + '\'' +
            ", resultType=" + resultType +
            ", " + getStorageKeyword() +
            '}';
    }
}
