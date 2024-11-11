/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.base;

import lombok.Data;

@Data
public class ColumnType {
    public String type;
    public String defaultValue;
    public boolean canNull;
}
