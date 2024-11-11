/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.rpl.dbmeta;

import lombok.Data;

/**
 * @author shicai.xsc 2020/12/15 16:51
 * @since 5.0.0.0
 */
@Data
public class ColumnValue {

    private final Object value;
    private final ColumnInfo column;

    public ColumnValue(Object value, ColumnInfo column) {
        this.value = value;
        this.column = column;
    }

    @Override
    public String toString() {
        if (column != null) {
            StringBuilder sb = new StringBuilder();
            sb.append("column:");
            sb.append(column.getName());
            if (value != null) {
                sb.append(",value:");
                sb.append(String.valueOf(value));
            } else {
                sb.append(",value:null");
            }
            return sb.toString();
        } else {
            return null;
        }
    }
}
