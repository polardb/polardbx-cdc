/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random.generator;

import com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum;

public abstract class AbstractValueGenerator<T> implements ValueGenerator<T> {

    private ColumnTypeEnum type;

    public AbstractValueGenerator(ColumnTypeEnum type) {
        this.type = type;
    }

    public ColumnTypeEnum getType() {
        return type;
    }

    @Override
    public abstract T generator();
}
