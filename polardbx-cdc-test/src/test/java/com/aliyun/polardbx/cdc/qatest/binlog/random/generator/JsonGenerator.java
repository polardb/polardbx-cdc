/**
 * Copyright (c) 2013-Present, Alibaba Group Holding Limited.
 * All rights reserved.
 * <p>
 * Licensed under the Server Side Public License v1 (SSPLv1).
 */
package com.aliyun.polardbx.cdc.qatest.binlog.random.generator;

import com.alibaba.fastjson.JSON;
import com.aliyun.polardbx.cdc.qatest.binlog.random.ColumnTypeEnum;
import com.aliyun.polardbx.cdc.qatest.random.RandomUtil;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.RandomUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JsonGenerator extends AbstractValueGenerator<String> {

    public JsonGenerator(ColumnTypeEnum type) {
        super(type);
    }

    @Override
    public String generator() {
        int rate = RandomUtil.nextInt(100);
        if (rate < 25) {
            return bigJson();
        }else if (rate < 50){
            return normalJson();
        }else if (rate < 75) {
            return null;
        }else {
            return "abc";
        }
    }

    public String normalJson(){
        Map<String, Object> valueMap = new HashMap<>();
        valueMap.put("name", RandomStringUtils.randomAlphanumeric(10));
        valueMap.put("age", RandomUtils.nextInt(4, 100));
        valueMap.put("birthday", new Date());
        return JSON.toJSONString(valueMap);
    }

    public String bigJson(){
        StringBuilder jsonBuilder = new StringBuilder();
        jsonBuilder.append("{");
        // 随机1000 ~ 10000个属性
        int propertyCount = RandomUtil.nextInt(10000) + 1000;
        for (int i = 0; i < propertyCount; i++) {
            String name = RandomStringUtils.randomAlphabetic(5) + "_" + i;
            int rate = RandomUtils.nextInt(0, 100);
            Serializable v = null;
            if (rate < 30) {
                v = RandomUtils.nextLong();
            } else if (rate < 60) {
                v = "\"" + RandomStringUtils.randomAlphabetic(200) + "\"";
            } else {
                int randomLength = RandomUtil.nextInt(1000) + 10;
                List<String> arrayList = Lists.newArrayListWithCapacity(randomLength);
                for (int j = 0; j < randomLength; j++) {
                    arrayList.add(RandomStringUtils.randomAlphabetic(200));
                }
                v = JSON.toJSONString(arrayList);
            }
            jsonBuilder.append("\"").append(name).append("\":").append(v);
            if (i != propertyCount - 1) {
                jsonBuilder.append(",");
            }
        }
        jsonBuilder.append("}");
        return jsonBuilder.toString();
    }

}
