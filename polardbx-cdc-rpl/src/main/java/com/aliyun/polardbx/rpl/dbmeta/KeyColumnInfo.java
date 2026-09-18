/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.dbmeta;

import lombok.Data;

/**
 * @author shicai.xsc 2020/12/7 11:02
 * @since 5.0.0.0
 */
@Data
public class KeyColumnInfo {

    private String table;
    private String keyName;
    private String columnName;
    private int nonUnique;
    private int seqInIndex;
    /**
     * SHOW INDEXES.Sub_part. A non-null value means that only a column prefix participates in the index.
     */
    private Integer subPart;
    /**
     * MySQL 8.0 functional indexes expose a NULL Column_name.
     */
    private boolean expression;

    public KeyColumnInfo(String table, String keyName, String columnName, int nonUnique, int seqInIndex) {
        this(table, keyName, columnName, nonUnique, seqInIndex, null, false);
    }

    public KeyColumnInfo(String table, String keyName, String columnName, int nonUnique, int seqInIndex,
                         Integer subPart, boolean expression) {
        this.table = table;
        this.keyName = keyName;
        this.columnName = columnName;
        this.nonUnique = nonUnique;
        this.seqInIndex = seqInIndex;
        this.subPart = subPart;
        this.expression = expression;
    }
}
