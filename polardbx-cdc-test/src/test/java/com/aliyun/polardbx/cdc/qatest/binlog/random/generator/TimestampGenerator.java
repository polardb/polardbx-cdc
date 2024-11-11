/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random.generator;

import com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum;

import java.sql.Timestamp;

public class TimestampGenerator extends AbstractValueGenerator<Timestamp> {
    public TimestampGenerator(ColumnTypeEnum type) {
        super(type);
    }

    @Override
    public Timestamp generator() {
        return new Timestamp(System.currentTimeMillis());
    }
}
