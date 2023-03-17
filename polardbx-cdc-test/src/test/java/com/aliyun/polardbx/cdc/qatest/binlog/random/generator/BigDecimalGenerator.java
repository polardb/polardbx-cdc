/**
 * Copyright (c) 2013-2022, Alibaba Group Holding Limited;
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * </p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
