/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random.generator;

import com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum;

import java.util.Date;

public class DateGenerator extends AbstractValueGenerator<Date> {
    public DateGenerator(ColumnTypeEnum type) {
        super(type);
    }

    @Override
    public Date generator() {
        return new Date(System.currentTimeMillis());
    }
}
