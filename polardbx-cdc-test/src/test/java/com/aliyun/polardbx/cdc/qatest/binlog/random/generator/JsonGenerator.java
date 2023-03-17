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
