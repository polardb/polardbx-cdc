/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.binlog.util.format;

import lombok.Data;

@Data
public class Column {

    private String title;
    private int columnLen;
    private int index;

    public Column(String title) {
        this.title = title;
    }
}
