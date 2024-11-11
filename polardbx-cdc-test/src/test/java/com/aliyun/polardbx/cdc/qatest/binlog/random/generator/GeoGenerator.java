/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random.generator;

import com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum;
import lombok.SneakyThrows;
import org.apache.commons.lang3.RandomUtils;

public class GeoGenerator extends AbstractValueGenerator<String> {
    public GeoGenerator(ColumnTypeEnum type) {
        super(type);
    }

    @SneakyThrows
    @Override
    public String generator() {
        return "ST_GeomFromText('POINT(" + RandomUtils.nextFloat() + " " + RandomUtils.nextFloat() + ")')";
    }
}
