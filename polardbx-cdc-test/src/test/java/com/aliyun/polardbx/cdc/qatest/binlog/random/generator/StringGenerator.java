/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random.generator;

import com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum;
import org.apache.commons.lang3.RandomStringUtils;

public class StringGenerator extends AbstractValueGenerator<String> {

    public StringGenerator(ColumnTypeEnum type) {
        super(type);
    }

    @Override
    public String generator() {
        return RandomStringUtils.randomAlphanumeric(20);
    }
}
