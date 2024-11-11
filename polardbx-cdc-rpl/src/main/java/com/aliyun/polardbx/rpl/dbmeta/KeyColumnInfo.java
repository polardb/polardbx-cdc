/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
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

    public KeyColumnInfo(String table, String keyName, String columnName, int nonUnique, int seqInIndex) {
        this.table = table;
        this.keyName = keyName;
        this.columnName = columnName;
        this.nonUnique = nonUnique;
        this.seqInIndex = seqInIndex;
    }
}
