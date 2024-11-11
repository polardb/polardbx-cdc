/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 *
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random.generator;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JsonGenerator extends AbstractValueGenerator<String> {

    public JsonGenerator(ColumnTypeEnum type) {
        super(type);
    }

    @Override
    public String generator() {
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("name", RandomStringUtils.randomAlphanumeric(10));
        valueMap.put("age", RandomUtils.nextInt(4, 100));
        valueMap.put("birthday", new Date());
        return JSON.toJSONString(valueMap);
    }
}
