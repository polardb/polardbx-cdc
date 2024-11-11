/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random.generator;

import com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum;
import org.apache.commons.lang3.RandomUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class BigDecimalGenerator extends AbstractValueGenerator<BigDecimal> {

    public BigDecimalGenerator(ColumnTypeEnum type) {
        super(type);
    }

    @Override
    public BigDecimal generator() {
        BigDecimal decimal = BigDecimal.valueOf(999999d - RandomUtils.nextDouble(0, 999999d));
        decimal = decimal.setScale(3, RoundingMode.HALF_UP);
        return decimal;
    }
}
